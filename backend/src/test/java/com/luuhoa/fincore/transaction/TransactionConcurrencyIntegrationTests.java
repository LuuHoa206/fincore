package com.luuhoa.fincore.transaction;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import com.luuhoa.fincore.TestcontainersConfiguration;
import com.luuhoa.fincore.category.Category;
import com.luuhoa.fincore.category.CategoryRepository;
import com.luuhoa.fincore.category.CategoryType;
import com.luuhoa.fincore.identity.UserAccount;
import com.luuhoa.fincore.identity.UserAccountRepository;
import com.luuhoa.fincore.shared.api.ConflictException;
import com.luuhoa.fincore.wallet.Wallet;
import com.luuhoa.fincore.wallet.WalletRepository;
import com.luuhoa.fincore.wallet.WalletType;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Exercises financial write locking against real PostgreSQL connections.
 * These tests are intentionally opt-in because Testcontainers needs Docker.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@EnabledIfEnvironmentVariable(named = "RUN_INTEGRATION_TESTS", matches = "true")
class TransactionConcurrencyIntegrationTests {

    private static final int PARALLEL_REQUESTS = 12;

    @Autowired
    private TransactionService transactionService;

    @Autowired
    private UserAccountRepository userRepository;

    @Autowired
    private WalletRepository walletRepository;

    @Autowired
    private CategoryRepository categoryRepository;

    @Autowired
    private FinancialTransactionRepository transactionRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void recordsExactlyOneTransactionWhenTheSameIdempotencyKeyArrivesConcurrently() throws Exception {
        Fixture fixture = createFixture(BigDecimal.ZERO);
        String idempotencyKey = "parallel-income-" + UUID.randomUUID();
        CreateTransactionRequest request = request(fixture, TransactionType.INCOME, new BigDecimal("250000"));

        List<TransactionResponse> responses = runConcurrently(PARALLEL_REQUESTS,
                () -> transactionService.create(fixture.userId(), request, idempotencyKey));

        assertThat(responses).hasSize(PARALLEL_REQUESTS);
        UUID transactionId = responses.getFirst().id();
        assertThat(responses).allSatisfy(response -> assertThat(response.id()).isEqualTo(transactionId));
        assertThat(transactionRepository.findTop100ByUserIdOrderByOccurredAtDesc(fixture.userId()))
                .hasSize(1)
                .extracting(FinancialTransaction::getId)
                .containsExactly(transactionId);
        assertThat(count("select count(*) from ledger_entries where transaction_id = ?", transactionId)).isEqualTo(2);
        assertThat(sum("select coalesce(sum(signed_amount), 0) from ledger_entries where transaction_id = ?", transactionId))
                .isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(walletRepository.findById(fixture.walletId()).orElseThrow().getCurrentBalance())
                .isEqualByComparingTo("250000");
    }

    @Test
    void neverOverdrawsWhenDistinctExpenseRequestsRaceForTheSameWallet() throws Exception {
        Fixture fixture = createFixture(new BigDecimal("100000"));
        CreateTransactionRequest request = request(fixture, TransactionType.EXPENSE, new BigDecimal("25000"));

        List<Boolean> results = runConcurrently(6, new java.util.function.IntFunction<Callable<Boolean>>() {
            @Override
            public Callable<Boolean> apply(int index) {
                return () -> {
                    try {
                        transactionService.create(fixture.userId(), request, "parallel-expense-" + index + "-" + UUID.randomUUID());
                        return true;
                    } catch (ConflictException exception) {
                        return false;
                    }
                };
            }
        });

        assertThat(results).filteredOn(Boolean::booleanValue).hasSize(4);
        assertThat(walletRepository.findById(fixture.walletId()).orElseThrow().getCurrentBalance())
                .isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(transactionRepository.findTop100ByUserIdOrderByOccurredAtDesc(fixture.userId())).hasSize(4);
    }

    private List<TransactionResponse> runConcurrently(int requestCount, Callable<TransactionResponse> task) throws Exception {
        List<Callable<TransactionResponse>> tasks = new ArrayList<>();
        for (int index = 0; index < requestCount; index++) {
            tasks.add(task);
        }
        return runConcurrently(tasks);
    }

    private List<Boolean> runConcurrently(int requestCount, java.util.function.IntFunction<Callable<Boolean>> taskFactory) throws Exception {
        List<Callable<Boolean>> tasks = new ArrayList<>();
        for (int index = 0; index < requestCount; index++) {
            tasks.add(taskFactory.apply(index));
        }
        return runConcurrently(tasks);
    }

    private <T> List<T> runConcurrently(List<Callable<T>> tasks) throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(tasks.size());
        CountDownLatch ready = new CountDownLatch(tasks.size());
        CountDownLatch start = new CountDownLatch(1);
        try {
            List<Future<T>> futures = tasks.stream()
                    .map(task -> executor.submit(() -> {
                        ready.countDown();
                        if (!start.await(10, TimeUnit.SECONDS)) {
                            throw new IllegalStateException("Timed out waiting for concurrent start");
                        }
                        return task.call();
                    }))
                    .toList();
            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            start.countDown();

            List<T> results = new ArrayList<>();
            for (Future<T> future : futures) {
                results.add(future.get(20, TimeUnit.SECONDS));
            }
            return results;
        } finally {
            executor.shutdownNow();
            executor.awaitTermination(5, TimeUnit.SECONDS);
        }
    }

    private Fixture createFixture(BigDecimal openingBalance) {
        String suffix = UUID.randomUUID().toString();
        UserAccount user = userRepository.saveAndFlush(new UserAccount(
                "concurrency-" + suffix + "@example.test", "not-used", "Concurrency Test", "VND", "Asia/Ho_Chi_Minh"));
        Wallet wallet = new Wallet(user, "Wallet " + suffix, WalletType.BANK, "VND", false);
        wallet.applyBalance(openingBalance);
        wallet = walletRepository.saveAndFlush(wallet);
        Category category = categoryRepository.saveAndFlush(new Category(
                user, "Expenses " + suffix, CategoryType.EXPENSE, "receipt", "#111111"));
        return new Fixture(user.getId(), wallet.getId(), category.getId());
    }

    private CreateTransactionRequest request(Fixture fixture, TransactionType type, BigDecimal amount) {
        return new CreateTransactionRequest(
                fixture.walletId(), fixture.categoryId(), type, amount, "Concurrent financial write", null, Instant.now(), false);
    }

    private long count(String sql, UUID transactionId) {
        Long value = jdbcTemplate.queryForObject(sql, Long.class, transactionId);
        return value == null ? 0 : value;
    }

    private BigDecimal sum(String sql, UUID transactionId) {
        BigDecimal value = jdbcTemplate.queryForObject(sql, BigDecimal.class, transactionId);
        return value == null ? BigDecimal.ZERO : value;
    }

    private record Fixture(UUID userId, UUID walletId, UUID categoryId) {
    }
}

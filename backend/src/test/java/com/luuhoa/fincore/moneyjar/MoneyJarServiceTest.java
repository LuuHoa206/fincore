package com.luuhoa.fincore.moneyjar;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.luuhoa.fincore.audit.AuditLogService;
import com.luuhoa.fincore.identity.UserAccount;
import com.luuhoa.fincore.identity.UserAccountRepository;
import com.luuhoa.fincore.shared.api.ConflictException;
import com.luuhoa.fincore.shared.api.ResourceNotFoundException;
import com.luuhoa.fincore.wallet.Wallet;
import com.luuhoa.fincore.wallet.WalletService;
import com.luuhoa.fincore.wallet.WalletType;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class MoneyJarServiceTest {

    @Mock
    private MoneyJarRepository moneyJarRepository;

    @Mock
    private JarMovementRepository jarMovementRepository;

    @Mock
    private WalletService walletService;

    @Mock
    private UserAccountRepository userRepository;

    @Mock
    private AuditLogService auditLogService;

    private MoneyJarService service;

    @BeforeEach
    void setUp() {
        service = new MoneyJarService(moneyJarRepository, jarMovementRepository, walletService, userRepository, auditLogService);
    }

    @Test
    void allocatesOnlyFromMoneyThatIsNotAlreadyAssignedToAnotherJar() {
        UUID userId = UUID.randomUUID();
        UUID jarId = UUID.randomUUID();
        MoneyJar travelJar = jar(jarId, "Travel", "VND");
        MoneyJar emergencyJar = jar(UUID.randomUUID(), "Emergency", "VND");
        emergencyJar.applyAllocation(new BigDecimal("250000"));
        Wallet bankWallet = wallet("Bank", "VND", "1000000");

        when(walletService.lockActiveWalletsForAllocation(userId)).thenReturn(List.of(bankWallet));
        when(moneyJarRepository.findAllActiveByUserIdForUpdate(userId)).thenReturn(List.of(emergencyJar, travelJar));
        when(userRepository.findById(userId)).thenReturn(Optional.of(user()));

        JarAllocationResponse response = service.allocate(
                userId,
                jarId,
                new ChangeJarAllocationRequest(new BigDecimal("500000")));

        assertThat(response.jar().allocatedBalance()).isEqualByComparingTo("500000");
        assertThat(response.availableToAllocate()).isEqualByComparingTo("250000");
        assertThat(bankWallet.getCurrentBalance()).isEqualByComparingTo("1000000");
        verify(jarMovementRepository).save(any(JarMovement.class));
        verify(auditLogService).record(any(UserAccount.class), eq("MONEY_JAR_ALLOCATION_ADDED"), eq("MONEY_JAR"), eq(jarId), anyMap());
    }

    @Test
    void rejectsAllocationThatExceedsTheRemainingWalletBalance() {
        UUID userId = UUID.randomUUID();
        UUID jarId = UUID.randomUUID();
        MoneyJar jar = jar(jarId, "Travel", "VND");
        Wallet wallet = wallet("Bank", "VND", "100000");

        when(walletService.lockActiveWalletsForAllocation(userId)).thenReturn(List.of(wallet));
        when(moneyJarRepository.findAllActiveByUserIdForUpdate(userId)).thenReturn(List.of(jar));

        assertThatThrownBy(() -> service.allocate(
                userId,
                jarId,
                new ChangeJarAllocationRequest(new BigDecimal("100001"))))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("not enough unallocated");

        verify(jarMovementRepository, never()).save(any());
        assertThat(jar.getAllocatedBalance()).isEqualByComparingTo("0");
    }

    @Test
    void rejectsReleaseBeyondTheJarBalanceWhenNegativeBalancesAreDisabled() {
        UUID userId = UUID.randomUUID();
        UUID jarId = UUID.randomUUID();
        MoneyJar jar = jar(jarId, "Travel", "VND");
        jar.applyAllocation(new BigDecimal("50000"));

        when(walletService.lockActiveWalletsForAllocation(userId)).thenReturn(List.of());
        when(moneyJarRepository.findAllActiveByUserIdForUpdate(userId)).thenReturn(List.of(jar));

        assertThatThrownBy(() -> service.release(
                userId,
                jarId,
                new ChangeJarAllocationRequest(new BigDecimal("50001"))))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("does not have enough allocated");

        verify(jarMovementRepository, never()).save(any());
        assertThat(jar.getAllocatedBalance()).isEqualByComparingTo("50000");
    }

    @Test
    void refusesAllocationIntoAJarOutsideTheCurrentUsersScope() {
        UUID userId = UUID.randomUUID();
        UUID foreignJarId = UUID.randomUUID();

        when(walletService.lockActiveWalletsForAllocation(userId)).thenReturn(List.of());
        when(moneyJarRepository.findAllActiveByUserIdForUpdate(userId)).thenReturn(List.of());

        assertThatThrownBy(() -> service.allocate(
                userId,
                foreignJarId,
                new ChangeJarAllocationRequest(new BigDecimal("1"))))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Money jar was not found");

        verify(jarMovementRepository, never()).save(any());
    }

    @Test
    void transfersAllocatedBalanceBetweenTwoOwnedJarsWithoutChangingWalletBalance() {
        UUID userId = UUID.randomUUID();
        UUID sourceId = UUID.randomUUID();
        UUID destinationId = UUID.randomUUID();
        MoneyJar source = jar(sourceId, "Travel", "VND");
        MoneyJar destination = jar(destinationId, "Emergency", "VND");
        source.applyAllocation(new BigDecimal("300000"));
        Wallet wallet = wallet("Bank", "VND", "1000000");

        when(moneyJarRepository.findAllActiveByUserIdForUpdate(userId)).thenReturn(List.of(destination, source));
        when(userRepository.findById(userId)).thenReturn(Optional.of(user()));

        JarTransferResponse response = service.transfer(
                userId,
                new TransferBetweenJarsRequest(sourceId, destinationId, new BigDecimal("125000")));

        assertThat(response.amount()).isEqualByComparingTo("125000");
        assertThat(source.getAllocatedBalance()).isEqualByComparingTo("175000");
        assertThat(destination.getAllocatedBalance()).isEqualByComparingTo("125000");
        assertThat(wallet.getCurrentBalance()).isEqualByComparingTo("1000000");
        verify(jarMovementRepository).saveAll(any());
        verify(auditLogService).record(any(UserAccount.class), eq("MONEY_JAR_TRANSFERRED"), eq("MONEY_JAR"), eq(sourceId), anyMap());
    }

    @Test
    void rejectsTransferWhenTheSourceJarDoesNotHaveEnoughAllocatedBalance() {
        UUID userId = UUID.randomUUID();
        UUID sourceId = UUID.randomUUID();
        UUID destinationId = UUID.randomUUID();
        MoneyJar source = jar(sourceId, "Travel", "VND");
        MoneyJar destination = jar(destinationId, "Emergency", "VND");
        source.applyAllocation(new BigDecimal("100"));

        when(moneyJarRepository.findAllActiveByUserIdForUpdate(userId)).thenReturn(List.of(source, destination));

        assertThatThrownBy(() -> service.transfer(
                userId,
                new TransferBetweenJarsRequest(sourceId, destinationId, new BigDecimal("101"))))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("does not have enough allocated");

        assertThat(source.getAllocatedBalance()).isEqualByComparingTo("100");
        assertThat(destination.getAllocatedBalance()).isEqualByComparingTo("0");
        verify(jarMovementRepository, never()).saveAll(any());
        verify(auditLogService, never()).record(any(), any(), any(), any(), anyMap());
    }

    @Test
    void rejectsTransferAcrossCurrenciesBeforeWritingAnyMovement() {
        UUID userId = UUID.randomUUID();
        UUID sourceId = UUID.randomUUID();
        UUID destinationId = UUID.randomUUID();
        MoneyJar source = jar(sourceId, "Travel", "VND");
        MoneyJar destination = jar(destinationId, "Emergency", "USD");
        source.applyAllocation(new BigDecimal("100"));

        when(moneyJarRepository.findAllActiveByUserIdForUpdate(userId)).thenReturn(List.of(source, destination));

        assertThatThrownBy(() -> service.transfer(
                userId,
                new TransferBetweenJarsRequest(sourceId, destinationId, new BigDecimal("10"))))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("same currency");

        verify(jarMovementRepository, never()).saveAll(any());
    }

    @Test
    void transferStillRequiresSourceBalanceWhenTheSourceJarAllowsNegativeTracking() {
        UUID userId = UUID.randomUUID();
        UUID sourceId = UUID.randomUUID();
        UUID destinationId = UUID.randomUUID();
        MoneyJar source = new MoneyJar(user(), "Receivable", "VND", null, null, null, true);
        setId(source, sourceId);
        MoneyJar destination = jar(destinationId, "Emergency", "VND");

        when(moneyJarRepository.findAllActiveByUserIdForUpdate(userId)).thenReturn(List.of(source, destination));

        assertThatThrownBy(() -> service.transfer(
                userId,
                new TransferBetweenJarsRequest(sourceId, destinationId, BigDecimal.ONE)))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("source money jar");

        verify(jarMovementRepository, never()).saveAll(any());
    }

    private MoneyJar jar(UUID jarId, String name, String currency) {
        MoneyJar jar = new MoneyJar(
                user(),
                name,
                currency,
                null,
                "#0F8F72",
                "piggy-bank",
                false);
        setId(jar, jarId);
        return jar;
    }

    private Wallet wallet(String name, String currency, String balance) {
        Wallet wallet = new Wallet(
                user(),
                name,
                WalletType.BANK,
                currency,
                false);
        wallet.applyBalance(new BigDecimal(balance));
        return wallet;
    }

    private UserAccount user() {
        return new UserAccount("owner@example.com", "hash", "Owner", "VND", "Asia/Ho_Chi_Minh");
    }

    private void setId(MoneyJar jar, UUID id) {
        try {
            Field field = MoneyJar.class.getDeclaredField("id");
            field.setAccessible(true);
            field.set(jar, id);
        } catch (ReflectiveOperationException exception) {
            throw new AssertionError("Could not prepare money jar test fixture", exception);
        }
    }
}

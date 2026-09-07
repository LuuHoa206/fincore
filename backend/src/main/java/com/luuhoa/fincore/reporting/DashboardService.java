package com.luuhoa.fincore.reporting;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.luuhoa.fincore.budget.BudgetService;
import com.luuhoa.fincore.budget.BudgetStatus;
import com.luuhoa.fincore.identity.UserAccount;
import com.luuhoa.fincore.identity.UserAccountRepository;
import com.luuhoa.fincore.moneyjar.MoneyJarResponse;
import com.luuhoa.fincore.moneyjar.MoneyJarService;
import com.luuhoa.fincore.savinggoal.SavingGoalStatus;
import com.luuhoa.fincore.savinggoal.SavingGoalService;
import com.luuhoa.fincore.shared.api.ResourceNotFoundException;
import com.luuhoa.fincore.transaction.TransactionReportingService;
import com.luuhoa.fincore.transaction.TransactionReportingService.CurrencyCashFlow;
import com.luuhoa.fincore.transaction.TransactionService;
import com.luuhoa.fincore.wallet.WalletResponse;
import com.luuhoa.fincore.wallet.WalletService;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DashboardService {

    private final UserAccountRepository userRepository;
    private final WalletService walletService;
    private final MoneyJarService moneyJarService;
    private final BudgetService budgetService;
    private final SavingGoalService savingGoalService;
    private final TransactionReportingService transactionReportingService;
    private final TransactionService transactionService;

    public DashboardService(
            UserAccountRepository userRepository,
            WalletService walletService,
            MoneyJarService moneyJarService,
            BudgetService budgetService,
            SavingGoalService savingGoalService,
            TransactionReportingService transactionReportingService,
            TransactionService transactionService) {
        this.userRepository = userRepository;
        this.walletService = walletService;
        this.moneyJarService = moneyJarService;
        this.budgetService = budgetService;
        this.savingGoalService = savingGoalService;
        this.transactionReportingService = transactionReportingService;
        this.transactionService = transactionService;
    }

    @Transactional(readOnly = true)
    public DashboardResponse getDashboard(UUID userId, String requestedPeriod) {
        UserAccount user = requireUser(userId);
        ZoneId zoneId = ZoneId.of(user.getTimeZone());
        YearMonth period = resolvePeriod(requestedPeriod, zoneId);
        Instant periodStart = period.atDay(1).atStartOfDay(zoneId).toInstant();
        Instant periodEnd = period.plusMonths(1).atDay(1).atStartOfDay(zoneId).toInstant();

        List<WalletResponse> wallets = walletService.list(userId);
        List<MoneyJarResponse> jars = moneyJarService.list(userId);
        Map<String, CurrencyCashFlow> cashFlowByCurrency = transactionReportingService
                .postedCashFlowByCurrency(userId, periodStart, periodEnd);
        List<CurrencyDashboardSummary> summaries = summaries(user.getPreferredCurrency(), wallets, jars, cashFlowByCurrency);
        int budgetAlerts = (int) budgetService.list(userId, period).stream()
                .filter(budget -> budget.status() == BudgetStatus.WARNING || budget.status() == BudgetStatus.EXCEEDED)
                .count();
        int openGoals = (int) savingGoalService.list(userId).stream()
                .filter(goal -> goal.status() == SavingGoalStatus.ACTIVE || goal.status() == SavingGoalStatus.PAUSED)
                .count();

        return new DashboardResponse(
                period,
                user.getTimeZone(),
                summaries,
                wallets.size(),
                jars.size(),
                budgetAlerts,
                openGoals,
                transactionService.listRecent(userId));
    }

    private List<CurrencyDashboardSummary> summaries(
            String preferredCurrency,
            List<WalletResponse> wallets,
            List<MoneyJarResponse> jars,
            Map<String, CurrencyCashFlow> cashFlowByCurrency) {
        LinkedHashSet<String> currencies = new LinkedHashSet<>();
        currencies.add(preferredCurrency);
        wallets.forEach(wallet -> currencies.add(wallet.currency()));
        jars.forEach(jar -> currencies.add(jar.currency()));
        currencies.addAll(cashFlowByCurrency.keySet());

        return currencies.stream()
                .map(currency -> summary(currency, wallets, jars, cashFlowByCurrency.get(currency)))
                .sorted(Comparator.comparing(CurrencyDashboardSummary::currency))
                .toList();
    }

    private CurrencyDashboardSummary summary(
            String currency,
            List<WalletResponse> wallets,
            List<MoneyJarResponse> jars,
            CurrencyCashFlow cashFlow) {
        BigDecimal balance = wallets.stream()
                .filter(wallet -> currency.equals(wallet.currency()))
                .map(WalletResponse::currentBalance)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal allocated = jars.stream()
                .filter(jar -> currency.equals(jar.currency()))
                .map(MoneyJarResponse::allocatedBalance)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        CurrencyCashFlow flow = cashFlow == null ? CurrencyCashFlow.empty(currency) : cashFlow;
        return new CurrencyDashboardSummary(
                currency,
                balance,
                allocated,
                balance.subtract(allocated),
                flow.incomeAmount(),
                flow.incomeCount(),
                flow.expenseAmount(),
                flow.expenseCount(),
                flow.incomeAmount().subtract(flow.expenseAmount()));
    }

    private UserAccount requireUser(UUID userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("USER_NOT_FOUND", "User account was not found"));
    }

    private YearMonth resolvePeriod(String requestedPeriod, ZoneId zoneId) {
        if (requestedPeriod == null || requestedPeriod.isBlank()) {
            return YearMonth.now(zoneId);
        }
        try {
            return YearMonth.parse(requestedPeriod.trim());
        } catch (DateTimeParseException exception) {
            throw new IllegalArgumentException("period must use the YYYY-MM format");
        }
    }
}

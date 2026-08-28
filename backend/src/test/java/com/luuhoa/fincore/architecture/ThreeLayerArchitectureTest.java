package com.luuhoa.fincore.architecture;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Field;
import java.util.List;

import com.luuhoa.fincore.category.CategoryController;
import com.luuhoa.fincore.category.CategoryService;
import com.luuhoa.fincore.financialcalendar.FinancialCalendarController;
import com.luuhoa.fincore.financialcalendar.FinancialCalendarService;
import com.luuhoa.fincore.audit.AuditLogController;
import com.luuhoa.fincore.audit.AuditLogService;
import com.luuhoa.fincore.allocationrule.AllocationRuleController;
import com.luuhoa.fincore.allocationrule.AllocationRuleService;
import com.luuhoa.fincore.budget.BudgetController;
import com.luuhoa.fincore.budget.BudgetService;
import com.luuhoa.fincore.identity.AuthController;
import com.luuhoa.fincore.identity.AuthService;
import com.luuhoa.fincore.identity.CurrentUserController;
import com.luuhoa.fincore.moneyjar.MoneyJarController;
import com.luuhoa.fincore.moneyjar.MoneyJarService;
import com.luuhoa.fincore.monthlyreview.MonthlyReviewController;
import com.luuhoa.fincore.monthlyreview.MonthlyReviewService;
import com.luuhoa.fincore.notification.NotificationController;
import com.luuhoa.fincore.notification.NotificationService;
import com.luuhoa.fincore.savinggoal.SavingGoalController;
import com.luuhoa.fincore.savinggoal.SavingGoalService;
import com.luuhoa.fincore.statementimport.StatementImportController;
import com.luuhoa.fincore.statementimport.StatementImportService;
import com.luuhoa.fincore.splitbill.SplitBillController;
import com.luuhoa.fincore.splitbill.SplitBillService;
import com.luuhoa.fincore.reporting.DashboardController;
import com.luuhoa.fincore.reporting.DashboardService;
import com.luuhoa.fincore.recurring.RecurringRuleController;
import com.luuhoa.fincore.recurring.RecurringRuleService;
import com.luuhoa.fincore.reconciliation.WalletReconciliationController;
import com.luuhoa.fincore.reconciliation.WalletReconciliationService;
import com.luuhoa.fincore.transaction.TransactionController;
import com.luuhoa.fincore.transaction.TransactionService;
import com.luuhoa.fincore.wallet.WalletController;
import com.luuhoa.fincore.wallet.WalletService;

import org.junit.jupiter.api.Test;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Service;
import org.springframework.web.bind.annotation.RestController;

class ThreeLayerArchitectureTest {

    @Test
    void businessControllersDependOnServicesInsteadOfRepositories() {
        assertControllerUsesService(AuthController.class, AuthService.class);
        assertControllerUsesService(CurrentUserController.class, AuthService.class);
        assertControllerUsesService(AuditLogController.class, AuditLogService.class);
        assertControllerUsesService(CategoryController.class, CategoryService.class);
        assertControllerUsesService(FinancialCalendarController.class, FinancialCalendarService.class);
        assertControllerUsesService(AllocationRuleController.class, AllocationRuleService.class);
        assertControllerUsesService(BudgetController.class, BudgetService.class);
        assertControllerUsesService(MoneyJarController.class, MoneyJarService.class);
        assertControllerUsesService(MonthlyReviewController.class, MonthlyReviewService.class);
        assertControllerUsesService(NotificationController.class, NotificationService.class);
        assertControllerUsesService(SavingGoalController.class, SavingGoalService.class);
        assertControllerUsesService(StatementImportController.class, StatementImportService.class);
        assertControllerUsesService(SplitBillController.class, SplitBillService.class);
        assertControllerUsesService(DashboardController.class, DashboardService.class);
        assertControllerUsesService(RecurringRuleController.class, RecurringRuleService.class);
        assertControllerUsesService(WalletReconciliationController.class, WalletReconciliationService.class);
        assertControllerUsesService(WalletController.class, WalletService.class);
        assertControllerUsesService(TransactionController.class, TransactionService.class);
    }

    private void assertControllerUsesService(Class<?> controllerType, Class<?> serviceType) {
        assertThat(controllerType.isAnnotationPresent(RestController.class)).isTrue();
        assertThat(serviceType.isAnnotationPresent(Service.class)).isTrue();

        List<Class<?>> constructorDependencies = List.of(controllerType.getDeclaredConstructors()).stream()
                .flatMap(constructor -> List.of(constructor.getParameterTypes()).stream())
                .toList();
        assertThat(constructorDependencies).contains(serviceType);
        assertThat(constructorDependencies)
                .noneMatch(this::isRepository);

        assertThat(List.of(controllerType.getDeclaredFields()).stream().map(Field::getType))
                .noneMatch(this::isRepository);
    }

    private boolean isRepository(Class<?> dependency) {
        return JpaRepository.class.isAssignableFrom(dependency)
                || dependency.getSimpleName().endsWith("Repository");
    }
}

package com.luuhoa.fincore.savinggoal;

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
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.luuhoa.fincore.identity.UserAccount;
import com.luuhoa.fincore.identity.UserAccountRepository;
import com.luuhoa.fincore.audit.AuditLogService;
import com.luuhoa.fincore.moneyjar.MoneyJar;
import com.luuhoa.fincore.moneyjar.MoneyJarService;
import com.luuhoa.fincore.shared.api.ConflictException;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class SavingGoalServiceTest {

    @Mock
    private SavingGoalRepository savingGoalRepository;

    @Mock
    private UserAccountRepository userRepository;

    @Mock
    private MoneyJarService moneyJarService;

    @Mock
    private AuditLogService auditLogService;

    private SavingGoalService service;

    @BeforeEach
    void setUp() {
        service = new SavingGoalService(savingGoalRepository, userRepository, moneyJarService, auditLogService);
    }

    @Test
    void reportsCompletedWhenTheLinkedJarReachesTarget() throws Exception {
        UUID userId = UUID.randomUUID();
        UserAccount user = user();
        MoneyJar jar = jar(user, new BigDecimal("1000000"));
        SavingGoal goal = new SavingGoal(user, jar, "Emergency fund", new BigDecimal("1000000"), LocalDate.of(2026, 12, 31));
        setId(goal, UUID.randomUUID());

        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(savingGoalRepository.findAllByUserIdAndStatusNotOrderByCreatedAtAsc(userId, SavingGoalStatus.CANCELLED))
                .thenReturn(List.of(goal));

        SavingGoalResponse response = service.list(userId).getFirst();

        assertThat(response.currentAmount()).isEqualByComparingTo("1000000");
        assertThat(response.remainingAmount()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(response.progressPercentage()).isEqualByComparingTo("100.00");
        assertThat(response.status()).isEqualTo(SavingGoalStatus.COMPLETED);
    }

    @Test
    void rejectsSecondOpenGoalForTheSameJar() throws Exception {
        UUID userId = UUID.randomUUID();
        UUID jarId = UUID.randomUUID();
        MoneyJar jar = jar(user(), BigDecimal.ZERO);
        setId(jar, jarId);
        CreateSavingGoalRequest request = new CreateSavingGoalRequest(jarId, "New laptop", new BigDecimal("30000000"), null);

        when(moneyJarService.requireOwnedActiveJarForSavingGoal(userId, jarId)).thenReturn(jar);
        when(savingGoalRepository.existsByUserIdAndJarIdAndStatusIn(userId, jarId, List.of(SavingGoalStatus.ACTIVE, SavingGoalStatus.PAUSED)))
                .thenReturn(true);

        assertThatThrownBy(() -> service.create(userId, request))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("already has an active saving goal");

        verify(userRepository, never()).findById(any());
    }

    @Test
    void recordsCreationOnlyAfterTheSavingGoalIsPersisted() throws Exception {
        UUID userId = UUID.randomUUID();
        UUID jarId = UUID.randomUUID();
        UUID goalId = UUID.randomUUID();
        UserAccount user = user();
        MoneyJar jar = jar(user, BigDecimal.ZERO);
        setId(jar, jarId);
        SavingGoal goal = new SavingGoal(user, jar, "Emergency fund", new BigDecimal("3000000"), LocalDate.of(2026, 12, 31));
        setId(goal, goalId);
        CreateSavingGoalRequest request = new CreateSavingGoalRequest(jarId, "Emergency fund", new BigDecimal("3000000"), LocalDate.of(2026, 12, 31));

        when(moneyJarService.requireOwnedActiveJarForSavingGoal(userId, jarId)).thenReturn(jar);
        when(savingGoalRepository.existsByUserIdAndJarIdAndStatusIn(userId, jarId, List.of(SavingGoalStatus.ACTIVE, SavingGoalStatus.PAUSED)))
                .thenReturn(false);
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(savingGoalRepository.saveAndFlush(any(SavingGoal.class))).thenReturn(goal);

        SavingGoalResponse response = service.create(userId, request);

        assertThat(response.id()).isEqualTo(goalId);
        verify(auditLogService).record(eq(user), eq("SAVING_GOAL_CREATED"), eq("SAVING_GOAL"), eq(goalId), anyMap());
    }

    private UserAccount user() {
        return new UserAccount("owner@example.com", "hash", "Owner", "VND", "Asia/Ho_Chi_Minh");
    }

    private MoneyJar jar(UserAccount user, BigDecimal allocatedBalance) throws Exception {
        MoneyJar jar = new MoneyJar(user, "Emergency", "VND", null, "#0F8F72", "shield-check", false);
        setId(jar, UUID.randomUUID());
        Field balance = MoneyJar.class.getDeclaredField("allocatedBalance");
        balance.setAccessible(true);
        balance.set(jar, allocatedBalance.setScale(4));
        return jar;
    }

    private void setId(Object target, UUID id) throws Exception {
        Field idField = target.getClass().getDeclaredField("id");
        idField.setAccessible(true);
        idField.set(target, id);
    }
}

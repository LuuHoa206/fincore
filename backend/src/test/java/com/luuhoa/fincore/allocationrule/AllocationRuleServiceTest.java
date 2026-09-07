package com.luuhoa.fincore.allocationrule;

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
import com.luuhoa.fincore.moneyjar.MoneyJar;
import com.luuhoa.fincore.moneyjar.MoneyJarService;
import com.luuhoa.fincore.shared.api.ConflictException;
import com.luuhoa.fincore.wallet.WalletService;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AllocationRuleServiceTest {

    @Mock
    private AllocationRuleRepository allocationRuleRepository;

    @Mock
    private UserAccountRepository userRepository;

    @Mock
    private MoneyJarService moneyJarService;

    @Mock
    private WalletService walletService;

    @Mock
    private AuditLogService auditLogService;

    private AllocationRuleService service;

    @BeforeEach
    void setUp() {
        service = new AllocationRuleService(
                allocationRuleRepository,
                userRepository,
                moneyJarService,
                walletService,
                auditLogService);
    }

    @Test
    void recordsCreatedRuleAfterItsItemsHaveBeenValidated() throws Exception {
        UUID userId = UUID.randomUUID();
        UUID jarId = UUID.randomUUID();
        UUID ruleId = UUID.randomUUID();
        UserAccount user = user();
        MoneyJar jar = jar(user, jarId);
        CreateAllocationRuleRequest request = new CreateAllocationRuleRequest(
                "Payday plan", "VND", true, List.of(new AllocationRuleItemRequest(jarId, new BigDecimal("40.00"))));

        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(moneyJarService.requireOwnedActiveJarForSavingGoal(userId, jarId)).thenReturn(jar);
        when(allocationRuleRepository.existsByUserIdAndNameIgnoreCase(userId, "Payday plan")).thenReturn(false);
        when(allocationRuleRepository.saveAndFlush(any(AllocationRule.class))).thenAnswer(invocation -> {
            AllocationRule rule = invocation.getArgument(0);
            setId(rule, ruleId);
            return rule;
        });

        AllocationRuleResponse response = service.create(userId, request);

        assertThat(response.id()).isEqualTo(ruleId);
        assertThat(response.items()).singleElement().satisfies(item -> assertThat(item.percentage()).isEqualByComparingTo("40.00"));
        verify(auditLogService).record(eq(user), eq("ALLOCATION_RULE_CREATED"), eq("ALLOCATION_RULE"), eq(ruleId), anyMap());
    }

    @Test
    void rejectsDuplicateRuleBeforePersistingOrAuditing() {
        UUID userId = UUID.randomUUID();
        CreateAllocationRuleRequest request = new CreateAllocationRuleRequest(
                "Payday plan", "VND", true, List.of(new AllocationRuleItemRequest(UUID.randomUUID(), new BigDecimal("40.00"))));

        when(userRepository.findById(userId)).thenReturn(Optional.of(user()));
        when(allocationRuleRepository.existsByUserIdAndNameIgnoreCase(userId, "Payday plan")).thenReturn(true);

        assertThatThrownBy(() -> service.create(userId, request))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("name already exists");

        verify(moneyJarService, never()).requireOwnedActiveJarForSavingGoal(any(), any());
        verify(auditLogService, never()).record(any(), any(), any(), any(), any());
    }

    private UserAccount user() {
        return new UserAccount("owner@example.com", "hash", "Owner", "VND", "Asia/Ho_Chi_Minh");
    }

    private MoneyJar jar(UserAccount user, UUID jarId) throws Exception {
        MoneyJar jar = new MoneyJar(user, "Emergency", "VND", null, "#0F8F72", "shield-check", false);
        setId(jar, jarId);
        return jar;
    }

    private void setId(Object target, UUID id) throws Exception {
        Field field = target.getClass().getDeclaredField("id");
        field.setAccessible(true);
        field.set(target, id);
    }
}

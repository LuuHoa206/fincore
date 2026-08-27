package com.luuhoa.fincore.splitbill;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import java.util.UUID;

import com.luuhoa.fincore.shared.api.ConflictException;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class SplitBillTransactionReversalGuardTest {

    @Mock private SplitBillRepository splitBillRepository;
    @Mock private SplitBillPaymentRepository splitBillPaymentRepository;

    @Test
    void blocksReversalOfAnActiveSplitBillExpense() {
        UUID userId = UUID.randomUUID();
        UUID transactionId = UUID.randomUUID();
        when(splitBillRepository.existsByUserIdAndExpenseTransactionIdAndStatusNot(userId, transactionId, SplitBillStatus.CANCELLED))
                .thenReturn(true);
        SplitBillTransactionReversalGuard guard = new SplitBillTransactionReversalGuard(splitBillRepository, splitBillPaymentRepository);

        assertThatThrownBy(() -> guard.assertCanReverse(userId, transactionId))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("linked to a split bill");
    }
}

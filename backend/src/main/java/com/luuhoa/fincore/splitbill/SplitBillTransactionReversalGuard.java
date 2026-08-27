package com.luuhoa.fincore.splitbill;

import java.util.UUID;

import com.luuhoa.fincore.shared.api.ConflictException;
import com.luuhoa.fincore.transaction.TransactionReversalGuard;

import org.springframework.stereotype.Service;

@Service
public class SplitBillTransactionReversalGuard implements TransactionReversalGuard {

    private final SplitBillRepository splitBillRepository;
    private final SplitBillPaymentRepository splitBillPaymentRepository;

    public SplitBillTransactionReversalGuard(
            SplitBillRepository splitBillRepository,
            SplitBillPaymentRepository splitBillPaymentRepository) {
        this.splitBillRepository = splitBillRepository;
        this.splitBillPaymentRepository = splitBillPaymentRepository;
    }

    @Override
    public void assertCanReverse(UUID userId, UUID transactionId) {
        if (splitBillRepository.existsByUserIdAndExpenseTransactionIdAndStatusNot(userId, transactionId, SplitBillStatus.CANCELLED)
                || splitBillPaymentRepository.existsByTransactionId(transactionId)) {
            throw new ConflictException(
                    "SPLIT_BILL_TRANSACTION_CANNOT_BE_REVERSED",
                    "This transaction is linked to a split bill. Reverse or cancel it through the split-bill workflow instead.");
        }
    }
}

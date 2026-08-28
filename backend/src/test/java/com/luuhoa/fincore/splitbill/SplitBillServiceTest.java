package com.luuhoa.fincore.splitbill;

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
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.luuhoa.fincore.identity.UserAccount;
import com.luuhoa.fincore.identity.UserAccountRepository;
import com.luuhoa.fincore.audit.AuditLogService;
import com.luuhoa.fincore.shared.api.ConflictException;
import com.luuhoa.fincore.transaction.TransactionResponse;
import com.luuhoa.fincore.transaction.TransactionService;
import com.luuhoa.fincore.transaction.TransactionStatus;
import com.luuhoa.fincore.transaction.TransactionType;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class SplitBillServiceTest {

    @Mock private SplitBillRepository splitBillRepository;
    @Mock private SplitBillPaymentRepository splitBillPaymentRepository;
    @Mock private UserAccountRepository userRepository;
    @Mock private TransactionService transactionService;
    @Mock private AuditLogService auditLogService;

    private SplitBillService service;

    @BeforeEach
    void setUp() {
        service = new SplitBillService(splitBillRepository, splitBillPaymentRepository, userRepository, transactionService, auditLogService);
    }

    @Test
    void createsOneExpenseAndTracksOnlyTheParticipantSharesAsReceivable() throws Exception {
        UUID userId = UUID.randomUUID();
        UUID walletId = UUID.randomUUID();
        UUID categoryId = UUID.randomUUID();
        UUID expenseId = UUID.randomUUID();
        UserAccount user = user();
        CreateSplitBillRequest request = new CreateSplitBillRequest(
                walletId, categoryId, "Dinner with team", new BigDecimal("900000"), new BigDecimal("300000"),
                "Paid dinner for the team", null, Instant.parse("2026-08-27T12:00:00Z"),
                List.of(new SplitBillParticipantRequest("An", null, new BigDecimal("300000")),
                        new SplitBillParticipantRequest("Binh", "binh@example.com", new BigDecimal("300000"))));
        TransactionResponse expense = transaction(expenseId, walletId, categoryId, TransactionType.EXPENSE, new BigDecimal("900000"));

        when(transactionService.create(any(), any(), any())).thenReturn(expense);
        when(splitBillRepository.findByUserIdAndExpenseTransactionIdWithParticipants(userId, expenseId)).thenReturn(Optional.empty());
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(splitBillRepository.save(any(SplitBill.class))).thenAnswer(invocation -> {
            SplitBill bill = invocation.getArgument(0);
            setIdUnchecked(bill, UUID.randomUUID());
            bill.getParticipants().forEach(participant -> setIdUnchecked(participant, UUID.randomUUID()));
            return bill;
        });
        when(splitBillPaymentRepository.findAllByBillId(any())).thenReturn(List.of());

        SplitBillResponse response = service.create(userId, request, "split-bill-1");

        assertThat(response.totalAmount()).isEqualByComparingTo("900000");
        assertThat(response.payerShareAmount()).isEqualByComparingTo("300000");
        assertThat(response.reimbursableAmount()).isEqualByComparingTo("600000");
        assertThat(response.outstandingAmount()).isEqualByComparingTo("600000");
        assertThat(response.participants()).hasSize(2);
        ArgumentCaptor<com.luuhoa.fincore.transaction.CreateTransactionRequest> transactionRequest = ArgumentCaptor.forClass(com.luuhoa.fincore.transaction.CreateTransactionRequest.class);
        verify(transactionService).create(org.mockito.ArgumentMatchers.eq(userId), transactionRequest.capture(), org.mockito.ArgumentMatchers.eq("split-bill-1"));
        assertThat(transactionRequest.getValue().transactionType()).isEqualTo(TransactionType.EXPENSE);
        assertThat(transactionRequest.getValue().amount()).isEqualByComparingTo("900000");
        verify(auditLogService).record(eq(user), eq("SPLIT_BILL_CREATED"), eq("SPLIT_BILL"), any(), anyMap());
    }

    @Test
    void rejectsAnUnbalancedSplitBeforePostingTheExpense() {
        CreateSplitBillRequest request = new CreateSplitBillRequest(
                UUID.randomUUID(), UUID.randomUUID(), "Dinner", new BigDecimal("900000"), new BigDecimal("300000"),
                "Paid dinner", null, Instant.now(),
                List.of(new SplitBillParticipantRequest("An", null, new BigDecimal("200000"))));

        assertThatThrownBy(() -> service.create(UUID.randomUUID(), request, "split-bill-2"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("must equal");

        verify(transactionService, never()).create(any(), any(), any());
    }

    @Test
    void recordsAPartialReimbursementOnlyOnceAndUpdatesTheReceivableState() throws Exception {
        UUID userId = UUID.randomUUID();
        UUID billId = UUID.randomUUID();
        UUID participantId = UUID.randomUUID();
        UUID transactionId = UUID.randomUUID();
        SplitBill bill = bill(userId, billId, participantId, new BigDecimal("300000"));
        RecordSplitBillPaymentRequest request = new RecordSplitBillPaymentRequest(
                UUID.randomUUID(), UUID.randomUUID(), new BigDecimal("100000"), "Transferred by bank", Instant.now());
        TransactionResponse reimbursement = transaction(transactionId, request.walletId(), request.incomeCategoryId(), TransactionType.INCOME, request.amount());

        when(splitBillRepository.findOwnedForUpdateWithParticipants(billId, userId)).thenReturn(Optional.of(bill));
        when(transactionService.create(any(), any(), any())).thenReturn(reimbursement);
        when(splitBillPaymentRepository.findByTransactionId(transactionId)).thenReturn(Optional.empty());
        when(splitBillPaymentRepository.findAllByBillId(billId)).thenReturn(List.of());
        when(userRepository.findById(userId)).thenReturn(Optional.of(bill.getUser()));

        SplitBillResponse response = service.recordPayment(userId, billId, participantId, request, "split-payment-1");

        assertThat(response.status()).isEqualTo(SplitBillStatus.PARTIALLY_SETTLED);
        assertThat(response.settledAmount()).isEqualByComparingTo("100000");
        assertThat(response.outstandingAmount()).isEqualByComparingTo("200000");
        assertThat(response.participants()).singleElement().satisfies(participant -> {
            assertThat(participant.status()).isEqualTo(SplitBillParticipantStatus.PARTIALLY_PAID);
            assertThat(participant.outstandingAmount()).isEqualByComparingTo("200000");
        });
        verify(splitBillPaymentRepository).save(any(SplitBillPayment.class));
        verify(auditLogService).record(eq(bill.getUser()), eq("SPLIT_BILL_PAYMENT_RECORDED"), eq("SPLIT_BILL"), eq(billId), anyMap());
    }

    @Test
    void rejectsAReimbursementThatExceedsTheParticipantBalanceBeforePostingIncome() throws Exception {
        UUID userId = UUID.randomUUID();
        UUID billId = UUID.randomUUID();
        UUID participantId = UUID.randomUUID();
        SplitBill bill = bill(userId, billId, participantId, new BigDecimal("300000"));
        RecordSplitBillPaymentRequest request = new RecordSplitBillPaymentRequest(
                UUID.randomUUID(), UUID.randomUUID(), new BigDecimal("300001"), null, Instant.now());
        when(splitBillRepository.findOwnedForUpdateWithParticipants(billId, userId)).thenReturn(Optional.of(bill));

        assertThatThrownBy(() -> service.recordPayment(userId, billId, participantId, request, "split-payment-2"))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("greater than");

        verify(transactionService, never()).create(any(), any(), any());
    }

    @Test
    void returnsTheExistingPaymentOnRetryWithoutSettlingTheParticipantTwice() throws Exception {
        UUID userId = UUID.randomUUID();
        UUID billId = UUID.randomUUID();
        UUID participantId = UUID.randomUUID();
        UUID transactionId = UUID.randomUUID();
        SplitBill bill = bill(userId, billId, participantId, new BigDecimal("300000"));
        SplitBillParticipant participant = bill.getParticipants().getFirst();
        participant.recordPayment(new BigDecimal("100000"));
        bill.refreshStatus();
        RecordSplitBillPaymentRequest request = new RecordSplitBillPaymentRequest(
                UUID.randomUUID(), UUID.randomUUID(), new BigDecimal("100000"), null, Instant.now());
        TransactionResponse reimbursement = transaction(transactionId, request.walletId(), request.incomeCategoryId(), TransactionType.INCOME, request.amount());
        SplitBillPayment existingPayment = new SplitBillPayment(participant, transactionId, request.amount(), request.occurredAt());

        when(splitBillRepository.findOwnedForUpdateWithParticipants(billId, userId)).thenReturn(Optional.of(bill));
        when(transactionService.create(any(), any(), any())).thenReturn(reimbursement);
        when(splitBillPaymentRepository.findByTransactionId(transactionId)).thenReturn(Optional.of(existingPayment));
        when(splitBillPaymentRepository.findAllByBillId(billId)).thenReturn(List.of(existingPayment));

        SplitBillResponse response = service.recordPayment(userId, billId, participantId, request, "split-payment-retry");

        assertThat(response.settledAmount()).isEqualByComparingTo("100000");
        assertThat(response.outstandingAmount()).isEqualByComparingTo("200000");
        verify(splitBillPaymentRepository, never()).save(any(SplitBillPayment.class));
        verify(auditLogService, never()).record(any(), any(), any(), any(), any());
    }

    private SplitBill bill(UUID userId, UUID billId, UUID participantId, BigDecimal participantAmount) throws Exception {
        UserAccount user = user();
        SplitBill bill = new SplitBill(user, UUID.randomUUID(), "Dinner", new BigDecimal("600000"), new BigDecimal("300000"), "VND", "Paid dinner", null, Instant.now());
        bill.addParticipant("An", null, participantAmount);
        setId(bill, billId);
        setId(bill.getParticipants().getFirst(), participantId);
        return bill;
    }

    private TransactionResponse transaction(UUID id, UUID walletId, UUID categoryId, TransactionType type, BigDecimal amount) {
        return new TransactionResponse(id, walletId, "Cash", null, null, categoryId, "Category", "circle", "#000000", type,
                TransactionStatus.POSTED, amount, "VND", "Description", null, Instant.now(), Instant.now(), null);
    }

    private UserAccount user() {
        return new UserAccount("owner@example.com", "hash", "Owner", "VND", "Asia/Ho_Chi_Minh");
    }

    private void setId(Object target, UUID id) throws Exception {
        Field field = target.getClass().getDeclaredField("id");
        field.setAccessible(true);
        field.set(target, id);
    }

    private void setIdUnchecked(Object target, UUID id) {
        try {
            setId(target, id);
        } catch (Exception exception) {
            throw new AssertionError("Unable to set test entity id", exception);
        }
    }
}

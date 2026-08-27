package com.luuhoa.fincore.splitbill;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Currency;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

import com.luuhoa.fincore.identity.UserAccount;
import com.luuhoa.fincore.identity.UserAccountRepository;
import com.luuhoa.fincore.shared.api.ConflictException;
import com.luuhoa.fincore.shared.api.ResourceNotFoundException;
import com.luuhoa.fincore.transaction.CreateTransactionRequest;
import com.luuhoa.fincore.transaction.TransactionResponse;
import com.luuhoa.fincore.transaction.TransactionService;
import com.luuhoa.fincore.transaction.TransactionType;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SplitBillService {

    private final SplitBillRepository splitBillRepository;
    private final SplitBillPaymentRepository splitBillPaymentRepository;
    private final UserAccountRepository userRepository;
    private final TransactionService transactionService;

    public SplitBillService(
            SplitBillRepository splitBillRepository,
            SplitBillPaymentRepository splitBillPaymentRepository,
            UserAccountRepository userRepository,
            TransactionService transactionService) {
        this.splitBillRepository = splitBillRepository;
        this.splitBillPaymentRepository = splitBillPaymentRepository;
        this.userRepository = userRepository;
        this.transactionService = transactionService;
    }

    @Transactional(readOnly = true)
    public List<SplitBillResponse> list(UUID userId) {
        return splitBillRepository.findAllByUserIdWithParticipants(userId).stream()
                .map(bill -> toResponse(bill))
                .toList();
    }

    @Transactional(readOnly = true)
    public SplitBillResponse get(UUID userId, UUID billId) {
        return toResponse(requireOwnedBill(userId, billId));
    }

    @Transactional
    public SplitBillResponse create(UUID userId, CreateSplitBillRequest request, String idempotencyKey) {
        String key = requireIdempotencyKey(idempotencyKey);
        validateParticipantAmounts(request);

        TransactionResponse expense = transactionService.create(
                userId,
                new CreateTransactionRequest(
                        request.walletId(),
                        request.expenseCategoryId(),
                        TransactionType.EXPENSE,
                        request.totalAmount(),
                        normalizeRequired(request.description(), "Description"),
                        normalizeOptional(request.notes()),
                        request.occurredAt(),
                        false),
                key);

        SplitBill existing = splitBillRepository.findByUserIdAndExpenseTransactionIdWithParticipants(userId, expense.id()).orElse(null);
        if (existing != null) {
            return toResponse(existing);
        }
        if (expense.transactionType() != TransactionType.EXPENSE
                || !expense.walletId().equals(request.walletId())
                || !request.expenseCategoryId().equals(expense.categoryId())
                || expense.amount().compareTo(request.totalAmount()) != 0) {
            throw new ConflictException("IDEMPOTENCY_KEY_REUSED", "This Idempotency-Key already belongs to a different transaction");
        }

        String currency = expense.currency();
        BigDecimal total = normalizePositiveAmount(request.totalAmount(), currency);
        BigDecimal payerShare = normalizeNonNegativeAmount(request.payerShareAmount(), currency);
        BigDecimal participantTotal = request.participants().stream()
                .map(item -> normalizePositiveAmount(item.owedAmount(), currency))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        if (payerShare.add(participantTotal).compareTo(total) != 0) {
            throw new IllegalArgumentException("Your share and all participant shares must equal the total bill amount");
        }

        SplitBill bill = new SplitBill(
                requireUser(userId),
                expense.id(),
                normalizeRequired(request.name(), "Bill name"),
                total,
                payerShare,
                currency,
                normalizeRequired(request.description(), "Description"),
                normalizeOptional(request.notes()),
                request.occurredAt());
        request.participants().forEach(participant -> bill.addParticipant(
                normalizeRequired(participant.name(), "Participant name"),
                normalizeOptional(participant.contact()),
                normalizePositiveAmount(participant.owedAmount(), currency)));
        bill.refreshStatus();
        return toResponse(splitBillRepository.save(bill));
    }

    @Transactional
    public SplitBillResponse recordPayment(
            UUID userId,
            UUID billId,
            UUID participantId,
            RecordSplitBillPaymentRequest request,
            String idempotencyKey) {
        String key = requireIdempotencyKey(idempotencyKey);
        SplitBill bill = splitBillRepository.findOwnedForUpdateWithParticipants(billId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("SPLIT_BILL_NOT_FOUND", "Split bill was not found"));
        if (bill.getStatus() == SplitBillStatus.CANCELLED) {
            throw new ConflictException("SPLIT_BILL_CANCELLED", "Cannot record a payment for a cancelled split bill");
        }
        SplitBillParticipant participant = bill.getParticipants().stream()
                .filter(item -> item.getId().equals(participantId))
                .findFirst()
                .orElseThrow(() -> new ResourceNotFoundException("SPLIT_BILL_PARTICIPANT_NOT_FOUND", "Participant was not found"));
        BigDecimal amount = normalizePositiveAmount(request.amount(), bill.getCurrency());
        if (amount.compareTo(participant.getOutstandingAmount()) > 0) {
            throw new ConflictException("SPLIT_BILL_PAYMENT_EXCEEDS_OUTSTANDING", "Payment is greater than the participant's outstanding amount");
        }

        TransactionResponse reimbursement = transactionService.create(
                userId,
                new CreateTransactionRequest(
                        request.walletId(),
                        request.incomeCategoryId(),
                        TransactionType.INCOME,
                        amount,
                        "Hoàn tiền từ " + participant.getName() + " cho " + bill.getName(),
                        normalizeOptional(request.notes()),
                        request.occurredAt(),
                        false),
                key);

        SplitBillPayment existingPayment = splitBillPaymentRepository.findByTransactionId(reimbursement.id()).orElse(null);
        if (existingPayment != null) {
            if (!existingPayment.getParticipant().getId().equals(participantId)) {
                throw new ConflictException("IDEMPOTENCY_KEY_REUSED", "This Idempotency-Key already belongs to another split-bill payment");
            }
            return toResponse(bill);
        }
        if (reimbursement.transactionType() != TransactionType.INCOME
                || !reimbursement.walletId().equals(request.walletId())
                || !request.incomeCategoryId().equals(reimbursement.categoryId())
                || reimbursement.amount().compareTo(amount) != 0
                || !reimbursement.currency().equals(bill.getCurrency())) {
            throw new ConflictException("IDEMPOTENCY_KEY_REUSED", "This Idempotency-Key already belongs to a different transaction");
        }

        participant.recordPayment(amount);
        splitBillPaymentRepository.save(new SplitBillPayment(participant, reimbursement.id(), amount, request.occurredAt()));
        bill.refreshStatus();
        return toResponse(bill);
    }

    private SplitBillResponse toResponse(SplitBill bill) {
        return SplitBillResponse.from(bill, splitBillPaymentRepository.findAllByBillId(bill.getId()));
    }

    private SplitBill requireOwnedBill(UUID userId, UUID billId) {
        return splitBillRepository.findOwnedWithParticipants(billId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("SPLIT_BILL_NOT_FOUND", "Split bill was not found"));
    }

    private UserAccount requireUser(UUID userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("USER_NOT_FOUND", "User account was not found"));
    }

    private void validateParticipantAmounts(CreateSplitBillRequest request) {
        if (request.payerShareAmount().signum() < 0) {
            throw new IllegalArgumentException("Your share cannot be negative");
        }
        if (request.payerShareAmount().compareTo(request.totalAmount()) > 0) {
            throw new IllegalArgumentException("Your share cannot be greater than the total bill amount");
        }
        if (request.participants().stream().map(SplitBillParticipantRequest::owedAmount).anyMatch(amount -> amount.signum() <= 0)) {
            throw new IllegalArgumentException("Each participant share must be greater than zero");
        }
        BigDecimal participantTotal = request.participants().stream()
                .map(SplitBillParticipantRequest::owedAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        if (request.payerShareAmount().add(participantTotal).compareTo(request.totalAmount()) != 0) {
            throw new IllegalArgumentException("Your share and all participant shares must equal the total bill amount");
        }
    }

    private String requireIdempotencyKey(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Idempotency-Key is required when recording a split bill or reimbursement");
        }
        String key = value.trim();
        if (key.length() > 100) {
            throw new IllegalArgumentException("Idempotency-Key must contain at most 100 characters");
        }
        return key;
    }

    private String normalizeRequired(String value, String fieldName) {
        String normalized = value == null ? "" : value.trim();
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
        return normalized;
    }

    private String normalizeOptional(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private BigDecimal normalizePositiveAmount(BigDecimal value, String currency) {
        if (value == null || value.signum() <= 0) {
            throw new IllegalArgumentException("Amount must be greater than zero");
        }
        return normalizeAmount(value, currency);
    }

    private BigDecimal normalizeNonNegativeAmount(BigDecimal value, String currency) {
        if (value == null || value.signum() < 0) {
            throw new IllegalArgumentException("Amount cannot be negative");
        }
        return normalizeAmount(value, currency);
    }

    private BigDecimal normalizeAmount(BigDecimal value, String currencyCode) {
        int scale = Math.max(Currency.getInstance(currencyCode.toUpperCase(Locale.ROOT)).getDefaultFractionDigits(), 0);
        if (value.scale() > scale) {
            throw new IllegalArgumentException("Amount has more decimal places than the bill currency supports");
        }
        return value.setScale(scale);
    }
}

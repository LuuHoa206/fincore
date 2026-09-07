package com.luuhoa.fincore.moneyjar;

import java.math.BigDecimal;
import java.util.Currency;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

import com.luuhoa.fincore.identity.UserAccount;
import com.luuhoa.fincore.identity.UserAccountRepository;
import com.luuhoa.fincore.audit.AuditLogService;
import com.luuhoa.fincore.shared.api.ConflictException;
import com.luuhoa.fincore.shared.api.ResourceNotFoundException;
import com.luuhoa.fincore.transaction.FinancialTransaction;
import com.luuhoa.fincore.wallet.Wallet;
import com.luuhoa.fincore.wallet.WalletService;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class MoneyJarService {

    private final MoneyJarRepository moneyJarRepository;
    private final JarMovementRepository jarMovementRepository;
    private final WalletService walletService;
    private final UserAccountRepository userRepository;
    private final AuditLogService auditLogService;

    public MoneyJarService(
            MoneyJarRepository moneyJarRepository,
            JarMovementRepository jarMovementRepository,
            WalletService walletService,
            UserAccountRepository userRepository,
            AuditLogService auditLogService) {
        this.moneyJarRepository = moneyJarRepository;
        this.jarMovementRepository = jarMovementRepository;
        this.walletService = walletService;
        this.userRepository = userRepository;
        this.auditLogService = auditLogService;
    }

    @Transactional(readOnly = true)
    public List<MoneyJarResponse> list(UUID userId) {
        return moneyJarRepository.findAllByUserIdAndArchivedFalseOrderByCreatedAtAsc(userId).stream()
                .map(MoneyJarResponse::from)
                .toList();
    }

    @Transactional
    public MoneyJarResponse create(UUID userId, CreateMoneyJarRequest request) {
        String name = normalizeName(request.name());
        if (moneyJarRepository.existsByUserIdAndNameIgnoreCase(userId, name)) {
            throw duplicateJar();
        }

        UserAccount user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("USER_NOT_FOUND", "User account was not found"));
        MoneyJar jar = new MoneyJar(
                user,
                name,
                normalizeCurrency(request.currency()),
                request.spendingLimit(),
                normalizeOptional(request.color()),
                normalizeOptional(request.icon()),
                request.allowNegative());
        MoneyJar saved;
        try {
            saved = moneyJarRepository.saveAndFlush(jar);
        } catch (DataIntegrityViolationException exception) {
            throw duplicateJar();
        }
        auditLogService.record(user, "MONEY_JAR_CREATED", "MONEY_JAR", saved.getId(), jarDetails(saved));
        return MoneyJarResponse.from(saved);
    }

    @Transactional
    public MoneyJarResponse update(UUID userId, UUID jarId, UpdateMoneyJarRequest request) {
        MoneyJar jar = requireOwnedActiveJar(userId, jarId);
        UserAccount user = requireUser(userId);
        String name = request.name() == null ? null : normalizeName(request.name());
        if (name != null && !jar.getName().equalsIgnoreCase(name)
                && moneyJarRepository.existsByUserIdAndNameIgnoreCase(userId, name)) {
            throw duplicateJar();
        }
        jar.updateDetails(
                name,
                request.spendingLimit(),
                normalizeOptional(request.color()),
                normalizeOptional(request.icon()),
                request.allowNegative());
        try {
            moneyJarRepository.flush();
        } catch (DataIntegrityViolationException exception) {
            throw duplicateJar();
        }
        auditLogService.record(user, "MONEY_JAR_UPDATED", "MONEY_JAR", jar.getId(), jarDetails(jar));
        return MoneyJarResponse.from(jar);
    }

    @Transactional
    public void archive(UUID userId, UUID jarId) {
        MoneyJar jar = requireOwnedActiveJar(userId, jarId);
        if (jar.getAllocatedBalance().signum() != 0) {
            throw new ConflictException(
                    "JAR_HAS_ALLOCATED_BALANCE",
                    "Release the allocated money before archiving this jar");
        }
        jar.archive();
        auditLogService.record(requireUser(userId), "MONEY_JAR_ARCHIVED", "MONEY_JAR", jar.getId(), jarDetails(jar));
    }

    @Transactional
    public JarAllocationResponse allocate(UUID userId, UUID jarId, ChangeJarAllocationRequest request) {
        AllocationContext context = lockAllocationContext(userId, jarId);
        BigDecimal amount = normalizeAmount(request.amount(), context.jar().getCurrency());
        if (context.availableToAllocate().compareTo(amount) < 0) {
            throw new ConflictException("INSUFFICIENT_UNALLOCATED_BALANCE", "There is not enough unallocated wallet balance");
        }
        context.jar().applyAllocation(amount);
        jarMovementRepository.save(JarMovement.allocation(context.jar(), amount));
        auditLogService.record(requireUser(userId), "MONEY_JAR_ALLOCATION_ADDED", "MONEY_JAR", context.jar().getId(),
                Map.of("amount", amount.toPlainString(), "currency", context.jar().getCurrency()));
        return new JarAllocationResponse(
                MoneyJarResponse.from(context.jar()),
                context.availableToAllocate().subtract(amount));
    }

    @Transactional
    public JarAllocationResponse release(UUID userId, UUID jarId, ChangeJarAllocationRequest request) {
        AllocationContext context = lockAllocationContext(userId, jarId);
        BigDecimal amount = normalizeAmount(request.amount(), context.jar().getCurrency());
        context.jar().applyAllocation(amount.negate());
        jarMovementRepository.save(JarMovement.release(context.jar(), amount));
        auditLogService.record(requireUser(userId), "MONEY_JAR_ALLOCATION_RELEASED", "MONEY_JAR", context.jar().getId(),
                Map.of("amount", amount.toPlainString(), "currency", context.jar().getCurrency()));
        return new JarAllocationResponse(
                MoneyJarResponse.from(context.jar()),
                context.availableToAllocate().add(amount));
    }

    @Transactional
    public JarTransferResponse transfer(UUID userId, TransferBetweenJarsRequest request) {
        if (request.sourceJarId().equals(request.destinationJarId())) {
            throw new ConflictException("JAR_TRANSFER_SAME_JAR", "Choose two different money jars for a transfer");
        }

        List<MoneyJar> jars = moneyJarRepository.findAllActiveByUserIdForUpdate(userId);
        MoneyJar sourceJar = requireFromLockedJars(jars, request.sourceJarId());
        MoneyJar destinationJar = requireFromLockedJars(jars, request.destinationJarId());
        if (!sourceJar.getCurrency().equals(destinationJar.getCurrency())) {
            throw new ConflictException("JAR_TRANSFER_CURRENCY_MISMATCH", "Money jars must use the same currency");
        }

        BigDecimal amount = normalizeAmount(request.amount(), sourceJar.getCurrency());
        if (sourceJar.getAllocatedBalance().compareTo(amount) < 0) {
            throw new ConflictException("INSUFFICIENT_JAR_BALANCE", "The source money jar does not have enough allocated balance");
        }
        sourceJar.applyAllocation(amount.negate());
        destinationJar.applyAllocation(amount);
        jarMovementRepository.saveAll(List.of(
                JarMovement.transferOut(sourceJar, amount),
                JarMovement.transferIn(destinationJar, amount)));
        auditLogService.record(requireUser(userId), "MONEY_JAR_TRANSFERRED", "MONEY_JAR", sourceJar.getId(), Map.of(
                "amount", amount.toPlainString(),
                "currency", sourceJar.getCurrency(),
                "sourceJar", sourceJar.getName(),
                "destinationJar", destinationJar.getName(),
                "destinationJarId", destinationJar.getId().toString()));
        return new JarTransferResponse(
                MoneyJarResponse.from(sourceJar),
                MoneyJarResponse.from(destinationJar),
                amount);
    }

    private AllocationContext lockAllocationContext(UUID userId, UUID jarId) {
        List<Wallet> wallets = walletService.lockActiveWalletsForAllocation(userId);
        List<MoneyJar> jars = moneyJarRepository.findAllActiveByUserIdForUpdate(userId);
        MoneyJar jar = jars.stream()
                .filter(candidate -> candidate.getId().equals(jarId))
                .findFirst()
                .orElseThrow(() -> new ResourceNotFoundException("MONEY_JAR_NOT_FOUND", "Money jar was not found"));
        BigDecimal walletBalance = wallets.stream()
                .filter(wallet -> wallet.getCurrency().equals(jar.getCurrency()))
                .map(Wallet::getCurrentBalance)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal allocatedBalance = jars.stream()
                .filter(candidate -> candidate.getCurrency().equals(jar.getCurrency()))
                .map(MoneyJar::getAllocatedBalance)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        return new AllocationContext(jar, walletBalance.subtract(allocatedBalance));
    }

    public MoneyJar requireOwnedActiveJarForSavingGoal(UUID userId, UUID jarId) {
        return requireOwnedActiveJar(userId, jarId);
    }

    /**
     * Locks all active jars in the same order as manual allocation. Other transactional
     * use cases must use this boundary rather than lock individual jar rows themselves.
     */
    @Transactional
    public List<MoneyJar> lockActiveJarsForAllocation(UUID userId) {
        return moneyJarRepository.findAllActiveByUserIdForUpdate(userId);
    }

    /**
     * Applies an already validated rule plan while the caller holds the ordered wallet and
     * jar locks. The movement remains linked to its source transaction for auditability.
     */
    @Transactional
    public void applyAutomaticAllocations(
            List<MoneyJar> lockedJars,
            FinancialTransaction transaction,
            List<AutomaticJarAllocation> allocations) {
        Map<UUID, MoneyJar> jarsById = lockedJars.stream()
                .collect(java.util.stream.Collectors.toMap(MoneyJar::getId, jar -> jar));
        List<JarMovement> movements = allocations.stream().map(allocation -> {
            MoneyJar jar = jarsById.get(allocation.jarId());
            if (jar == null || !jar.getCurrency().equals(transaction.getCurrency())) {
                throw new ConflictException("ALLOCATION_JAR_UNAVAILABLE", "An allocation money jar is no longer available");
            }
            jar.applyAllocation(allocation.amount());
            return JarMovement.allocation(jar, transaction, allocation.amount());
        }).toList();
        jarMovementRepository.saveAll(movements);
    }

    public record AutomaticJarAllocation(UUID jarId, BigDecimal amount) {
    }

    private MoneyJar requireOwnedActiveJar(UUID userId, UUID jarId) {
        return moneyJarRepository.findByIdAndUserIdAndArchivedFalse(jarId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("MONEY_JAR_NOT_FOUND", "Money jar was not found"));
    }

    private MoneyJar requireFromLockedJars(List<MoneyJar> jars, UUID jarId) {
        return jars.stream()
                .filter(jar -> jar.getId().equals(jarId))
                .findFirst()
                .orElseThrow(() -> new ResourceNotFoundException("MONEY_JAR_NOT_FOUND", "Money jar was not found"));
    }

    private UserAccount requireUser(UUID userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("USER_NOT_FOUND", "User account was not found"));
    }

    private Map<String, Object> jarDetails(MoneyJar jar) {
        Map<String, Object> details = new java.util.LinkedHashMap<>();
        details.put("name", jar.getName());
        details.put("currency", jar.getCurrency());
        details.put("allowNegative", jar.isAllowNegative());
        if (jar.getSpendingLimit() != null) {
            details.put("spendingLimit", jar.getSpendingLimit().toPlainString());
        }
        return details;
    }

    private BigDecimal normalizeAmount(BigDecimal requestedAmount, String currencyCode) {
        Currency currency = Currency.getInstance(currencyCode.toUpperCase(Locale.ROOT));
        int allowedScale = Math.max(currency.getDefaultFractionDigits(), 0);
        if (requestedAmount.scale() > allowedScale) {
            throw new IllegalArgumentException("Amount has more decimal places than the jar currency supports");
        }
        BigDecimal normalizedAmount = requestedAmount.setScale(allowedScale);
        if (normalizedAmount.signum() <= 0) {
            throw new IllegalArgumentException("Amount must be greater than zero for the jar currency");
        }
        return normalizedAmount;
    }

    private String normalizeCurrency(String requestedCurrency) {
        String currency = requestedCurrency.trim().toUpperCase(Locale.ROOT);
        try {
            Currency.getInstance(currency);
            return currency;
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("Currency must be a valid ISO 4217 code");
        }
    }

    private String normalizeName(String name) {
        String normalized = name.trim();
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException("Money jar name must not be blank");
        }
        return normalized;
    }

    private String normalizeOptional(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private ConflictException duplicateJar() {
        return new ConflictException("MONEY_JAR_ALREADY_EXISTS", "A money jar with this name already exists");
    }

    private record AllocationContext(MoneyJar jar, BigDecimal availableToAllocate) {
    }
}

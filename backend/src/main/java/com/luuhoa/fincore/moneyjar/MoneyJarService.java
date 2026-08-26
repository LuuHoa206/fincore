package com.luuhoa.fincore.moneyjar;

import java.math.BigDecimal;
import java.util.Currency;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

import com.luuhoa.fincore.identity.UserAccount;
import com.luuhoa.fincore.identity.UserAccountRepository;
import com.luuhoa.fincore.shared.api.ConflictException;
import com.luuhoa.fincore.shared.api.ResourceNotFoundException;
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

    public MoneyJarService(
            MoneyJarRepository moneyJarRepository,
            JarMovementRepository jarMovementRepository,
            WalletService walletService,
            UserAccountRepository userRepository) {
        this.moneyJarRepository = moneyJarRepository;
        this.jarMovementRepository = jarMovementRepository;
        this.walletService = walletService;
        this.userRepository = userRepository;
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
        try {
            return MoneyJarResponse.from(moneyJarRepository.saveAndFlush(jar));
        } catch (DataIntegrityViolationException exception) {
            throw duplicateJar();
        }
    }

    @Transactional
    public MoneyJarResponse update(UUID userId, UUID jarId, UpdateMoneyJarRequest request) {
        MoneyJar jar = requireOwnedActiveJar(userId, jarId);
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
            return MoneyJarResponse.from(jar);
        } catch (DataIntegrityViolationException exception) {
            throw duplicateJar();
        }
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
        return new JarAllocationResponse(
                MoneyJarResponse.from(context.jar()),
                context.availableToAllocate().add(amount));
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

    private MoneyJar requireOwnedActiveJar(UUID userId, UUID jarId) {
        return moneyJarRepository.findByIdAndUserIdAndArchivedFalse(jarId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("MONEY_JAR_NOT_FOUND", "Money jar was not found"));
    }

    private BigDecimal normalizeAmount(BigDecimal requestedAmount, String currencyCode) {
        Currency currency = Currency.getInstance(currencyCode.toUpperCase(Locale.ROOT));
        int allowedScale = Math.max(currency.getDefaultFractionDigits(), 0);
        if (requestedAmount.scale() > allowedScale) {
            throw new IllegalArgumentException("Amount has more decimal places than the jar currency supports");
        }
        return requestedAmount.setScale(allowedScale);
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

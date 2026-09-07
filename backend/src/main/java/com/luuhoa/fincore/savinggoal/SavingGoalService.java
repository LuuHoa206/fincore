package com.luuhoa.fincore.savinggoal;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.Currency;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

import com.luuhoa.fincore.audit.AuditLogService;
import com.luuhoa.fincore.identity.UserAccount;
import com.luuhoa.fincore.identity.UserAccountRepository;
import com.luuhoa.fincore.moneyjar.MoneyJar;
import com.luuhoa.fincore.moneyjar.MoneyJarService;
import com.luuhoa.fincore.shared.api.ConflictException;
import com.luuhoa.fincore.shared.api.ResourceNotFoundException;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SavingGoalService {

    private static final List<SavingGoalStatus> OPEN_STATUSES = List.of(SavingGoalStatus.ACTIVE, SavingGoalStatus.PAUSED);

    private final SavingGoalRepository savingGoalRepository;
    private final UserAccountRepository userRepository;
    private final MoneyJarService moneyJarService;
    private final AuditLogService auditLogService;

    public SavingGoalService(
            SavingGoalRepository savingGoalRepository,
            UserAccountRepository userRepository,
            MoneyJarService moneyJarService,
            AuditLogService auditLogService) {
        this.savingGoalRepository = savingGoalRepository;
        this.userRepository = userRepository;
        this.moneyJarService = moneyJarService;
        this.auditLogService = auditLogService;
    }

    @Transactional(readOnly = true)
    public List<SavingGoalResponse> list(UUID userId) {
        ZoneId zoneId = ZoneId.of(requireUser(userId).getTimeZone());
        return savingGoalRepository.findAllByUserIdAndStatusNotOrderByCreatedAtAsc(userId, SavingGoalStatus.CANCELLED).stream()
                .map(goal -> toResponse(goal, zoneId))
                .toList();
    }

    @Transactional
    public SavingGoalResponse create(UUID userId, CreateSavingGoalRequest request) {
        MoneyJar jar = moneyJarService.requireOwnedActiveJarForSavingGoal(userId, request.jarId());
        if (savingGoalRepository.existsByUserIdAndJarIdAndStatusIn(userId, jar.getId(), OPEN_STATUSES)) {
            throw new ConflictException("JAR_ALREADY_HAS_SAVING_GOAL", "This money jar already has an active saving goal");
        }
        UserAccount user = requireUser(userId);
        SavingGoal goal = new SavingGoal(
                user,
                jar,
                normalizeName(request.name()),
                normalizeAmount(request.targetAmount(), jar.getCurrency()),
                request.targetDate());
        SavingGoal savedGoal;
        try {
            savedGoal = savingGoalRepository.saveAndFlush(goal);
        } catch (DataIntegrityViolationException exception) {
            throw new ConflictException("JAR_ALREADY_HAS_SAVING_GOAL", "This money jar already has an active saving goal");
        }
        auditLogService.record(user, "SAVING_GOAL_CREATED", "SAVING_GOAL", savedGoal.getId(), goalDetails(savedGoal));
        return toResponse(savedGoal, ZoneId.of(user.getTimeZone()));
    }

    @Transactional
    public SavingGoalResponse update(UUID userId, UUID goalId, UpdateSavingGoalRequest request) {
        SavingGoal goal = requireOwnedGoal(userId, goalId);
        UserAccount user = requireUser(userId);
        goal.updateDetails(
                request.name() == null ? null : normalizeName(request.name()),
                request.targetAmount() == null ? null : normalizeAmount(request.targetAmount(), goal.getJar().getCurrency()),
                request.targetDate());
        auditLogService.record(user, "SAVING_GOAL_UPDATED", "SAVING_GOAL", goal.getId(), goalDetails(goal));
        return toResponse(goal, ZoneId.of(user.getTimeZone()));
    }

    @Transactional
    public SavingGoalResponse changeStatus(UUID userId, UUID goalId, ChangeSavingGoalStatusRequest request) {
        SavingGoal goal = requireOwnedGoal(userId, goalId);
        UserAccount user = requireUser(userId);
        if (request.status() == SavingGoalStatus.COMPLETED) {
            throw new IllegalArgumentException("Completed status is calculated from the money jar balance");
        }
        goal.changeStatus(request.status());
        auditLogService.record(user, "SAVING_GOAL_STATUS_CHANGED", "SAVING_GOAL", goal.getId(), goalDetails(goal));
        return toResponse(goal, ZoneId.of(user.getTimeZone()));
    }

    private SavingGoal requireOwnedGoal(UUID userId, UUID goalId) {
        return savingGoalRepository.findByIdAndUserIdAndStatusNot(goalId, userId, SavingGoalStatus.CANCELLED)
                .orElseThrow(() -> new ResourceNotFoundException("SAVING_GOAL_NOT_FOUND", "Saving goal was not found"));
    }

    private UserAccount requireUser(UUID userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("USER_NOT_FOUND", "User account was not found"));
    }

    private Map<String, Object> goalDetails(SavingGoal goal) {
        Map<String, Object> details = new java.util.LinkedHashMap<>();
        details.put("name", goal.getName());
        details.put("currency", goal.getJar().getCurrency());
        details.put("targetAmount", goal.getTargetAmount().toPlainString());
        details.put("status", goal.getStatus().name());
        if (goal.getTargetDate() != null) {
            details.put("targetDate", goal.getTargetDate().toString());
        }
        return details;
    }

    private SavingGoalResponse toResponse(SavingGoal goal, ZoneId zoneId) {
        MoneyJar jar = goal.getJar();
        BigDecimal currentAmount = jar.getAllocatedBalance();
        BigDecimal remaining = goal.getTargetAmount().subtract(currentAmount).max(BigDecimal.ZERO);
        BigDecimal progress = currentAmount.multiply(BigDecimal.valueOf(100))
                .divide(goal.getTargetAmount(), 2, RoundingMode.HALF_UP)
                .min(BigDecimal.valueOf(100));
        SavingGoalStatus status = goal.getStatus() == SavingGoalStatus.ACTIVE && remaining.signum() == 0
                ? SavingGoalStatus.COMPLETED
                : goal.getStatus();
        return new SavingGoalResponse(
                goal.getId(),
                jar.getId(),
                jar.getName(),
                jar.getCurrency(),
                goal.getName(),
                goal.getTargetAmount(),
                currentAmount,
                remaining,
                progress,
                suggestedMonthlyContribution(remaining, goal.getTargetDate(), zoneId, jar.getCurrency()),
                goal.getTargetDate(),
                status,
                goal.getCreatedAt(),
                goal.getUpdatedAt());
    }

    private BigDecimal suggestedMonthlyContribution(BigDecimal remaining, LocalDate targetDate, ZoneId zoneId, String currency) {
        if (targetDate == null || remaining.signum() == 0) return null;
        YearMonth currentMonth = YearMonth.now(zoneId);
        YearMonth targetMonth = YearMonth.from(targetDate);
        long months = Math.max(1, ChronoUnit.MONTHS.between(currentMonth, targetMonth) + 1);
        int scale = Math.max(Currency.getInstance(currency).getDefaultFractionDigits(), 0);
        return remaining.divide(BigDecimal.valueOf(months), scale, RoundingMode.CEILING);
    }

    private String normalizeName(String name) {
        String normalized = name.trim();
        if (normalized.isEmpty()) throw new IllegalArgumentException("Saving goal name must not be blank");
        return normalized;
    }

    private BigDecimal normalizeAmount(BigDecimal requestedAmount, String currencyCode) {
        int allowedScale = Math.max(Currency.getInstance(currencyCode.toUpperCase(Locale.ROOT)).getDefaultFractionDigits(), 0);
        if (requestedAmount.scale() > allowedScale) {
            throw new IllegalArgumentException("Amount has more decimal places than the goal currency supports");
        }
        return requestedAmount.setScale(allowedScale);
    }
}

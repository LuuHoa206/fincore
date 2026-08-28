package com.luuhoa.fincore.notification;

import java.time.Instant;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import com.luuhoa.fincore.audit.AuditLogService;
import com.luuhoa.fincore.budget.BudgetResponse;
import com.luuhoa.fincore.budget.BudgetService;
import com.luuhoa.fincore.budget.BudgetStatus;
import com.luuhoa.fincore.identity.UserAccount;
import com.luuhoa.fincore.identity.UserAccountRepository;
import com.luuhoa.fincore.recurring.RecurringRuleResponse;
import com.luuhoa.fincore.recurring.RecurringRuleService;
import com.luuhoa.fincore.shared.api.ResourceNotFoundException;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class NotificationService {

    private static final int MAX_NOTIFICATIONS = 20;
    private static final int UPCOMING_DAYS = 3;

    private final NotificationReadStateRepository readStateRepository;
    private final UserAccountRepository userRepository;
    private final RecurringRuleService recurringRuleService;
    private final BudgetService budgetService;
    private final AuditLogService auditLogService;

    public NotificationService(
            NotificationReadStateRepository readStateRepository,
            UserAccountRepository userRepository,
            RecurringRuleService recurringRuleService,
            BudgetService budgetService,
            AuditLogService auditLogService) {
        this.readStateRepository = readStateRepository;
        this.userRepository = userRepository;
        this.recurringRuleService = recurringRuleService;
        this.budgetService = budgetService;
        this.auditLogService = auditLogService;
    }

    @Transactional(readOnly = true)
    public List<FinancialNotificationResponse> list(UUID userId) {
        List<GeneratedNotification> generated = generate(userId);
        if (generated.isEmpty()) {
            return List.of();
        }
        Set<String> readKeys = readStateRepository.findAllByUserIdAndNotificationKeyIn(
                        userId, generated.stream().map(GeneratedNotification::key).toList())
                .stream()
                .map(NotificationReadState::getNotificationKey)
                .collect(Collectors.toSet());
        return generated.stream()
                .map(notification -> notification.toResponse(readKeys.contains(notification.key())))
                .toList();
    }

    @Transactional(readOnly = true)
    public long unreadCount(UUID userId) {
        return list(userId).stream().filter(notification -> !notification.read()).count();
    }

    @Transactional
    public void markRead(UUID userId, MarkNotificationReadRequest request) {
        String key = request.notificationKey().trim();
        UserAccount user = requireUser(userId);
        ensureCurrentNotification(userId, key);
        if (readStateRepository.insertIfAbsent(userId, key) == 0) {
            return;
        }
        auditLogService.record(user, "NOTIFICATION_MARKED_READ", "NOTIFICATION", null, java.util.Map.of("key", key));
    }

    @Transactional
    public void markUnread(UUID userId, MarkNotificationReadRequest request) {
        String key = request.notificationKey().trim();
        ensureCurrentNotification(userId, key);
        readStateRepository.deleteByUserIdAndNotificationKey(userId, key);
    }

    private List<GeneratedNotification> generate(UUID userId) {
        UserAccount user = requireUser(userId);
        Instant now = Instant.now();
        Instant upcomingCutoff = now.plus(UPCOMING_DAYS, ChronoUnit.DAYS);
        ZoneId zoneId = ZoneId.of(user.getTimeZone());

        List<GeneratedNotification> recurringNotifications = recurringRuleService.upcoming(userId, 10).stream()
                .filter(rule -> !rule.nextRunAt().isAfter(upcomingCutoff))
                .map(rule -> fromRecurringRule(rule, now, zoneId))
                .toList();
        List<GeneratedNotification> budgetNotifications = budgetService.list(userId, null).stream()
                .filter(budget -> budget.status() != BudgetStatus.ON_TRACK)
                .map(this::fromBudget)
                .toList();

        return java.util.stream.Stream.concat(recurringNotifications.stream(), budgetNotifications.stream())
                .sorted(Comparator.comparing(GeneratedNotification::priority).reversed()
                        .thenComparing(GeneratedNotification::occurredAt, Comparator.reverseOrder()))
                .limit(MAX_NOTIFICATIONS)
                .toList();
    }

    private GeneratedNotification fromRecurringRule(RecurringRuleResponse rule, Instant now, ZoneId zoneId) {
        boolean due = !rule.nextRunAt().isAfter(now);
        String key = "recurring:" + rule.id() + ":" + rule.nextRunAt().toEpochMilli();
        String scheduledAt = java.time.format.DateTimeFormatter.ofPattern("HH:mm dd/MM")
                .withZone(zoneId)
                .format(rule.nextRunAt());
        if (due) {
            return new GeneratedNotification(
                    key,
                    NotificationPriority.WARNING,
                    "Giao dịch định kỳ đã đến hạn",
                    rule.name() + " cần được ghi nhận. Hạn: " + scheduledAt + ".",
                    "/recurring",
                    rule.nextRunAt());
        }
        return new GeneratedNotification(
                key,
                NotificationPriority.INFO,
                "Giao dịch định kỳ sắp đến hạn",
                rule.name() + " sẽ đến hạn lúc " + scheduledAt + ".",
                "/recurring",
                rule.nextRunAt());
    }

    private GeneratedNotification fromBudget(BudgetResponse budget) {
        String key = "budget:" + budget.id() + ":" + budget.periodStart() + ":" + budget.status();
        boolean exceeded = budget.status() == BudgetStatus.EXCEEDED;
        String usage = budget.usagePercentage().setScale(0, java.math.RoundingMode.HALF_UP).toPlainString();
        return new GeneratedNotification(
                key,
                exceeded ? NotificationPriority.CRITICAL : NotificationPriority.WARNING,
                exceeded ? "Ngân sách đã vượt hạn mức" : "Ngân sách sắp vượt hạn mức",
                budget.categoryName() + " đã dùng " + usage + "% ngân sách tháng này.",
                "/budgets",
                budget.updatedAt());
    }

    private void ensureCurrentNotification(UUID userId, String key) {
        boolean exists = generate(userId).stream().anyMatch(notification -> notification.key().equals(key));
        if (!exists) {
            throw new ResourceNotFoundException("NOTIFICATION_NOT_FOUND", "Notification was not found");
        }
    }

    private UserAccount requireUser(UUID userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("USER_NOT_FOUND", "User account was not found"));
    }

    private record GeneratedNotification(
            String key,
            NotificationPriority priority,
            String title,
            String message,
            String destination,
            Instant occurredAt) {
        FinancialNotificationResponse toResponse(boolean read) {
            return new FinancialNotificationResponse(key, priority, title, message, destination, occurredAt, read);
        }
    }
}

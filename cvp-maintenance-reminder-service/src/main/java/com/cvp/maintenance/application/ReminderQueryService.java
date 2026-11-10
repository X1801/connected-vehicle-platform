package com.cvp.maintenance.application;

import com.cvp.maintenance.adapter.outbound.persistence.ReminderEntity;
import com.cvp.maintenance.adapter.outbound.persistence.ReminderNotificationRepository;
import com.cvp.maintenance.adapter.outbound.persistence.ReminderRepository;
import com.cvp.maintenance.domain.NotificationStatus;
import com.cvp.maintenance.domain.ReminderSeverity;
import com.cvp.maintenance.domain.ReminderStatus;
import org.springframework.data.domain.*;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
public class ReminderQueryService {
    public static final int DEFAULT_PAGE_SIZE = 20;
    public static final int MAX_PAGE_SIZE = 100;
    private static final Sort STABLE_ORDER = Sort.by(Sort.Order.desc("updatedAt"), Sort.Order.asc("id"));

    private final ReminderRepository reminders;
    private final ReminderNotificationRepository notifications;

    public ReminderQueryService(ReminderRepository reminders, ReminderNotificationRepository notifications) {
        this.reminders = reminders;
        this.notifications = notifications;
    }

    public List<Result> list(String vehicleId, ReminderStatus status, ReminderSeverity severity, Integer page, Integer size) {
        if (page != null && page < 0) throw new IllegalArgumentException("page must be zero or greater");
        if (size != null && size < 1) throw new IllegalArgumentException("size must be greater than zero");
        if (size != null && size > MAX_PAGE_SIZE) throw new IllegalArgumentException("size must not exceed " + MAX_PAGE_SIZE);
        Pageable pageable = page == null && size == null ? Pageable.unpaged(STABLE_ORDER)
                : PageRequest.of(page == null ? 0 : page, size == null ? DEFAULT_PAGE_SIZE : size, STABLE_ORDER);
        List<ReminderEntity> result = reminders.findForVehicle(vehicleId, status, severity, pageable).getContent();
        return withLatestNotification(result);
    }

    public Result get(UUID reminderId) {
        ReminderEntity reminder = reminders.findById(reminderId)
                .orElseThrow(() -> new NotFoundException("Reminder not found"));
        return withLatestNotification(List.of(reminder)).getFirst();
    }

    private List<Result> withLatestNotification(List<ReminderEntity> result) {
        if (result.isEmpty()) return List.of();
        Map<UUID, NotificationStatus> latest = new HashMap<>();
        notifications.findStatesByReminderIds(result.stream().map(r -> r.id).toList())
                .forEach(notification -> latest.putIfAbsent(notification.getReminderId(), notification.getStatus()));
        return result.stream().map(reminder -> new Result(reminder, latest.get(reminder.id))).toList();
    }

    public record Result(ReminderEntity reminder, NotificationStatus notificationStatus) { }
}

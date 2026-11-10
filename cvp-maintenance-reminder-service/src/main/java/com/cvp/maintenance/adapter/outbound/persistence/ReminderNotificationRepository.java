package com.cvp.maintenance.adapter.outbound.persistence;

import org.springframework.data.jpa.repository.*;

import java.util.*;

public interface ReminderNotificationRepository extends JpaRepository<ReminderNotificationEntity, UUID> {
    Optional<ReminderNotificationEntity> findByIdempotencyKey(String key);

    @Query("select n.reminder.id as reminderId, n.status as status from ReminderNotificationEntity n " +
            "where n.reminder.id in :reminderIds order by n.updatedAt desc, n.id desc")
    List<ReminderNotificationState> findStatesByReminderIds(Collection<UUID> reminderIds);

    interface ReminderNotificationState {
        UUID getReminderId();
        com.cvp.maintenance.domain.NotificationStatus getStatus();
    }
}

package com.luuhoa.fincore.notification;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface NotificationReadStateRepository extends JpaRepository<NotificationReadState, UUID> {

    List<NotificationReadState> findAllByUserIdAndNotificationKeyIn(UUID userId, Collection<String> notificationKeys);

    @Modifying
    @Query(value = """
            INSERT INTO notification_read_states (id, user_id, notification_key, read_at)
            VALUES (gen_random_uuid(), :userId, :notificationKey, CURRENT_TIMESTAMP)
            ON CONFLICT (user_id, notification_key) DO NOTHING
            """, nativeQuery = true)
    int insertIfAbsent(@Param("userId") UUID userId, @Param("notificationKey") String notificationKey);

    void deleteByUserIdAndNotificationKey(UUID userId, String notificationKey);
}

package com.luuhoa.fincore.notification;

import java.time.Instant;
import java.util.UUID;

import com.luuhoa.fincore.identity.UserAccount;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

@Entity
@Table(name = "notification_read_states", uniqueConstraints = @UniqueConstraint(
        name = "uk_notification_read_states_user_key", columnNames = {"user_id", "notification_key"}))
class NotificationReadState {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private UserAccount user;

    @Column(name = "notification_key", nullable = false, length = 320)
    private String notificationKey;

    @Column(name = "read_at", nullable = false)
    private Instant readAt = Instant.now();

    protected NotificationReadState() {
    }

    NotificationReadState(UserAccount user, String notificationKey) {
        this.user = user;
        this.notificationKey = notificationKey;
    }

    String getNotificationKey() {
        return notificationKey;
    }
}

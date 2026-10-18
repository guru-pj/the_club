package com.theclub.notificationservice.service;

import com.theclub.common.web.ApiException;
import com.theclub.notificationservice.dto.EmailRequest;
import com.theclub.notificationservice.email.EmailOutboxService;
import com.theclub.notificationservice.model.Notification;
import com.theclub.notificationservice.repository.NotificationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NotificationServiceTest {

    @Mock
    NotificationRepository notifications;
    @Mock
    EmailOutboxService emailOutbox;

    NotificationService service;
    UUID userId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        service = new NotificationService(notifications, emailOutbox,
                Clock.fixed(Instant.parse("2026-01-01T00:00:00Z"), ZoneOffset.UTC));
    }

    @Test
    void storesNotificationWithoutEmail() {
        when(notifications.save(any(Notification.class))).thenAnswer(inv -> inv.getArgument(0));

        Notification saved = service.create(userId, "CLUB_JOINED", "Welcome", null);

        assertThat(saved.getUserId()).isEqualTo(userId);
        assertThat(saved.isRead()).isFalse();
        verify(emailOutbox, never()).enqueue(any());
    }

    @Test
    void storesNotificationAndQueuesEmail() {
        when(notifications.save(any(Notification.class))).thenAnswer(inv -> inv.getArgument(0));
        EmailRequest email = new EmailRequest("a@b.com", "Ticket", "<p>hi</p>", List.of());

        service.create(userId, "EVENT_REGISTRATION_CONFIRMED", "You're in", email);

        verify(emailOutbox).enqueue(email);
    }

    @Test
    void truncatesLongMessages() {
        when(notifications.save(any(Notification.class))).thenAnswer(inv -> inv.getArgument(0));

        Notification saved = service.create(userId, "T".repeat(80), "m".repeat(400), null);

        assertThat(saved.getMessage()).hasSize(255);
        assertThat(saved.getType()).hasSize(50);
    }

    @Test
    void markReadOfSomeoneElsesNotificationIsNotFound() {
        UUID id = UUID.randomUUID();
        when(notifications.findByIdAndUserId(id, userId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.markRead(id, userId)).isInstanceOf(ApiException.class);
    }

    @Test
    void rejectsOversizedPage() {
        assertThatThrownBy(() -> service.list(userId, 0, 101)).isInstanceOf(ApiException.class);
    }
}

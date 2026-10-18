package com.theclub.notificationservice.service;

import com.theclub.common.web.ApiException;
import com.theclub.notificationservice.dto.EmailRequest;
import com.theclub.notificationservice.model.Notification;
import com.theclub.notificationservice.repository.NotificationRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.UUID;

@Service
public class NotificationService {

    static final int MAX_PAGE_SIZE = 100;

    private final NotificationRepository notifications;
    private final EmailSender emailSender;
    private final Clock clock;

    public NotificationService(NotificationRepository notifications, EmailSender emailSender, Clock clock) {
        this.notifications = notifications;
        this.emailSender = emailSender;
        this.clock = clock;
    }

    /**
     * Stores the in-app notification and, when {@code email} is given, hands it to the async sender.
     */
    @Transactional
    public Notification create(UUID userId, String type, String message, EmailRequest email) {
        Notification notification = notifications.save(new Notification(userId, type, message, LocalDateTime.now(clock)));
        if (email != null) {
            emailSender.send(email);
        }
        return notification;
    }

    @Transactional(readOnly = true)
    public Page<Notification> list(UUID userId, int page, int size) {
        if (page < 0 || size < 1 || size > MAX_PAGE_SIZE) {
            throw ApiException.badRequest("page must be >= 0 and size between 1 and " + MAX_PAGE_SIZE);
        }
        return notifications.findByUserIdOrderByCreatedAtDescIdDesc(userId, PageRequest.of(page, size));
    }

    @Transactional(readOnly = true)
    public long unreadCount(UUID userId) {
        return notifications.countByUserIdAndReadFalse(userId);
    }

    @Transactional
    public Notification markRead(UUID id, UUID userId) {
        Notification notification = notifications.findByIdAndUserId(id, userId)
                .orElseThrow(() -> ApiException.notFound("Notification not found"));
        notification.setRead(true);
        return notification;
    }

    @Transactional
    public int markAllRead(UUID userId) {
        return notifications.markAllRead(userId);
    }
}

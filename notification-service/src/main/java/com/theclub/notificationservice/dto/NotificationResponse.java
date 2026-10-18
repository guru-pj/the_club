package com.theclub.notificationservice.dto;

import com.theclub.notificationservice.model.Notification;

import java.time.LocalDateTime;
import java.util.UUID;

public record NotificationResponse(UUID id, UUID userId, String type, String message, boolean isRead,
                                   LocalDateTime createdAt) {

    public static NotificationResponse from(Notification notification) {
        return new NotificationResponse(notification.getId(), notification.getUserId(), notification.getType(),
                notification.getMessage(), notification.isRead(), notification.getCreatedAt());
    }
}

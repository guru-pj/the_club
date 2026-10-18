package com.theclub.clubservice.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

import java.util.UUID;

@FeignClient(name = "notification-service", contextId = "notificationClient",
        url = "${theclub.clients.notification-service.url:}", path = "/internal/notifications")
public interface NotificationClient {

    @PostMapping
    void create(@RequestBody NotificationRequest request);

    /** In-app only; club-service never sends email. */
    record NotificationRequest(UUID userId, String type, String message) {
    }
}

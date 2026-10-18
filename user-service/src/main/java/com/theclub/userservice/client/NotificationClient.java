package com.theclub.userservice.client;

import com.fasterxml.jackson.annotation.JsonInclude;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

import java.util.UUID;

@FeignClient(name = "notification-service", url = "${theclub.clients.notification-service.url:}",
        path = "/internal/notifications")
public interface NotificationClient {

    @PostMapping
    void send(@RequestBody Notification notification);

    @JsonInclude(JsonInclude.Include.NON_NULL)
    record Notification(UUID userId, String type, String message, Email email) {
    }

    record Email(String to, String subject, String body) {
    }
}

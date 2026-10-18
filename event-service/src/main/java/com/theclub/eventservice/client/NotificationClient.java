package com.theclub.eventservice.client;

import com.fasterxml.jackson.annotation.JsonInclude;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

import java.util.List;
import java.util.UUID;

@FeignClient(name = "notification-service", contextId = "notificationClient",
        url = "${theclub.clients.notification-service.url:}", path = "/internal/notifications")
public interface NotificationClient {

    @PostMapping
    NotificationCreated create(@RequestBody NotificationRequest request);

    @JsonInclude(JsonInclude.Include.NON_NULL)
    record NotificationRequest(UUID userId, String type, String message, EmailMessage email) {
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    record EmailMessage(String to, String subject, String body, List<EmailAttachment> attachments) {
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    record EmailAttachment(String filename, String contentType, String base64Content, boolean inline,
                           String contentId) {
    }

    record NotificationCreated(UUID id) {
    }
}

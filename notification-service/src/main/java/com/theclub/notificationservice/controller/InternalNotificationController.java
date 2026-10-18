package com.theclub.notificationservice.controller;

import com.theclub.notificationservice.dto.CreatedResponse;
import com.theclub.notificationservice.dto.InternalNotificationRequest;
import com.theclub.notificationservice.service.NotificationService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Service-to-service API (requires X-Internal-Key; never routed by the gateway).
 */
@RestController
@RequestMapping("/internal/notifications")
public class InternalNotificationController {

    private final NotificationService notificationService;

    public InternalNotificationController(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.ACCEPTED)
    public CreatedResponse create(@Valid @RequestBody InternalNotificationRequest request) {
        return new CreatedResponse(notificationService.create(request.userId(), request.type(), request.message(),
                request.email()).getId());
    }
}

package com.theclub.notificationservice.controller;

import com.theclub.common.security.AuthenticatedUser;
import com.theclub.common.web.ApiException;
import com.theclub.notificationservice.dto.CreateNotificationRequest;
import com.theclub.notificationservice.dto.NotificationResponse;
import com.theclub.notificationservice.dto.PageResponse;
import com.theclub.notificationservice.dto.UnreadCountResponse;
import com.theclub.notificationservice.service.NotificationService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/notifications")
public class NotificationController {

    private final NotificationService notificationService;

    public NotificationController(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    @GetMapping("/me")
    public PageResponse<NotificationResponse> mine(@AuthenticationPrincipal AuthenticatedUser caller,
                                                   @RequestParam(defaultValue = "0") int page,
                                                   @RequestParam(defaultValue = "20") int size) {
        return PageResponse.of(notificationService.list(caller.id(), page, size), NotificationResponse::from);
    }

    @GetMapping("/user/{userId}")
    public PageResponse<NotificationResponse> forUser(@PathVariable UUID userId,
                                                      @AuthenticationPrincipal AuthenticatedUser caller,
                                                      @RequestParam(defaultValue = "0") int page,
                                                      @RequestParam(defaultValue = "20") int size) {
        if (!caller.id().equals(userId) && !caller.isAdmin()) {
            throw ApiException.forbidden("You can only view your own notifications");
        }
        return PageResponse.of(notificationService.list(userId, page, size), NotificationResponse::from);
    }

    @GetMapping("/me/unread-count")
    public UnreadCountResponse unreadCount(@AuthenticationPrincipal AuthenticatedUser caller) {
        return new UnreadCountResponse(notificationService.unreadCount(caller.id()));
    }

    @PatchMapping("/{id}/read")
    public NotificationResponse markRead(@PathVariable UUID id, @AuthenticationPrincipal AuthenticatedUser caller) {
        return NotificationResponse.from(notificationService.markRead(id, caller.id()));
    }

    @PostMapping("/me/read-all")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void markAllRead(@AuthenticationPrincipal AuthenticatedUser caller) {
        notificationService.markAllRead(caller.id());
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('ADMIN')")
    public NotificationResponse create(@Valid @RequestBody CreateNotificationRequest request) {
        return NotificationResponse.from(
                notificationService.create(request.userId(), request.type(), request.message(), null));
    }
}

package com.theclub.activityservice.controller;

import com.theclub.activityservice.dto.ActivityRequest;
import com.theclub.activityservice.dto.ActivityResponse;
import com.theclub.activityservice.dto.PageResponse;
import com.theclub.activityservice.service.ActivityService;
import com.theclub.common.security.AuthenticatedUser;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/activities")
public class ActivityController {

    private final ActivityService activityService;

    public ActivityController(ActivityService activityService) {
        this.activityService = activityService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ActivityResponse create(@Valid @RequestBody ActivityRequest request,
                                   @AuthenticationPrincipal AuthenticatedUser caller) {
        return ActivityResponse.from(activityService.create(caller.id(), request));
    }

    @GetMapping("/me")
    public PageResponse<ActivityResponse> mine(
            @AuthenticationPrincipal AuthenticatedUser caller,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime to,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return PageResponse.of(activityService.list(caller.id(), null, from, to, page, size), ActivityResponse::from);
    }

    @GetMapping("/user/{userId}")
    public PageResponse<ActivityResponse> byUser(
            @PathVariable UUID userId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime to,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return PageResponse.of(activityService.list(userId, null, from, to, page, size), ActivityResponse::from);
    }

    @GetMapping("/{id}")
    public ActivityResponse get(@PathVariable UUID id) {
        return ActivityResponse.from(activityService.get(id));
    }

    @PutMapping("/{id}")
    public ActivityResponse update(@PathVariable UUID id, @Valid @RequestBody ActivityRequest request,
                                   @AuthenticationPrincipal AuthenticatedUser caller) {
        return ActivityResponse.from(activityService.update(id, caller, request));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable UUID id, @AuthenticationPrincipal AuthenticatedUser caller) {
        activityService.delete(id, caller);
    }
}

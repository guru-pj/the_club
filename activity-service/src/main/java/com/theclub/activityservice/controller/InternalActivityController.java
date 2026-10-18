package com.theclub.activityservice.controller;

import com.theclub.activityservice.dto.ActivityResponse;
import com.theclub.activityservice.dto.StatsRequest;
import com.theclub.activityservice.dto.UserActivityStats;
import com.theclub.activityservice.service.ActivityService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * Service-to-service API (requires X-Internal-Key; never routed by the gateway).
 */
@RestController
@RequestMapping("/internal/activities")
public class InternalActivityController {

    private final ActivityService activityService;

    public InternalActivityController(ActivityService activityService) {
        this.activityService = activityService;
    }

    @PostMapping("/stats")
    public List<UserActivityStats> stats(@Valid @RequestBody StatsRequest request) {
        return activityService.stats(request.userIds(), request.from(), request.to());
    }

    @GetMapping("/{id}")
    public ActivityResponse get(@PathVariable UUID id) {
        return ActivityResponse.from(activityService.get(id));
    }
}

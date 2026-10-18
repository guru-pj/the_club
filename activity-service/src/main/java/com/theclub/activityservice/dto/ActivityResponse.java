package com.theclub.activityservice.dto;

import com.theclub.activityservice.model.Activity;
import com.theclub.activityservice.model.ActivitySource;
import com.theclub.activityservice.model.ActivityType;

import java.time.LocalDateTime;
import java.util.UUID;

public record ActivityResponse(UUID id, UUID userId, String title, ActivityType type, double distance, int duration,
                               Integer calories, LocalDateTime startTime, LocalDateTime endTime,
                               ActivitySource source, String externalId, LocalDateTime createdAt) {

    public static ActivityResponse from(Activity a) {
        return new ActivityResponse(a.getId(), a.getUserId(), a.getTitle(), a.getType(), a.getDistance(),
                a.getDuration(), a.getCalories(), a.getStartTime(), a.getEndTime(), a.getSource(),
                a.getExternalId(), a.getCreatedAt());
    }
}

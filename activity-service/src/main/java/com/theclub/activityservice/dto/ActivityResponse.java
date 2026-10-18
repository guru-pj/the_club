package com.theclub.activityservice.dto;

import com.theclub.activityservice.model.Activity;
import com.theclub.activityservice.model.ActivitySource;
import com.theclub.activityservice.model.ActivityType;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * @param duration           moving time in seconds
 * @param elapsedTime        total time in seconds including pauses (Strava only)
 * @param startTime          local wall-clock start of the activity
 * @param startTimeUtc       absolute start instant (Strava only)
 * @param privateActivity    hidden on Strava; excluded from club leaderboards unless the athlete opted in
 */
public record ActivityResponse(UUID id, UUID userId, String title, ActivityType type, double distance, int duration,
                               Integer calories, LocalDateTime startTime, LocalDateTime endTime,
                               ActivitySource source, String externalId, LocalDateTime createdAt,
                               Integer elapsedTime, Double totalElevationGain, Double averageHeartrate,
                               String deviceName, boolean privateActivity, Instant startTimeUtc, String timeZone) {

    public static ActivityResponse from(Activity a) {
        return new ActivityResponse(a.getId(), a.getUserId(), a.getTitle(), a.getType(), a.getDistance(),
                a.getDuration(), a.getCalories(), a.getStartTime(), a.getEndTime(), a.getSource(),
                a.getExternalId(), a.getCreatedAt(), a.getElapsedTime(), a.getTotalElevationGain(),
                a.getAverageHeartrate(), a.getDeviceName(), a.isPrivateActivity(), a.getStartTimeUtc(),
                a.getTimeZone());
    }
}

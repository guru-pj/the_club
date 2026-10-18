package com.theclub.activityservice.strava;

import com.theclub.activityservice.model.Activity;
import com.theclub.activityservice.model.ActivitySource;
import com.theclub.activityservice.repository.ActivityRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

/**
 * Stores Strava activities in the member's account, keyed by the Strava id so replays are idempotent.
 */
@Component
public class StravaActivityImporter {

    private static final int MAX_TITLE = 100;

    private final ActivityRepository activities;

    public StravaActivityImporter(ActivityRepository activities) {
        this.activities = activities;
    }

    /**
     * @return true when a new activity was created, false when an existing one was updated.
     */
    @Transactional
    public boolean upsert(UUID userId, StravaActivity source) {
        String externalId = String.valueOf(source.id());
        Optional<Activity> existing = activities.findBySourceAndExternalId(ActivitySource.STRAVA, externalId);
        Activity activity = existing.orElseGet(() -> {
            Activity created = new Activity(userId, ActivitySource.STRAVA);
            created.setExternalId(externalId);
            return created;
        });
        activity.setUserId(userId);
        String title = source.name() == null || source.name().isBlank() ? "Strava activity" : source.name().trim();
        activity.setTitle(title.length() > MAX_TITLE ? title.substring(0, MAX_TITLE) : title);
        activity.setType(StravaTypeMapper.map(source.sportType(), source.type()));
        activity.setDistance(source.distance() / 1000.0);
        int duration = source.movingTime() > 0 ? source.movingTime() : source.elapsedTime();
        activity.setDuration(duration);
        activity.setCalories(source.calories() == null ? null : (int) Math.round(source.calories()));
        LocalDateTime start = parseLocal(source.startDateLocal());
        activity.setStartTime(start);
        activity.setEndTime(start.plusSeconds(Math.max(source.elapsedTime(), duration)));
        activities.save(activity);
        return existing.isEmpty();
    }

    @Transactional
    public void delete(UUID userId, long stravaActivityId) {
        activities.findBySourceAndExternalId(ActivitySource.STRAVA, String.valueOf(stravaActivityId))
                .filter(activity -> activity.getUserId().equals(userId))
                .ifPresent(activities::delete);
    }

    /**
     * Strava's start_date_local is wall-clock time with a misleading "Z" suffix.
     */
    static LocalDateTime parseLocal(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Strava activity has no start_date_local");
        }
        String trimmed = value.endsWith("Z") ? value.substring(0, value.length() - 1) : value;
        return LocalDateTime.parse(trimmed);
    }
}

package com.theclub.activityservice.strava;

import com.theclub.activityservice.model.Activity;
import com.theclub.activityservice.model.ActivitySource;
import com.theclub.activityservice.repository.ActivityRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.DateTimeException;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;
import java.util.Optional;
import java.util.UUID;

/**
 * Stores Strava activities in the member's account, keyed by the Strava id so replays are idempotent.
 */
@Component
public class StravaActivityImporter {

    private static final int MAX_TITLE = 100;
    private static final int MAX_DEVICE = 255;

    public enum Result { CREATED, UPDATED }

    private final ActivityRepository activities;

    public StravaActivityImporter(ActivityRepository activities) {
        this.activities = activities;
    }

    /**
     * @throws IllegalArgumentException when Strava's data is unusable (no start time)
     */
    @Transactional
    public Result upsert(UUID userId, StravaActivity source) {
        String externalId = String.valueOf(source.id());
        Optional<Activity> existing = activities.findBySourceAndExternalId(ActivitySource.STRAVA, externalId);
        Activity activity = existing.orElseGet(() -> {
            Activity created = new Activity(userId, ActivitySource.STRAVA);
            created.setExternalId(externalId);
            return created;
        });
        activity.setUserId(userId);
        String title = source.name() == null || source.name().isBlank() ? "Strava activity" : source.name().trim();
        activity.setTitle(truncate(title, MAX_TITLE));
        activity.setType(StravaTypeMapper.map(source.sportType(), source.type()));
        activity.setDistance(Math.max(0, source.distance()) / 1000.0);
        int moving = source.movingTime() > 0 ? source.movingTime() : source.elapsedTime();
        // A 0-second activity is invalid for our model (duration >= 1)
        activity.setDuration(Math.max(1, moving));
        activity.setElapsedTime(source.elapsedTime() > 0 ? source.elapsedTime() : null);
        activity.setCalories(source.calories() == null ? null : (int) Math.round(source.calories()));
        activity.setTotalElevationGain(source.totalElevationGain());
        activity.setAverageHeartrate(source.averageHeartrate());
        activity.setDeviceName(source.deviceName() == null ? null : truncate(source.deviceName(), MAX_DEVICE));
        activity.setPrivateActivity(source.isPrivate());

        Instant startUtc = parseInstant(source.startDate());
        ZoneId zone = parseZone(source.timezone());
        LocalDateTime start = startDateLocal(source.startDateLocal(), startUtc, zone);
        activity.setStartTimeUtc(startUtc);
        activity.setTimeZone(zone == null ? null : zone.getId());
        activity.setStartTime(start);
        activity.setEndTime(start.plusSeconds(Math.max(source.elapsedTime(), activity.getDuration())));
        activities.save(activity);
        return existing.isEmpty() ? Result.CREATED : Result.UPDATED;
    }

    @Transactional
    public void delete(UUID userId, long stravaActivityId) {
        activities.findBySourceAndExternalId(ActivitySource.STRAVA, String.valueOf(stravaActivityId))
                .filter(activity -> activity.getUserId().equals(userId))
                .ifPresent(activities::delete);
    }

    /**
     * Wall-clock start in the activity's own zone. Strava's start_date_local carries a misleading "Z"; when it is
     * missing, it's derived from start_date (UTC) and the timezone.
     */
    static LocalDateTime startDateLocal(String startDateLocal, Instant startUtc, ZoneId zone) {
        if (startDateLocal != null && !startDateLocal.isBlank()) {
            String trimmed = startDateLocal.endsWith("Z")
                    ? startDateLocal.substring(0, startDateLocal.length() - 1) : startDateLocal;
            try {
                return LocalDateTime.parse(trimmed);
            } catch (DateTimeParseException e) {
                // fall through to start_date + timezone
            }
        }
        if (startUtc != null) {
            return LocalDateTime.ofInstant(startUtc, zone == null ? ZoneId.of("UTC") : zone);
        }
        throw new IllegalArgumentException("Strava activity has no start time");
    }

    static Instant parseInstant(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return Instant.parse(value);
        } catch (DateTimeParseException e) {
            return null;
        }
    }

    /** Strava's timezone looks like "(GMT+05:30) Asia/Kolkata"; the IANA id follows the parenthesis. */
    static ZoneId parseZone(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String id = value.contains(") ") ? value.substring(value.indexOf(") ") + 2).trim() : value.trim();
        try {
            return ZoneId.of(id);
        } catch (DateTimeException e) {
            return null;
        }
    }

    private static String truncate(String value, int max) {
        return value.length() > max ? value.substring(0, max) : value;
    }
}

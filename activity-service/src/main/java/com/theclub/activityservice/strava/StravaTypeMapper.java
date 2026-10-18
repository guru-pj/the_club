package com.theclub.activityservice.strava;

import com.theclub.activityservice.model.ActivityType;

import java.util.Map;

/**
 * Maps Strava sport types to our activity types.
 */
public final class StravaTypeMapper {

    private static final Map<String, ActivityType> TYPES = Map.ofEntries(
            Map.entry("Run", ActivityType.RUN),
            Map.entry("TrailRun", ActivityType.RUN),
            Map.entry("VirtualRun", ActivityType.RUN),
            Map.entry("Ride", ActivityType.RIDE),
            Map.entry("VirtualRide", ActivityType.RIDE),
            Map.entry("MountainBikeRide", ActivityType.RIDE),
            Map.entry("GravelRide", ActivityType.RIDE),
            Map.entry("EBikeRide", ActivityType.RIDE),
            Map.entry("Walk", ActivityType.WALK),
            Map.entry("Hike", ActivityType.HIKE),
            Map.entry("Swim", ActivityType.SWIM),
            Map.entry("Yoga", ActivityType.YOGA),
            Map.entry("WeightTraining", ActivityType.WORKOUT),
            Map.entry("Workout", ActivityType.WORKOUT),
            Map.entry("Crossfit", ActivityType.WORKOUT));

    private StravaTypeMapper() {
    }

    /**
     * Prefers sport_type (more specific) and falls back to the legacy type field.
     */
    public static ActivityType map(String sportType, String type) {
        if (sportType != null && TYPES.containsKey(sportType)) {
            return TYPES.get(sportType);
        }
        if (type != null && TYPES.containsKey(type)) {
            return TYPES.get(type);
        }
        return ActivityType.OTHER;
    }
}

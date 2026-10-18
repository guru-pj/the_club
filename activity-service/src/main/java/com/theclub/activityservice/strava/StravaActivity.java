package com.theclub.activityservice.strava;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Subset of Strava's activity representation (GET /activities/{id}, GET /athlete/activities).
 *
 * @param distance          metres
 * @param movingTime        seconds in motion
 * @param elapsedTime       seconds including pauses
 * @param startDate         UTC instant, e.g. "2026-10-18T00:30:00Z"
 * @param startDateLocal    wall-clock time in the activity's zone with a misleading "Z", e.g. "2026-10-18T06:00:00Z"
 * @param timezone          e.g. "(GMT+05:30) Asia/Kolkata"
 * @param privateActivity   true when the activity is visible only to the athlete
 * @param visibility        "everyone", "followers_only" or "only_me" (newer API field)
 * @param averageHeartrate  present only when recorded with a heart-rate monitor
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record StravaActivity(@JsonProperty("id") long id,
                             @JsonProperty("name") String name,
                             @JsonProperty("type") String type,
                             @JsonProperty("sport_type") String sportType,
                             @JsonProperty("distance") double distance,
                             @JsonProperty("moving_time") int movingTime,
                             @JsonProperty("elapsed_time") int elapsedTime,
                             @JsonProperty("calories") Double calories,
                             @JsonProperty("start_date") String startDate,
                             @JsonProperty("start_date_local") String startDateLocal,
                             @JsonProperty("timezone") String timezone,
                             @JsonProperty("total_elevation_gain") Double totalElevationGain,
                             @JsonProperty("average_heartrate") Double averageHeartrate,
                             @JsonProperty("device_name") String deviceName,
                             @JsonProperty("private") Boolean privateActivity,
                             @JsonProperty("visibility") String visibility,
                             @JsonProperty("athlete") StravaTokenResponse.Athlete athlete) {

    public boolean ownedBy(long athleteId) {
        return athlete != null && athlete.id() == athleteId;
    }

    /** Private = hidden from everyone but the athlete. "followers_only" is treated as shareable. */
    public boolean isPrivate() {
        return Boolean.TRUE.equals(privateActivity) || "only_me".equals(visibility);
    }
}

package com.theclub.activityservice.strava;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Subset of Strava's activity representation.
 *
 * @param distance   metres
 * @param movingTime seconds
 * @param startDateLocal ISO timestamp in the athlete's local zone, e.g. "2026-10-18T06:00:00Z"
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
                             @JsonProperty("start_date_local") String startDateLocal,
                             @JsonProperty("athlete") StravaTokenResponse.Athlete athlete) {

    public boolean ownedBy(long athleteId) {
        return athlete != null && athlete.id() == athleteId;
    }
}

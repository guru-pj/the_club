package com.theclub.activityservice.strava;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Response of POST /oauth/token for both authorization_code and refresh_token grants.
 * The athlete is only present for the authorization_code grant.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record StravaTokenResponse(@JsonProperty("access_token") String accessToken,
                                  @JsonProperty("refresh_token") String refreshToken,
                                  @JsonProperty("expires_at") long expiresAt,
                                  @JsonProperty("athlete") Athlete athlete) {

    @Override
    public String toString() {
        return "StravaTokenResponse[expiresAt=" + expiresAt + ", athlete=" + athlete + "]";
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Athlete(@JsonProperty("id") long id) {
    }
}

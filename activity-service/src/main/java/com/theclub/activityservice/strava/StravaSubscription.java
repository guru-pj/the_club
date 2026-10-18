package com.theclub.activityservice.strava;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * A Strava push subscription (an app can have exactly one).
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record StravaSubscription(@JsonProperty("id") long id,
                                 @JsonProperty("callback_url") String callbackUrl,
                                 @JsonProperty("created_at") String createdAt,
                                 @JsonProperty("updated_at") String updatedAt) {
}

package com.theclub.activityservice.strava;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Map;

/**
 * Push event from a Strava webhook subscription.
 *
 * @param objectId activity id or athlete id depending on objectType
 * @param ownerId  athlete id
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record StravaWebhookEvent(@JsonProperty("object_type") String objectType,
                                 @JsonProperty("object_id") long objectId,
                                 @JsonProperty("aspect_type") String aspectType,
                                 @JsonProperty("owner_id") long ownerId,
                                 @JsonProperty("updates") Map<String, Object> updates,
                                 @JsonProperty("event_time") long eventTime,
                                 @JsonProperty("subscription_id") Long subscriptionId) {
}

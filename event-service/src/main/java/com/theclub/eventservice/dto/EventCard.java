package com.theclub.eventservice.dto;

import java.util.Set;

/**
 * An event in the upcoming-events feed.
 *
 * @param sources    why it's listed: FOLLOWING, SUBSCRIBED and/or NEARBY
 * @param distanceKm distance to the organising club, when the feed was asked for a location
 */
public record EventCard(EventResponse event, String clubName, String clubLogoUrl, Set<String> sources,
                        Double distanceKm) {
}

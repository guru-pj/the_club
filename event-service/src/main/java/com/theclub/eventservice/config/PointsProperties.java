package com.theclub.eventservice.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * @param eventsPerStar number of attended events that earn one star.
 */
@ConfigurationProperties(prefix = "theclub.points")
public record PointsProperties(Integer eventsPerStar) {

    public PointsProperties {
        if (eventsPerStar == null || eventsPerStar < 1) {
            eventsPerStar = 5;
        }
    }
}

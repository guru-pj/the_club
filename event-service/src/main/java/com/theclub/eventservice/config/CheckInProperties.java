package com.theclub.eventservice.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * Check-in is accepted from {@code startTime - opensBefore} until {@code startTime + closesAfter}.
 */
@ConfigurationProperties(prefix = "theclub.checkin")
public record CheckInProperties(Duration opensBefore, Duration closesAfter) {

    public CheckInProperties {
        if (opensBefore == null) {
            opensBefore = Duration.ofHours(6);
        }
        if (closesAfter == null) {
            closesAfter = Duration.ofHours(12);
        }
    }
}

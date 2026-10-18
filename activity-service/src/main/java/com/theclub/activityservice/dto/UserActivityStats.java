package com.theclub.activityservice.dto;

import java.util.UUID;

public record UserActivityStats(UUID userId, Double totalDistanceKm, Long totalDurationSeconds, Long sessions) {
}

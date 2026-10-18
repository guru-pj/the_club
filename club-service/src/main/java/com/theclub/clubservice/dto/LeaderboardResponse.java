package com.theclub.clubservice.dto;

import com.theclub.clubservice.service.LeaderboardService.Metric;
import com.theclub.clubservice.service.LeaderboardService.Period;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public record LeaderboardResponse(UUID clubId, Metric metric, Period period, LocalDateTime from, LocalDateTime to,
                                  List<Entry> entries) {

    public record Entry(int rank, UUID userId, String name, double totalDistanceKm, long totalDurationSeconds,
                        long sessions) {
    }
}

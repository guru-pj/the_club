package com.theclub.clubservice.service;

import com.theclub.clubservice.client.ActivityClient;
import com.theclub.clubservice.dto.LeaderboardResponse;
import com.theclub.common.web.ApiException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Ranks club members by distance, moving time or number of sessions over a period.
 */
@Service
public class LeaderboardService {

    private static final Logger log = LoggerFactory.getLogger(LeaderboardService.class);

    public enum Metric { DISTANCE, TIME, SESSIONS }

    public enum Period { WEEK, MONTH, YEAR, ALL }

    private final ClubService clubService;
    private final ActivityClient activityClient;
    private final Clock clock;

    public LeaderboardService(ClubService clubService, ActivityClient activityClient, Clock clock) {
        this.clubService = clubService;
        this.activityClient = activityClient;
        this.clock = clock;
    }

    public LeaderboardResponse leaderboard(UUID clubId, Metric metric, Period period) {
        clubService.get(clubId);
        LocalDateTime to = LocalDateTime.now(clock);
        LocalDateTime from = periodStart(period, to.toLocalDate());
        List<UUID> memberIds = clubService.memberIds(clubId);

        Map<UUID, ActivityClient.ActivityStats> statsByUser = Map.of();
        if (!memberIds.isEmpty()) {
            try {
                statsByUser = activityClient.stats(new ActivityClient.StatsRequest(memberIds, from, to)).stream()
                        .collect(Collectors.toMap(ActivityClient.ActivityStats::userId, Function.identity(), (a, b) -> a));
            } catch (RuntimeException e) {
                log.warn("Activity stats unavailable for club {}: {}", clubId, e.getMessage());
                throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "Activity data is temporarily unavailable");
            }
        }
        Map<UUID, String> names = clubService.namesOf(memberIds);

        List<ActivityClient.ActivityStats> rows = new ArrayList<>();
        for (UUID memberId : memberIds) {
            rows.add(statsByUser.getOrDefault(memberId, new ActivityClient.ActivityStats(memberId, 0, 0, 0)));
        }
        Comparator<ActivityClient.ActivityStats> byMetric = Comparator.comparingDouble(s -> value(s, metric));
        rows.sort(byMetric.reversed().thenComparing(s -> s.userId().toString()));

        List<LeaderboardResponse.Entry> entries = new ArrayList<>();
        int rank = 0;
        double previous = Double.NaN;
        for (int i = 0; i < rows.size(); i++) {
            ActivityClient.ActivityStats s = rows.get(i);
            double current = value(s, metric);
            if (i == 0 || Double.compare(current, previous) != 0) {
                rank = i + 1;
            }
            previous = current;
            entries.add(new LeaderboardResponse.Entry(rank, s.userId(), names.get(s.userId()),
                    s.totalDistanceKm(), s.totalDurationSeconds(), s.sessions()));
        }
        return new LeaderboardResponse(clubId, metric, period, from, to, entries);
    }

    static LocalDateTime periodStart(Period period, LocalDate today) {
        return switch (period) {
            case WEEK -> today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)).atStartOfDay();
            case MONTH -> today.withDayOfMonth(1).atStartOfDay();
            case YEAR -> today.withDayOfYear(1).atStartOfDay();
            case ALL -> null;
        };
    }

    private static double value(ActivityClient.ActivityStats stats, Metric metric) {
        return switch (metric) {
            case DISTANCE -> stats.totalDistanceKm();
            case TIME -> stats.totalDurationSeconds();
            case SESSIONS -> stats.sessions();
        };
    }
}

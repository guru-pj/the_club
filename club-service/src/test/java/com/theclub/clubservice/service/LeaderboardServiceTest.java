package com.theclub.clubservice.service;

import com.theclub.clubservice.client.ActivityClient;
import com.theclub.clubservice.dto.LeaderboardResponse;
import com.theclub.clubservice.model.Club;
import com.theclub.common.web.ApiException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.http.HttpStatus;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class LeaderboardServiceTest {

    private static final ZoneId ZONE = ZoneId.of("Asia/Kolkata");
    // Saturday 2026-10-17 10:00 IST
    private final Clock clock = Clock.fixed(LocalDateTime.of(2026, 10, 17, 10, 0).atZone(ZONE).toInstant(), ZONE);

    @Mock
    ClubService clubService;
    @Mock
    ActivityClient activityClient;
    LeaderboardService leaderboardService;

    private final Club club = new Club("C", null, null, null, null, UUID.randomUUID());
    private final UUID a = UUID.fromString("00000000-0000-0000-0000-00000000000a");
    private final UUID b = UUID.fromString("00000000-0000-0000-0000-00000000000b");
    private final UUID c = UUID.fromString("00000000-0000-0000-0000-00000000000c");
    private final UUID d = UUID.fromString("00000000-0000-0000-0000-00000000000d");

    @BeforeEach
    void setUp() {
        leaderboardService = new LeaderboardService(clubService, activityClient, clock);
        when(clubService.get(club.getId())).thenReturn(club);
        when(clubService.memberIds(club.getId())).thenReturn(List.of(a, b, c, d));
        when(clubService.namesOf(any())).thenReturn(Map.of(a, "Asha", b, "Bala"));
    }

    @Test
    void ranksWithTiesAndZeroActivityMembersLast() {
        when(activityClient.stats(any())).thenReturn(List.of(
                new ActivityClient.ActivityStats(a, 10, 3600, 2),
                new ActivityClient.ActivityStats(b, 20, 1800, 1),
                new ActivityClient.ActivityStats(c, 10, 7200, 3)));

        LeaderboardResponse board = leaderboardService.leaderboard(club.getId(), LeaderboardService.Metric.DISTANCE,
                LeaderboardService.Period.MONTH);

        assertThat(board.entries()).extracting(LeaderboardResponse.Entry::userId).containsExactly(b, a, c, d);
        assertThat(board.entries()).extracting(LeaderboardResponse.Entry::rank).containsExactly(1, 2, 2, 4);
        assertThat(board.entries().get(3).sessions()).isZero();
        assertThat(board.entries().get(0).name()).isEqualTo("Bala");
        assertThat(board.entries().get(3).name()).isNull();
    }

    @Test
    void ranksBySessionsAndTime() {
        when(activityClient.stats(any())).thenReturn(List.of(
                new ActivityClient.ActivityStats(a, 10, 3600, 2),
                new ActivityClient.ActivityStats(b, 20, 1800, 1),
                new ActivityClient.ActivityStats(c, 10, 7200, 3)));

        assertThat(leaderboardService.leaderboard(club.getId(), LeaderboardService.Metric.SESSIONS, LeaderboardService.Period.ALL)
                .entries()).extracting(LeaderboardResponse.Entry::userId).containsExactly(c, a, b, d);
        assertThat(leaderboardService.leaderboard(club.getId(), LeaderboardService.Metric.TIME, LeaderboardService.Period.ALL)
                .entries()).extracting(LeaderboardResponse.Entry::userId).containsExactly(c, a, b, d);
    }

    @Test
    void requestsStatsForThePeriod() {
        when(activityClient.stats(any())).thenReturn(List.of());

        LeaderboardResponse board = leaderboardService.leaderboard(club.getId(), LeaderboardService.Metric.DISTANCE,
                LeaderboardService.Period.WEEK);

        ArgumentCaptor<ActivityClient.StatsRequest> request = ArgumentCaptor.forClass(ActivityClient.StatsRequest.class);
        verify(activityClient).stats(request.capture());
        assertThat(request.getValue().from()).isEqualTo(LocalDateTime.of(2026, 10, 12, 0, 0));
        assertThat(request.getValue().to()).isEqualTo(LocalDateTime.of(2026, 10, 17, 10, 0));
        assertThat(request.getValue().userIds()).containsExactly(a, b, c, d);
        assertThat(board.from()).isEqualTo(LocalDateTime.of(2026, 10, 12, 0, 0));
    }

    @Test
    void periodBoundaries() {
        LocalDate monday = LocalDate.of(2026, 10, 12);
        assertThat(LeaderboardService.periodStart(LeaderboardService.Period.WEEK, monday)).isEqualTo(monday.atStartOfDay());
        assertThat(LeaderboardService.periodStart(LeaderboardService.Period.WEEK, LocalDate.of(2026, 10, 18)))
                .isEqualTo(monday.atStartOfDay());
        assertThat(LeaderboardService.periodStart(LeaderboardService.Period.MONTH, LocalDate.of(2026, 10, 18)))
                .isEqualTo(LocalDateTime.of(2026, 10, 1, 0, 0));
        assertThat(LeaderboardService.periodStart(LeaderboardService.Period.YEAR, LocalDate.of(2026, 10, 18)))
                .isEqualTo(LocalDateTime.of(2026, 1, 1, 0, 0));
        assertThat(LeaderboardService.periodStart(LeaderboardService.Period.ALL, LocalDate.of(2026, 10, 18))).isNull();
    }

    @Test
    void activityServiceFailureIsServiceUnavailable() {
        when(activityClient.stats(any())).thenThrow(new RuntimeException("down"));

        assertThatThrownBy(() -> leaderboardService.leaderboard(club.getId(), LeaderboardService.Metric.DISTANCE,
                LeaderboardService.Period.MONTH))
                .isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.getStatus()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE));
    }
}

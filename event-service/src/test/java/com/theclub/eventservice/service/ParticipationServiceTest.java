package com.theclub.eventservice.service;

import com.theclub.common.web.ApiException;
import com.theclub.eventservice.config.PointsProperties;
import com.theclub.eventservice.dto.MonthlyLeaderboardResponse;
import com.theclub.eventservice.dto.ParticipationResponse;
import com.theclub.eventservice.repository.ClubPointsAggregate;
import com.theclub.eventservice.repository.PointsLedgerRepository;
import com.theclub.eventservice.repository.PointsTotals;
import com.theclub.eventservice.repository.UserPointsAggregate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static com.theclub.eventservice.service.TestData.CLOCK;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ParticipationServiceTest {

    @Mock
    PointsLedgerRepository ledger;
    @Mock
    NameResolver names;

    ParticipationService service;

    @BeforeEach
    void setUp() {
        service = new ParticipationService(ledger, names, new PointsProperties(null), CLOCK);
        when(names.userNames(anyCollection())).thenReturn(Map.of());
        when(names.clubNames(anyCollection())).thenReturn(Map.of());
    }

    @Test
    void starsAreEarnedPerFiveEvents() {
        UUID userId = UUID.randomUUID();
        when(ledger.totalsForUser(userId)).thenReturn(new PointsTotals(11L, 110L));

        ParticipationResponse response = service.forUser(userId, null);

        assertThat(response.eventsAttended()).isEqualTo(11);
        assertThat(response.totalPoints()).isEqualTo(110);
        assertThat(response.stars()).isEqualTo(2);
        assertThat(service.stars(4)).isZero();
        assertThat(service.stars(5)).isEqualTo(1);
    }

    @Test
    void clubScopedStatsUseClubQuery() {
        UUID userId = UUID.randomUUID();
        UUID clubId = UUID.randomUUID();
        when(ledger.totalsForUserInClub(userId, clubId)).thenReturn(new PointsTotals(0L, 0L));

        assertThat(service.forUser(userId, clubId).clubId()).isEqualTo(clubId);
    }

    @Test
    void competitionRankingSharesRanksOnTies() {
        assertThat(ParticipationService.ranks(List.of(50L, 30L, 30L, 10L), Long::longValue)).containsExactly(1, 2, 2, 4);
        assertThat(ParticipationService.ranks(List.<Long>of(), Long::longValue)).isEmpty();
    }

    @Test
    void monthlyLeaderboardCoversWholeMonth() {
        UUID a = UUID.randomUUID();
        UUID b = UUID.randomUUID();
        UUID club = UUID.randomUUID();
        when(ledger.topUsers(eq(LocalDateTime.of(2026, 2, 1, 0, 0)), eq(LocalDateTime.of(2026, 3, 1, 0, 0)), any()))
                .thenReturn(List.of(new UserPointsAggregate(a, 20L, 2L), new UserPointsAggregate(b, 20L, 1L)));
        when(ledger.topClubs(any(), any(), any())).thenReturn(List.of(new ClubPointsAggregate(club, 40L, 3L)));
        when(names.userNames(anyCollection())).thenReturn(Map.of(a, "Asha"));

        MonthlyLeaderboardResponse response = service.monthly("2026-02", 10);

        assertThat(response.month()).isEqualTo("2026-02");
        assertThat(response.topIndividuals()).extracting(MonthlyLeaderboardResponse.IndividualEntry::rank).containsExactly(1, 1);
        assertThat(response.topIndividuals().get(0).name()).isEqualTo("Asha");
        assertThat(response.topIndividuals().get(1).name()).isNull();
        assertThat(response.topClubs().get(0).participations()).isEqualTo(3);
    }

    @Test
    void monthDefaultsToCurrentMonthAndValidatesFormat() {
        when(ledger.topUsers(any(), any(), any())).thenReturn(List.of());
        when(ledger.topClubs(any(), any(), any())).thenReturn(List.of());

        assertThat(service.monthly(null, 500).month()).isEqualTo("2026-10");
        verify(ledger).topUsers(eq(LocalDateTime.of(2026, 10, 1, 0, 0)), eq(LocalDateTime.of(2026, 11, 1, 0, 0)), any());
        assertThatThrownBy(() -> service.monthly("October", 10)).isInstanceOf(ApiException.class);
    }
}

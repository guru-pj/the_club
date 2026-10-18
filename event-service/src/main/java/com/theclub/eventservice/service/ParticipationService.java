package com.theclub.eventservice.service;

import com.theclub.common.web.ApiException;
import com.theclub.eventservice.config.PointsProperties;
import com.theclub.eventservice.dto.MemberParticipation;
import com.theclub.eventservice.dto.MonthlyLeaderboardResponse;
import com.theclub.eventservice.dto.MonthlyLeaderboardResponse.ClubEntry;
import com.theclub.eventservice.dto.MonthlyLeaderboardResponse.IndividualEntry;
import com.theclub.eventservice.dto.ParticipationResponse;
import com.theclub.eventservice.repository.ClubPointsAggregate;
import com.theclub.eventservice.repository.PointsLedgerRepository;
import com.theclub.eventservice.repository.PointsTotals;
import com.theclub.eventservice.repository.UserPointsAggregate;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.ToLongFunction;

/**
 * Participation counts, points/stars and the monthly leaderboard, all derived from the points ledger.
 */
@Service
public class ParticipationService {

    static final int MAX_LIMIT = 100;

    private final PointsLedgerRepository ledger;
    private final NameResolver names;
    private final PointsProperties properties;
    private final Clock clock;

    public ParticipationService(PointsLedgerRepository ledger, NameResolver names, PointsProperties properties,
                                Clock clock) {
        this.ledger = ledger;
        this.names = names;
        this.properties = properties;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public ParticipationResponse forUser(UUID userId, UUID clubId) {
        PointsTotals totals = clubId == null ? ledger.totalsForUser(userId) : ledger.totalsForUserInClub(userId, clubId);
        long events = totals.events();
        return new ParticipationResponse(userId, clubId, events, totals.points(), stars(events));
    }

    @Transactional(readOnly = true)
    public List<MemberParticipation> forClub(UUID clubId) {
        List<UserPointsAggregate> rows = ledger.participationByClub(clubId);
        Map<UUID, String> userNames = names.userNames(rows.stream().map(UserPointsAggregate::userId).toList());
        return rows.stream()
                .map(r -> new MemberParticipation(r.userId(), userNames.get(r.userId()), r.events(), r.points(),
                        stars(r.events())))
                .toList();
    }

    @Transactional(readOnly = true)
    public MonthlyLeaderboardResponse monthly(String month, int limit) {
        YearMonth yearMonth = parseMonth(month);
        int size = Math.clamp(limit, 1, MAX_LIMIT);
        LocalDateTime from = yearMonth.atDay(1).atStartOfDay();
        LocalDateTime to = yearMonth.plusMonths(1).atDay(1).atStartOfDay();
        PageRequest page = PageRequest.of(0, size);

        List<UserPointsAggregate> users = ledger.topUsers(from, to, page);
        List<ClubPointsAggregate> clubs = ledger.topClubs(from, to, page);
        Map<UUID, String> userNames = names.userNames(users.stream().map(UserPointsAggregate::userId).toList());
        Map<UUID, String> clubNames = names.clubNames(clubs.stream().map(ClubPointsAggregate::clubId).toList());

        List<Integer> userRanks = ranks(users, UserPointsAggregate::points);
        List<IndividualEntry> topIndividuals = new ArrayList<>();
        for (int i = 0; i < users.size(); i++) {
            UserPointsAggregate u = users.get(i);
            topIndividuals.add(new IndividualEntry(userRanks.get(i), u.userId(), userNames.get(u.userId()),
                    u.points(), u.events()));
        }
        List<Integer> clubRanks = ranks(clubs, ClubPointsAggregate::points);
        List<ClubEntry> topClubs = new ArrayList<>();
        for (int i = 0; i < clubs.size(); i++) {
            ClubPointsAggregate c = clubs.get(i);
            topClubs.add(new ClubEntry(clubRanks.get(i), c.clubId(), clubNames.get(c.clubId()), c.points(),
                    c.participations()));
        }
        return new MonthlyLeaderboardResponse(yearMonth.toString(), topIndividuals, topClubs);
    }

    long stars(long eventsAttended) {
        return eventsAttended / properties.eventsPerStar();
    }

    /**
     * Standard competition ranking ("1224") over rows already sorted by score descending.
     */
    static <T> List<Integer> ranks(List<T> sorted, ToLongFunction<T> score) {
        List<Integer> ranks = new ArrayList<>(sorted.size());
        for (int i = 0; i < sorted.size(); i++) {
            boolean tiedWithPrevious = i > 0 && score.applyAsLong(sorted.get(i)) == score.applyAsLong(sorted.get(i - 1));
            ranks.add(tiedWithPrevious ? ranks.get(i - 1) : i + 1);
        }
        return ranks;
    }

    private YearMonth parseMonth(String month) {
        if (month == null || month.isBlank()) {
            return YearMonth.now(clock);
        }
        try {
            return YearMonth.parse(month.trim());
        } catch (DateTimeParseException e) {
            throw ApiException.badRequest("month must be formatted as YYYY-MM");
        }
    }
}

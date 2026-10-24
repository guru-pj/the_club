package com.theclub.eventservice.service;

import com.theclub.common.security.AuthenticatedUser;
import com.theclub.eventservice.client.ClubClient;
import com.theclub.eventservice.client.ClubClient.ClubSummary;
import com.theclub.eventservice.client.ClubClient.NearbyClub;
import com.theclub.eventservice.client.ClubClient.RelatedClubs;
import com.theclub.eventservice.client.IdsRequest;
import com.theclub.eventservice.dto.EventCard;
import com.theclub.eventservice.model.Event;
import com.theclub.eventservice.model.EventStatus;
import com.theclub.eventservice.repository.EventRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/** Upcoming events from clubs the user follows or subscribes to, and from nearby clubs (user journey step 6). */
@Service
public class FeedService {

    public static final String FOLLOWING = "FOLLOWING";
    public static final String SUBSCRIBED = "SUBSCRIBED";
    public static final String NEARBY = "NEARBY";
    static final double DEFAULT_RADIUS_KM = 25;
    static final int MAX_LIMIT = 50;

    private final EventRepository events;
    private final EventService eventService;
    private final ClubClient clubClient;
    private final Clock clock;

    public FeedService(EventRepository events, EventService eventService, ClubClient clubClient, Clock clock) {
        this.events = events;
        this.eventService = eventService;
        this.clubClient = clubClient;
        this.clock = clock;
    }

    /**
     * @param lat  with lng, also includes events of clubs within {@code radiusKm} of the point
     * @param city used for "nearby" instead when there are no coordinates: clubs whose city matches
     */
    @Transactional(readOnly = true)
    public List<EventCard> upcoming(AuthenticatedUser caller, Double lat, Double lng, Double radiusKm, String city,
                                    Integer limit) {
        int max = limit == null ? 20 : Math.clamp(limit, 1, MAX_LIMIT);
        Map<UUID, Set<String>> sources = new LinkedHashMap<>();
        Map<UUID, Double> distance = new LinkedHashMap<>();
        Map<UUID, ClubSummary> clubs = new LinkedHashMap<>();

        RelatedClubs related = clubClient.related(caller.id());
        related.subscribed().forEach(id -> sources.computeIfAbsent(id, k -> new LinkedHashSet<>()).add(SUBSCRIBED));
        related.followed().forEach(id -> sources.computeIfAbsent(id, k -> new LinkedHashSet<>()).add(FOLLOWING));
        if (lat != null && lng != null) {
            double radius = radiusKm == null ? DEFAULT_RADIUS_KM : radiusKm;
            for (NearbyClub club : clubClient.nearby(lat, lng, radius, MAX_LIMIT)) {
                sources.computeIfAbsent(club.id(), k -> new LinkedHashSet<>()).add(NEARBY);
                distance.put(club.id(), club.distanceKm());
                clubs.put(club.id(), new ClubSummary(club.id(), club.name(), club.logoUrl()));
            }
        } else if (city != null && !city.isBlank()) {
            for (ClubSummary club : clubClient.inCity(city.trim(), MAX_LIMIT)) {
                sources.computeIfAbsent(club.id(), k -> new LinkedHashSet<>()).add(NEARBY);
                clubs.put(club.id(), club);
            }
        }
        if (sources.isEmpty()) {
            return List.of();
        }

        List<Event> upcoming = events.findByClubIdInAndStatusAndStartTimeAfterOrderByStartTimeAsc(
                sources.keySet(), EventStatus.SCHEDULED, LocalDateTime.now(clock), PageRequest.of(0, max));
        List<UUID> unnamed = upcoming.stream().map(Event::getClubId).distinct().filter(id -> !clubs.containsKey(id)).toList();
        if (!unnamed.isEmpty()) {
            clubs.putAll(clubClient.summaries(new IdsRequest(unnamed)).stream()
                    .collect(Collectors.toMap(ClubSummary::id, Function.identity())));
        }
        return upcoming.stream().map(event -> {
            ClubSummary club = clubs.get(event.getClubId());
            return new EventCard(eventService.view(event, caller.id()), club == null ? null : club.name(),
                    club == null ? null : club.logoUrl(), sources.get(event.getClubId()), distance.get(event.getClubId()));
        }).toList();
    }
}

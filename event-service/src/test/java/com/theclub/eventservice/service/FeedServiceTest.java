package com.theclub.eventservice.service;

import com.theclub.common.security.AuthenticatedUser;
import com.theclub.eventservice.client.ClubClient;
import com.theclub.eventservice.client.ClubClient.ClubSummary;
import com.theclub.eventservice.client.ClubClient.RelatedClubs;
import com.theclub.eventservice.dto.EventCard;
import com.theclub.eventservice.model.Event;
import com.theclub.eventservice.model.EventStatus;
import com.theclub.eventservice.repository.EventRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static com.theclub.eventservice.service.TestData.CLOCK;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FeedServiceTest {

    @Mock
    EventRepository events;
    @Mock
    EventService eventService;
    @Mock
    ClubClient clubClient;

    private FeedService feed;
    private final AuthenticatedUser caller = new AuthenticatedUser(UUID.randomUUID(), "u@example.com", "U", Set.of("USER"));
    private final UUID followed = UUID.randomUUID();
    private final UUID inCity = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        feed = new FeedService(events, eventService, clubClient, CLOCK);
        when(clubClient.related(caller.id())).thenReturn(new RelatedClubs(List.of(followed), List.of()));
    }

    @Test
    @SuppressWarnings("unchecked")
    void withoutCoordinatesNearbyFallsBackToClubsInTheUsersCity() {
        when(clubClient.inCity("Chennai", FeedService.MAX_LIMIT))
                .thenReturn(List.of(new ClubSummary(inCity, "Velachery Riders", null)));
        Event event = new Event(inCity, UUID.randomUUID());
        when(events.findByClubIdInAndStatusAndStartTimeAfterOrderByStartTimeAsc(any(), eq(EventStatus.SCHEDULED), any(), any()))
                .thenReturn(List.of(event));

        List<EventCard> cards = feed.upcoming(caller, null, null, null, "  Chennai ", null);

        ArgumentCaptor<Collection<UUID>> clubIds = ArgumentCaptor.forClass(Collection.class);
        verify(events).findByClubIdInAndStatusAndStartTimeAfterOrderByStartTimeAsc(clubIds.capture(), any(), any(), any());
        assertThat(clubIds.getValue()).containsExactlyInAnyOrder(followed, inCity);
        assertThat(cards).singleElement().satisfies(card -> {
            assertThat(card.clubName()).isEqualTo("Velachery Riders");
            assertThat(card.sources()).containsExactly(FeedService.NEARBY);
        });
        verify(clubClient, never()).nearby(anyDouble(), anyDouble(), anyDouble(), anyInt());
        verify(clubClient, never()).summaries(any());
    }

    @Test
    void coordinatesWinOverTheCity() {
        when(clubClient.nearby(13.0, 80.2, 25, FeedService.MAX_LIMIT)).thenReturn(List.of());
        when(events.findByClubIdInAndStatusAndStartTimeAfterOrderByStartTimeAsc(any(), any(), any(), any())).thenReturn(List.of());

        feed.upcoming(caller, 13.0, 80.2, null, "Chennai", null);

        verify(clubClient, never()).inCity(any(), anyInt());
    }
}

package com.theclub.activityservice.strava;

import com.theclub.activityservice.model.Activity;
import com.theclub.activityservice.model.ActivitySource;
import com.theclub.activityservice.model.ActivityType;
import com.theclub.activityservice.model.StravaConnection;
import com.theclub.activityservice.repository.ActivityRepository;
import com.theclub.activityservice.repository.StravaConnectionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.HttpClientErrorException;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StravaWebhookProcessorTest {

    private static final long ATHLETE = 42L;
    private static final long STRAVA_ID = 9001L;

    @Mock
    StravaConnectionRepository connections;
    @Mock
    StravaTokenManager tokens;
    @Mock
    StravaClient client;
    @Mock
    ActivityRepository activities;

    StravaWebhookProcessor processor;
    StravaConnection connection;
    final UUID userId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        processor = new StravaWebhookProcessor(connections, tokens, client, new StravaActivityImporter(activities));
        connection = new StravaConnection(userId, ATHLETE);
        connection.setRefreshToken("refresh");
    }

    private static StravaWebhookEvent event(String objectType, String aspect, Map<String, Object> updates) {
        return new StravaWebhookEvent(objectType, "athlete".equals(objectType) ? ATHLETE : STRAVA_ID, aspect, ATHLETE,
                updates, 0, 1L);
    }

    private static StravaActivity stravaActivity(String name, long ownerAthlete) {
        return new StravaActivity(STRAVA_ID, name, "Run", "TrailRun", 10500.0, 3600, 3700, 812.4,
                "2026-10-18T06:00:00Z", new StravaTokenResponse.Athlete(ownerAthlete));
    }

    @Test
    void createImportsActivityIntoMembersAccount() {
        when(connections.findByAthleteId(ATHLETE)).thenReturn(Optional.of(connection));
        when(tokens.accessToken(connection)).thenReturn("token");
        when(client.getActivity("token", STRAVA_ID)).thenReturn(stravaActivity("Trail run", ATHLETE));
        when(activities.findBySourceAndExternalId(ActivitySource.STRAVA, "9001")).thenReturn(Optional.empty());

        processor.process(event("activity", "create", null));

        ArgumentCaptor<Activity> saved = ArgumentCaptor.forClass(Activity.class);
        verify(activities).save(saved.capture());
        Activity activity = saved.getValue();
        assertThat(activity.getUserId()).isEqualTo(userId);
        assertThat(activity.getSource()).isEqualTo(ActivitySource.STRAVA);
        assertThat(activity.getExternalId()).isEqualTo("9001");
        assertThat(activity.getType()).isEqualTo(ActivityType.RUN);
        assertThat(activity.getDistance()).isEqualTo(10.5);
        assertThat(activity.getDuration()).isEqualTo(3600);
        assertThat(activity.getCalories()).isEqualTo(812);
        assertThat(activity.getStartTime()).isEqualTo(LocalDateTime.of(2026, 10, 18, 6, 0));
        assertThat(activity.getEndTime()).isEqualTo(LocalDateTime.of(2026, 10, 18, 7, 1, 40));
    }

    @Test
    void updateIsIdempotentOnStravaId() {
        Activity existing = new Activity(userId, ActivitySource.STRAVA);
        existing.setExternalId("9001");
        when(connections.findByAthleteId(ATHLETE)).thenReturn(Optional.of(connection));
        when(tokens.accessToken(connection)).thenReturn("token");
        when(client.getActivity("token", STRAVA_ID)).thenReturn(stravaActivity("Renamed run", ATHLETE));
        when(activities.findBySourceAndExternalId(ActivitySource.STRAVA, "9001")).thenReturn(Optional.of(existing));

        processor.process(event("activity", "update", Map.of("title", "Renamed run")));
        processor.process(event("activity", "update", Map.of("title", "Renamed run")));

        verify(activities, times(2)).save(existing);
        assertThat(existing.getTitle()).isEqualTo("Renamed run");
        assertThat(existing.getId()).isNotNull();
    }

    @Test
    void ignoresActivitiesNotOwnedByTheAthlete() {
        when(connections.findByAthleteId(ATHLETE)).thenReturn(Optional.of(connection));
        when(tokens.accessToken(connection)).thenReturn("token");
        when(client.getActivity("token", STRAVA_ID)).thenReturn(stravaActivity("Someone else", 7L));

        processor.process(event("activity", "create", null));

        verify(activities, never()).save(any());
    }

    @Test
    void ignoresUnknownAthletes() {
        when(connections.findByAthleteId(ATHLETE)).thenReturn(Optional.empty());

        processor.process(event("activity", "create", null));

        verifyNoInteractions(client, activities);
    }

    @Test
    void deleteRemovesActivityOnceStravaConfirmsItIsGone() {
        Activity existing = new Activity(userId, ActivitySource.STRAVA);
        when(connections.findByAthleteId(ATHLETE)).thenReturn(Optional.of(connection));
        when(tokens.accessToken(connection)).thenReturn("token");
        when(client.getActivity("token", STRAVA_ID)).thenThrow(
                HttpClientErrorException.create(HttpStatus.NOT_FOUND, "Not Found", null, null, null));
        when(activities.findBySourceAndExternalId(ActivitySource.STRAVA, "9001")).thenReturn(Optional.of(existing));

        processor.process(event("activity", "delete", null));

        verify(activities).delete(existing);
    }

    @Test
    void spoofedDeleteIsIgnoredWhenActivityStillExists() {
        when(connections.findByAthleteId(ATHLETE)).thenReturn(Optional.of(connection));
        when(tokens.accessToken(connection)).thenReturn("token");
        when(client.getActivity("token", STRAVA_ID)).thenReturn(stravaActivity("Still here", ATHLETE));

        processor.process(event("activity", "delete", null));

        verify(activities, never()).delete(any());
    }

    @Test
    void deauthorizationRemovesConnectionWhenRefreshIsRejected() {
        when(connections.findByAthleteId(ATHLETE)).thenReturn(Optional.of(connection));
        when(client.refresh("refresh")).thenThrow(
                HttpClientErrorException.create(HttpStatus.BAD_REQUEST, "Bad Request", null, null, null));

        processor.process(event("athlete", "update", Map.of("authorized", "false")));

        verify(connections).delete(connection);
    }

    @Test
    void spoofedDeauthorizationKeepsConnection() {
        when(connections.findByAthleteId(ATHLETE)).thenReturn(Optional.of(connection));
        when(client.refresh("refresh")).thenReturn(new StravaTokenResponse("new-access", "new-refresh",
                Instant.parse("2026-10-18T12:00:00Z").getEpochSecond(), null));

        processor.process(event("athlete", "update", Map.of("authorized", "false")));

        verify(connections, never()).delete(any());
        assertThat(connection.getRefreshToken()).isEqualTo("new-refresh");
        verify(client, never()).getActivity(any(), anyLong());
    }
}

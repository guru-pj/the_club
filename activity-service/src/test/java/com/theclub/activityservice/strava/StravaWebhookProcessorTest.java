package com.theclub.activityservice.strava;

import com.theclub.activityservice.model.Activity;
import com.theclub.activityservice.model.ActivitySource;
import com.theclub.activityservice.model.ActivityType;
import com.theclub.activityservice.model.StravaConnection;
import com.theclub.activityservice.model.StravaConnectionStatus;
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
import java.util.function.Function;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
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
        StravaProperties properties = new StravaProperties("123", "secret", null, "verify", null, null, null,
                null, null, null, null);
        processor = new StravaWebhookProcessor(connections, tokens, client, new StravaActivityImporter(activities),
                properties);
        connection = new StravaConnection(userId, ATHLETE);
        // Run the call the processor hands to the token manager with a fixed token
        lenient().when(tokens.withAccessToken(eq(connection), any())).thenAnswer(invocation -> {
            Function<String, Object> call = invocation.getArgument(1);
            return call.apply("token");
        });
    }

    private static StravaWebhookEvent event(String objectType, String aspect, Map<String, Object> updates) {
        return new StravaWebhookEvent(objectType, "athlete".equals(objectType) ? ATHLETE : STRAVA_ID, aspect, ATHLETE,
                updates, 0, 1L);
    }

    @Test
    void createImportsActivityIntoMembersAccount() {
        when(connections.findByAthleteId(ATHLETE)).thenReturn(Optional.of(connection));
        when(client.getActivity("token", STRAVA_ID)).thenReturn(StravaTestData.activity(STRAVA_ID, "Trail run", ATHLETE));
        when(activities.findBySourceAndExternalId(ActivitySource.STRAVA, "9001")).thenReturn(Optional.empty());

        assertThat(processor.process(event("activity", "create", null))).isEqualTo(StravaWebhookProcessor.Outcome.DONE);

        ArgumentCaptor<Activity> saved = ArgumentCaptor.forClass(Activity.class);
        verify(activities).save(saved.capture());
        Activity activity = saved.getValue();
        assertThat(activity.getUserId()).isEqualTo(userId);
        assertThat(activity.getSource()).isEqualTo(ActivitySource.STRAVA);
        assertThat(activity.getExternalId()).isEqualTo("9001");
        assertThat(activity.getType()).isEqualTo(ActivityType.RUN);
        assertThat(activity.getDistance()).isEqualTo(10.5);
        assertThat(activity.getDuration()).isEqualTo(3600);
        assertThat(activity.getElapsedTime()).isEqualTo(3700);
        assertThat(activity.getCalories()).isEqualTo(812);
        assertThat(activity.getTotalElevationGain()).isEqualTo(120.5);
        assertThat(activity.getAverageHeartrate()).isEqualTo(152.3);
        assertThat(activity.getDeviceName()).isEqualTo("Garmin Forerunner 265");
        assertThat(activity.isPrivateActivity()).isFalse();
        assertThat(activity.getStartTime()).isEqualTo(LocalDateTime.of(2026, 10, 18, 6, 0));
        assertThat(activity.getStartTimeUtc()).isEqualTo(Instant.parse("2026-10-18T00:30:00Z"));
        assertThat(activity.getTimeZone()).isEqualTo("Asia/Kolkata");
        assertThat(activity.getEndTime()).isEqualTo(LocalDateTime.of(2026, 10, 18, 7, 1, 40));
    }

    @Test
    void privateActivitiesAreImportedAndFlagged() {
        when(connections.findByAthleteId(ATHLETE)).thenReturn(Optional.of(connection));
        when(client.getActivity("token", STRAVA_ID)).thenReturn(StravaTestData.activity(STRAVA_ID, "Secret", ATHLETE, true));
        when(activities.findBySourceAndExternalId(ActivitySource.STRAVA, "9001")).thenReturn(Optional.empty());

        processor.process(event("activity", "create", null));

        ArgumentCaptor<Activity> saved = ArgumentCaptor.forClass(Activity.class);
        verify(activities).save(saved.capture());
        assertThat(saved.getValue().isPrivateActivity()).isTrue();
    }

    @Test
    void updateIsIdempotentOnStravaId() {
        Activity existing = new Activity(userId, ActivitySource.STRAVA);
        existing.setExternalId("9001");
        when(connections.findByAthleteId(ATHLETE)).thenReturn(Optional.of(connection));
        when(client.getActivity("token", STRAVA_ID)).thenReturn(StravaTestData.activity(STRAVA_ID, "Renamed run", ATHLETE));
        when(activities.findBySourceAndExternalId(ActivitySource.STRAVA, "9001")).thenReturn(Optional.of(existing));

        processor.process(event("activity", "update", Map.of("title", "Renamed run")));
        processor.process(event("activity", "update", Map.of("title", "Renamed run")));

        verify(activities, times(2)).save(existing);
        assertThat(existing.getTitle()).isEqualTo("Renamed run");
    }

    @Test
    void ignoresActivitiesNotOwnedByTheAthlete() {
        when(connections.findByAthleteId(ATHLETE)).thenReturn(Optional.of(connection));
        when(client.getActivity("token", STRAVA_ID)).thenReturn(StravaTestData.activity(STRAVA_ID, "Someone else", 7L));

        assertThat(processor.process(event("activity", "create", null))).isEqualTo(StravaWebhookProcessor.Outcome.IGNORED);

        verify(activities, never()).save(any());
    }

    @Test
    void ignoresUnknownAndRevokedAthletes() {
        when(connections.findByAthleteId(ATHLETE)).thenReturn(Optional.empty());
        assertThat(processor.process(event("activity", "create", null))).isEqualTo(StravaWebhookProcessor.Outcome.IGNORED);

        connection.setStatus(StravaConnectionStatus.REVOKED);
        when(connections.findByAthleteId(ATHLETE)).thenReturn(Optional.of(connection));
        assertThat(processor.process(event("activity", "create", null))).isEqualTo(StravaWebhookProcessor.Outcome.IGNORED);

        verifyNoInteractions(client, activities);
    }

    @Test
    void deleteRemovesActivityOnceStravaConfirmsItIsGone() {
        Activity existing = new Activity(userId, ActivitySource.STRAVA);
        when(connections.findByAthleteId(ATHLETE)).thenReturn(Optional.of(connection));
        when(client.getActivity("token", STRAVA_ID)).thenThrow(
                HttpClientErrorException.create(HttpStatus.NOT_FOUND, "Not Found", null, null, null));
        when(activities.findBySourceAndExternalId(ActivitySource.STRAVA, "9001")).thenReturn(Optional.of(existing));

        processor.process(event("activity", "delete", null));

        verify(activities).delete(existing);
    }

    @Test
    void spoofedDeleteIsIgnoredWhenActivityStillExists() {
        when(connections.findByAthleteId(ATHLETE)).thenReturn(Optional.of(connection));
        when(client.getActivity("token", STRAVA_ID)).thenReturn(StravaTestData.activity(STRAVA_ID, "Still here", ATHLETE));

        processor.process(event("activity", "delete", null));

        verify(activities, never()).delete(any());
    }

    @Test
    void deauthorizationIsConfirmedByRefreshing() {
        when(connections.findByAthleteId(ATHLETE)).thenReturn(Optional.of(connection));
        doThrow(new StravaAccessRevokedException(userId)).when(tokens).verifyAuthorization(connection.getId());

        assertThat(processor.process(event("athlete", "update", Map.of("authorized", "false"))))
                .isEqualTo(StravaWebhookProcessor.Outcome.DONE);

        verify(tokens).verifyAuthorization(connection.getId());
        verifyNoInteractions(client);
    }

    @Test
    void spoofedDeauthorizationKeepsConnection() {
        when(connections.findByAthleteId(ATHLETE)).thenReturn(Optional.of(connection));

        processor.process(event("athlete", "update", Map.of("authorized", "false")));

        verify(tokens).verifyAuthorization(connection.getId());
        verify(connections, never()).delete(any());
    }

    @Test
    void failuresPropagateSoTheWorkerRetries() {
        when(connections.findByAthleteId(ATHLETE)).thenReturn(Optional.of(connection));
        when(client.getActivity("token", STRAVA_ID)).thenThrow(new StravaRateLimitedException(Instant.EPOCH));

        assertThatThrownBy(() -> processor.process(event("activity", "create", null)))
                .isInstanceOf(StravaRateLimitedException.class);
    }

    @Test
    void revokedAccessDuringProcessingEndsTheEvent() {
        when(connections.findByAthleteId(ATHLETE)).thenReturn(Optional.of(connection));
        when(client.getActivity("token", STRAVA_ID)).thenThrow(new StravaAccessRevokedException(userId));

        assertThat(processor.process(event("activity", "create", null))).isEqualTo(StravaWebhookProcessor.Outcome.IGNORED);
    }
}

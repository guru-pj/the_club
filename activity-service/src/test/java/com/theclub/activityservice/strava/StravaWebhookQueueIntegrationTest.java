package com.theclub.activityservice.strava;

import com.theclub.activityservice.model.StravaWebhookEventRecord;
import com.theclub.activityservice.model.StravaWebhookEventRecord.Status;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.client.ResourceAccessException;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class StravaWebhookQueueIntegrationTest extends StravaIntegrationTestBase {

    static final long ATHLETE = 55L;

    @Autowired
    StravaWebhookWorker worker;
    @Autowired
    PlatformTransactionManager transactionManager;

    private void postEvent(long activityId, String aspect, long eventTime) throws Exception {
        mvc.perform(post("/api/v1/strava/webhook").contentType(MediaType.APPLICATION_JSON).content("""
                        {"object_type":"activity","object_id":%d,"aspect_type":"%s","owner_id":%d,
                         "updates":{"title":"x"},"event_time":%d,"subscription_id":1}"""
                        .formatted(activityId, aspect, ATHLETE, eventTime)))
                .andExpect(status().isOk());
    }

    private StravaWebhookEventRecord only() {
        assertThat(webhookEvents.findAll()).hasSize(1);
        return webhookEvents.findAll().get(0);
    }

    @Test
    void eventsArePersistedOnceAndProcessedByTheWorker() throws Exception {
        UUID userId = UUID.randomUUID();
        connect(userId, ATHLETE, Duration.ofHours(5));
        postEvent(900, "create", 1760780000L);
        postEvent(900, "create", 1760780000L); // Strava re-delivery

        StravaWebhookEventRecord stored = only();
        assertThat(stored.getStatus()).isEqualTo(Status.PENDING);
        assertThat(stored.getUpdates()).isEqualTo("{\"title\":\"x\"}");

        when(stravaClient.getActivity("access-0", 900)).thenReturn(StravaTestData.activity(900, "Morning run", ATHLETE));
        StravaWebhookWorker.Summary summary = worker.runOnce();

        assertThat(summary.processed()).isEqualTo(1);
        assertThat(only().getStatus()).isEqualTo(Status.DONE);
        assertThat(only().getProcessedAt()).isEqualTo(NOW);
        assertThat(activities.findAll()).singleElement()
                .satisfies(a -> assertThat(a.getUserId()).isEqualTo(userId));
        assertThat(worker.runOnce().processed()).as("nothing left to do").isZero();
    }

    @Test
    void failuresAreRetriedWithBackoffThenMarkedFailed() throws Exception {
        connect(UUID.randomUUID(), ATHLETE, Duration.ofHours(5));
        postEvent(901, "create", 1760780001L);
        when(stravaClient.getActivity(anyString(), anyLong())).thenThrow(new ResourceAccessException("timeout"));

        assertThat(worker.runOnce().retried()).isEqualTo(1);
        StravaWebhookEventRecord afterFirst = only();
        assertThat(afterFirst.getStatus()).isEqualTo(Status.PENDING);
        assertThat(afterFirst.getAttempts()).isEqualTo(1);
        assertThat(afterFirst.getNextAttemptAt()).isEqualTo(NOW.plus(Duration.ofMinutes(1)));
        assertThat(afterFirst.getLastError()).contains("timeout");

        assertThat(worker.runOnce().retried()).as("not due yet").isZero();

        Duration[] waits = {Duration.ofMinutes(1), Duration.ofMinutes(5), Duration.ofMinutes(15), Duration.ofHours(1),
                Duration.ofHours(1)};
        for (Duration wait : waits) {
            clock.advance(wait);
            worker.runOnce();
        }
        StravaWebhookEventRecord failed = only();
        assertThat(failed.getAttempts()).isEqualTo(6);
        assertThat(failed.getStatus()).isEqualTo(Status.FAILED);
    }

    @Test
    void rateLimitRequeuesWithoutUsingAnAttempt() throws Exception {
        connect(UUID.randomUUID(), ATHLETE, Duration.ofHours(5));
        postEvent(902, "update", 1760780002L);
        Instant reset = Instant.parse("2026-10-18T10:15:00Z");
        when(stravaClient.getActivity(anyString(), anyLong())).thenThrow(new StravaRateLimitedException(reset));

        assertThat(worker.runOnce().requeued()).isEqualTo(1);

        StravaWebhookEventRecord requeued = only();
        assertThat(requeued.getStatus()).isEqualTo(Status.PENDING);
        assertThat(requeued.getAttempts()).isZero();
        assertThat(requeued.getNextAttemptAt()).isEqualTo(reset);
    }

    @Test
    void eventsOfUnknownAthletesAreIgnoredWithoutCallingStrava() throws Exception {
        postEvent(903, "create", 1760780003L);

        assertThat(worker.runOnce().ignored()).isEqualTo(1);
        assertThat(only().getStatus()).isEqualTo(Status.IGNORED);
        org.mockito.Mockito.verifyNoInteractions(stravaClient);
    }

    @Test
    void anEventCanBeClaimedByOnlyOneWorkerAndAbandonedClaimsAreReclaimed() throws Exception {
        postEvent(904, "create", 1760780004L);
        UUID id = only().getId();
        TransactionTemplate tx = new TransactionTemplate(transactionManager);

        Integer first = tx.execute(s -> webhookEvents.claim(id, NOW, NOW.plus(StravaWebhookWorker.LEASE)));
        Integer second = tx.execute(s -> webhookEvents.claim(id, NOW, NOW.plus(StravaWebhookWorker.LEASE)));
        assertThat(first).isEqualTo(1);
        assertThat(second).isZero();
        assertThat(only().getStatus()).isEqualTo(Status.PROCESSING);

        // The claiming replica died: once the lease expires another worker picks it up
        clock.advance(StravaWebhookWorker.LEASE.plusSeconds(1));
        assertThat(worker.runOnce().ignored()).isEqualTo(1);
        assertThat(only().getStatus()).isEqualTo(Status.IGNORED);
    }

    @Test
    void finishedEventsArePurgedAfterThirtyDays() throws Exception {
        postEvent(905, "create", 1760780005L);
        worker.runOnce();
        assertThat(only().getStatus()).isEqualTo(Status.IGNORED);

        clock.advance(Duration.ofDays(31));
        worker.runOnce();
        assertThat(webhookEvents.findAll()).isEmpty();
    }

    @Test
    void malformedEventsAreAcknowledgedButNotStored() throws Exception {
        mvc.perform(post("/api/v1/strava/webhook").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isOk());
        assertThat(webhookEvents.findAll()).isEmpty();
    }
}

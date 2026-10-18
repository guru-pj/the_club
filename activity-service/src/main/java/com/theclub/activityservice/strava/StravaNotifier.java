package com.theclub.activityservice.strava;

import com.theclub.activityservice.client.NotificationClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.UUID;

/**
 * Best-effort in-app notifications about the Strava connection, sent after the surrounding transaction commits.
 */
@Component
public class StravaNotifier {

    static final String DISCONNECTED = "STRAVA_DISCONNECTED";
    private static final Logger log = LoggerFactory.getLogger(StravaNotifier.class);

    private final NotificationClient notifications;

    public StravaNotifier(NotificationClient notifications) {
        this.notifications = notifications;
    }

    public void disconnected(UUID userId) {
        afterCommit(() -> send(userId, DISCONNECTED,
                "Your Strava connection stopped working (access was revoked or expired). "
                        + "Reconnect Strava on the Activities page to keep importing your activities."));
    }

    private void send(UUID userId, String type, String message) {
        try {
            notifications.create(new NotificationClient.NotificationRequest(userId, type, message));
        } catch (RuntimeException e) {
            log.warn("Could not send {} notification to user {}: {}", type, userId, e.getMessage());
        }
    }

    private static void afterCommit(Runnable action) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    action.run();
                }
            });
        } else {
            action.run();
        }
    }
}

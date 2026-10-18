package com.theclub.clubservice.service;

import com.theclub.clubservice.client.NotificationClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.Collection;
import java.util.UUID;

/**
 * Best-effort in-app notifications, sent only once the surrounding transaction has committed
 * so users are never told about changes that were rolled back.
 */
@Component
public class NotificationPublisher {

    private static final Logger log = LoggerFactory.getLogger(NotificationPublisher.class);

    private final NotificationClient client;

    public NotificationPublisher(NotificationClient client) {
        this.client = client;
    }

    public void notifyAfterCommit(UUID userId, String type, String message) {
        notifyAfterCommit(java.util.List.of(userId), type, message);
    }

    public void notifyAfterCommit(Collection<UUID> userIds, String type, String message) {
        if (userIds.isEmpty()) {
            return;
        }
        var recipients = java.util.List.copyOf(userIds);
        Runnable send = () -> recipients.forEach(id -> send(id, type, message));
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    send.run();
                }
            });
        } else {
            send.run();
        }
    }

    private void send(UUID userId, String type, String message) {
        try {
            client.create(new NotificationClient.NotificationRequest(userId, type, message));
        } catch (RuntimeException e) {
            log.warn("Could not send {} notification to {}: {}", type, userId, e.getMessage());
        }
    }
}

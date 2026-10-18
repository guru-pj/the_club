package com.theclub.eventservice.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * A provider webhook delivery that has been processed; its id makes redeliveries no-ops.
 */
@Entity
@Table(name = "payment_webhook_events")
@Getter
@NoArgsConstructor
public class PaymentWebhookEvent {

    @Id
    @Column(name = "event_id", length = 100, nullable = false, updatable = false)
    private String eventId;

    @Column(name = "event_type", length = 50, nullable = false, updatable = false)
    private String eventType;

    @Column(name = "received_at", nullable = false, updatable = false)
    private LocalDateTime receivedAt;

    public PaymentWebhookEvent(String eventId, String eventType, LocalDateTime receivedAt) {
        this.eventId = eventId;
        this.eventType = eventType;
        this.receivedAt = receivedAt;
    }
}

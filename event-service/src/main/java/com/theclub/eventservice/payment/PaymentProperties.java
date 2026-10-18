package com.theclub.eventservice.payment;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * @param provider           "mock" (development only) or "razorpay"; required, there is deliberately no default.
 * @param mockSecret         HMAC key of the mock provider (checkout and webhook signatures).
 * @param pendingHold        how long an unpaid registration holds its spot before it is released.
 * @param reconcileEnabled   whether the reconciliation job runs.
 * @param reconcileInterval  delay between reconciliation runs.
 * @param reconcileBatchSize payments examined per category per run.
 */
@ConfigurationProperties(prefix = "theclub.payments")
public record PaymentProperties(String provider, String mockSecret, Razorpay razorpay, Duration pendingHold,
                                Boolean reconcileEnabled, Duration reconcileInterval, Integer reconcileBatchSize) {

    public static final Duration DEFAULT_PENDING_HOLD = Duration.ofMinutes(15);

    public PaymentProperties {
        if (mockSecret == null || mockSecret.isBlank()) {
            mockSecret = "mock-secret";
        }
        if (razorpay == null) {
            razorpay = new Razorpay(null, null, null, null);
        }
        if (pendingHold == null) {
            pendingHold = DEFAULT_PENDING_HOLD;
        }
        if (reconcileEnabled == null) {
            reconcileEnabled = true;
        }
        if (reconcileInterval == null) {
            reconcileInterval = Duration.ofMinutes(5);
        }
        if (reconcileBatchSize == null || reconcileBatchSize < 1) {
            reconcileBatchSize = 50;
        }
    }

    public record Razorpay(String keyId, String keySecret, String webhookSecret, String baseUrl) {

        public Razorpay {
            if (baseUrl == null || baseUrl.isBlank()) {
                baseUrl = "https://api.razorpay.com";
            }
        }
    }
}

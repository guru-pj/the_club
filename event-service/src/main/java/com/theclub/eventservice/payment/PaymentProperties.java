package com.theclub.eventservice.payment;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * @param provider   "mock" (default, development only) or "razorpay".
 * @param mockSecret HMAC key of the mock provider.
 */
@ConfigurationProperties(prefix = "theclub.payments")
public record PaymentProperties(String provider, String mockSecret, Razorpay razorpay) {

    public PaymentProperties {
        if (provider == null || provider.isBlank()) {
            provider = "mock";
        }
        if (mockSecret == null || mockSecret.isBlank()) {
            mockSecret = "mock-secret";
        }
        if (razorpay == null) {
            razorpay = new Razorpay(null, null, null);
        }
    }

    public record Razorpay(String keyId, String keySecret, String baseUrl) {

        public Razorpay {
            if (baseUrl == null || baseUrl.isBlank()) {
                baseUrl = "https://api.razorpay.com";
            }
        }
    }
}

package com.theclub.eventservice.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.Locale;

/**
 * Per-deployment defaults for new events.
 *
 * @param currency ISO 4217 code used when an event doesn't name one (EVENTS_DEFAULT_CURRENCY, default INR)
 */
@ConfigurationProperties(prefix = "theclub.events")
public record EventDefaultsProperties(String currency) {

    public EventDefaultsProperties {
        currency = currency == null || currency.isBlank() ? "INR" : currency.trim().toUpperCase(Locale.ROOT);
        if (!currency.matches("[A-Z]{3}")) {
            throw new IllegalArgumentException("theclub.events.currency must be a 3-letter ISO currency code, got " + currency);
        }
    }
}

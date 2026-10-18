package com.theclub.eventservice.payment;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.web.client.RestClient;

import java.util.Locale;

@Configuration
public class PaymentGatewayConfig {

    @Bean
    public PaymentGateway paymentGateway(PaymentProperties properties, RestClient.Builder restClientBuilder,
                                         Environment environment) {
        return create(properties, restClientBuilder, environment);
    }

    /**
     * Fails fast on a missing or unknown provider, missing Razorpay credentials, or the mock provider in production.
     */
    static PaymentGateway create(PaymentProperties properties, RestClient.Builder restClientBuilder,
                                 Environment environment) {
        String provider = properties.provider();
        if (provider == null || provider.isBlank()) {
            throw new IllegalStateException(
                    "PAYMENT_PROVIDER (theclub.payments.provider) must be set to 'razorpay' or, for development, 'mock'");
        }
        return switch (provider.trim().toLowerCase(Locale.ROOT)) {
            case "mock" -> {
                if (environment.acceptsProfiles(Profiles.of("prod"))) {
                    throw new IllegalStateException("The mock payment provider cannot be used in production");
                }
                yield new MockPaymentGateway(properties.mockSecret());
            }
            case "razorpay" -> new RazorpayPaymentGateway(restClientBuilder, properties.razorpay());
            default -> throw new IllegalStateException(
                    "Unknown payment provider '" + provider + "'; expected 'razorpay' or 'mock'");
        };
    }
}

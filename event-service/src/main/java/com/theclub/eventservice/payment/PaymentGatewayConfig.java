package com.theclub.eventservice.payment;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

import java.util.Locale;

@Configuration
public class PaymentGatewayConfig {

    @Bean
    public PaymentGateway paymentGateway(PaymentProperties properties, RestClient.Builder restClientBuilder) {
        return switch (properties.provider().toLowerCase(Locale.ROOT)) {
            case "mock" -> new MockPaymentGateway(properties.mockSecret());
            case "razorpay" -> new RazorpayPaymentGateway(restClientBuilder, properties.razorpay());
            default -> throw new IllegalStateException("Unknown payment provider: " + properties.provider());
        };
    }
}

package com.theclub.eventservice.payment;

import com.theclub.common.web.ApiException;
import com.theclub.eventservice.model.PaymentProvider;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Razorpay Orders, Payments and Refunds APIs plus checkout and webhook signature verification.
 */
public class RazorpayPaymentGateway implements PaymentGateway {

    private final RestClient restClient;
    private final String keyId;
    private final String keySecret;
    private final String webhookSecret;

    public RazorpayPaymentGateway(RestClient.Builder builder, PaymentProperties.Razorpay properties) {
        if (isBlank(properties.keyId()) || isBlank(properties.keySecret())) {
            throw new IllegalStateException("RAZORPAY_KEY_ID and RAZORPAY_KEY_SECRET are required for the razorpay provider");
        }
        if (isBlank(properties.webhookSecret())) {
            throw new IllegalStateException("RAZORPAY_WEBHOOK_SECRET is required for the razorpay provider");
        }
        this.keyId = properties.keyId();
        this.keySecret = properties.keySecret();
        this.webhookSecret = properties.webhookSecret();
        this.restClient = builder.baseUrl(properties.baseUrl())
                .defaultHeaders(h -> h.setBasicAuth(keyId, keySecret))
                .build();
    }

    @Override
    public String createOrder(BigDecimal amount, String currency, String receipt) {
        try {
            Map<?, ?> response = restClient.post()
                    .uri("/v1/orders")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of("amount", Money.toMinor(amount), "currency", currency, "receipt", receipt))
                    .retrieve()
                    .body(Map.class);
            Object id = response == null ? null : response.get("id");
            if (id == null) {
                throw ApiException.badGateway("Payment provider did not return an order id");
            }
            return id.toString();
        } catch (RestClientException e) {
            throw ApiException.badGateway("Could not create payment order");
        }
    }

    @Override
    public boolean verify(String providerOrderId, String providerPaymentId, String signature) {
        return HmacSigner.matches(keySecret, providerOrderId, providerPaymentId, signature);
    }

    @Override
    public boolean verifyWebhook(byte[] rawBody, String signature) {
        return HmacSigner.bodyMatches(webhookSecret, rawBody, signature);
    }

    @Override
    public Optional<CapturedPayment> findCapturedPayment(String providerOrderId) {
        Map<?, ?> response = restClient.get()
                .uri("/v1/orders/{id}/payments", providerOrderId)
                .retrieve()
                .body(Map.class);
        if (response == null || !(response.get("items") instanceof List<?> items)) {
            return Optional.empty();
        }
        return items.stream()
                .filter(Map.class::isInstance)
                .map(Map.class::cast)
                .filter(item -> "captured".equals(item.get("status")))
                .findFirst()
                .map(item -> new CapturedPayment(String.valueOf(item.get("id")),
                        ((Number) item.get("amount")).longValue(), String.valueOf(item.get("currency"))));
    }

    @Override
    public String refund(String providerPaymentId, BigDecimal amount, String reason) {
        Map<?, ?> response = restClient.post()
                .uri("/v1/payments/{id}/refund", providerPaymentId)
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("amount", Money.toMinor(amount),
                        "notes", Map.of("reason", reason == null ? "" : reason)))
                .retrieve()
                .body(Map.class);
        Object id = response == null ? null : response.get("id");
        if (id == null) {
            throw new IllegalStateException("Payment provider did not return a refund id");
        }
        return id.toString();
    }

    @Override
    public PaymentProvider provider() {
        return PaymentProvider.RAZORPAY;
    }

    @Override
    public String keyId() {
        return keyId;
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}

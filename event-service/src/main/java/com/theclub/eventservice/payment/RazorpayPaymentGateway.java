package com.theclub.eventservice.payment;

import com.theclub.common.web.ApiException;
import com.theclub.eventservice.model.PaymentProvider;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Map;

/**
 * Razorpay Orders API + checkout signature verification.
 */
public class RazorpayPaymentGateway implements PaymentGateway {

    private final RestClient restClient;
    private final String keyId;
    private final String keySecret;

    public RazorpayPaymentGateway(RestClient.Builder builder, PaymentProperties.Razorpay properties) {
        if (isBlank(properties.keyId()) || isBlank(properties.keySecret())) {
            throw new IllegalStateException("RAZORPAY_KEY_ID and RAZORPAY_KEY_SECRET are required for the razorpay provider");
        }
        this.keyId = properties.keyId();
        this.keySecret = properties.keySecret();
        this.restClient = builder.baseUrl(properties.baseUrl())
                .defaultHeaders(h -> h.setBasicAuth(keyId, keySecret))
                .build();
    }

    @Override
    public String createOrder(BigDecimal amount, String currency, String receipt) {
        long minorUnits = amount.movePointRight(2).setScale(0, RoundingMode.HALF_UP).longValueExact();
        try {
            Map<?, ?> response = restClient.post()
                    .uri("/v1/orders")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of("amount", minorUnits, "currency", currency, "receipt", receipt))
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

package com.theclub.eventservice.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.theclub.common.web.ApiException;
import com.theclub.eventservice.model.PaymentWebhookEvent;
import com.theclub.eventservice.payment.PaymentGateway;
import com.theclub.eventservice.repository.PaymentWebhookEventRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.HexFormat;

/**
 * Razorpay webhooks. Each delivery is recorded by event id in the same transaction as its effects, so redeliveries
 * are no-ops and a failed delivery is retried by the provider.
 */
@Service
public class PaymentWebhookService {

    public enum Outcome { PROCESSED, DUPLICATE, IGNORED }

    private static final Logger log = LoggerFactory.getLogger(PaymentWebhookService.class);
    private static final int MAX_ID_LENGTH = 100;
    private static final int MAX_TYPE_LENGTH = 50;

    private final PaymentGateway gateway;
    private final PaymentService paymentService;
    private final PaymentWebhookEventRepository processed;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    public PaymentWebhookService(PaymentGateway gateway, PaymentService paymentService,
                                 PaymentWebhookEventRepository processed, ObjectMapper objectMapper, Clock clock) {
        this.gateway = gateway;
        this.paymentService = paymentService;
        this.processed = processed;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    /** Verifies the signature over the exact bytes received. */
    public boolean signatureValid(byte[] rawBody, String signature) {
        return gateway.verifyWebhook(rawBody, signature);
    }

    @Transactional
    public Outcome process(String deliveryId, byte[] rawBody) {
        JsonNode root;
        try {
            root = objectMapper.readTree(rawBody);
        } catch (IOException e) {
            throw ApiException.badRequest("Webhook body is not valid JSON");
        }
        String type = root.path("event").asText("");
        String eventId = deliveryId != null && !deliveryId.isBlank() ? deliveryId.trim() : "sha256:" + sha256(rawBody);
        if (eventId.length() > MAX_ID_LENGTH) {
            eventId = eventId.substring(0, MAX_ID_LENGTH);
        }
        if (processed.existsById(eventId)) {
            log.info("Webhook {} ({}) already processed", eventId, type);
            return Outcome.DUPLICATE;
        }
        processed.saveAndFlush(new PaymentWebhookEvent(eventId,
                type.length() > MAX_TYPE_LENGTH ? type.substring(0, MAX_TYPE_LENGTH) : type, LocalDateTime.now(clock)));

        JsonNode payload = root.path("payload");
        JsonNode payment = payload.path("payment").path("entity");
        switch (type) {
            case "payment.captured", "order.paid" -> {
                String orderId = payment.path("order_id").asText(null);
                if (orderId == null) {
                    log.warn("Webhook {} ({}) has no order id", eventId, type);
                    return Outcome.IGNORED;
                }
                paymentService.settleCapturedOrder(orderId, payment.path("id").asText(null),
                        payment.path("amount").asLong(-1), payment.path("currency").asText(null));
            }
            case "payment.failed" -> {
                String orderId = payment.path("order_id").asText(null);
                if (orderId != null) {
                    paymentService.markFailedByOrder(orderId);
                }
            }
            case "refund.processed" -> {
                JsonNode refund = payload.path("refund").path("entity");
                String paymentId = refund.path("payment_id").asText(null);
                if (paymentId != null) {
                    paymentService.markRefundProcessed(paymentId, refund.path("id").asText(null));
                }
            }
            default -> {
                log.debug("Ignoring webhook {} of type {}", eventId, type);
                return Outcome.IGNORED;
            }
        }
        log.info("Processed webhook {} ({})", eventId, type);
        return Outcome.PROCESSED;
    }

    private static String sha256(byte[] body) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(body));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}

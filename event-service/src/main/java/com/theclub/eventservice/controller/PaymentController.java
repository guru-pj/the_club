package com.theclub.eventservice.controller;

import com.theclub.common.security.AuthenticatedUser;
import com.theclub.common.web.ApiException;
import com.theclub.eventservice.dto.ConfirmPaymentRequest;
import com.theclub.eventservice.dto.MockSignatureRequest;
import com.theclub.eventservice.dto.MockSignatureResponse;
import com.theclub.eventservice.dto.PaymentResponse;
import com.theclub.eventservice.dto.RefundRequest;
import com.theclub.eventservice.service.PaymentService;
import com.theclub.eventservice.service.PaymentWebhookService;
import jakarta.validation.Valid;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/payments")
public class PaymentController {

    private final PaymentService paymentService;
    private final PaymentWebhookService webhooks;

    public PaymentController(PaymentService paymentService, PaymentWebhookService webhooks) {
        this.paymentService = paymentService;
        this.webhooks = webhooks;
    }

    @GetMapping("/{paymentId}")
    public PaymentResponse get(@PathVariable UUID paymentId, @AuthenticationPrincipal AuthenticatedUser caller) {
        return paymentService.get(paymentId, caller);
    }

    @PostMapping("/{paymentId}/confirm")
    public PaymentResponse confirm(@PathVariable UUID paymentId, @Valid @RequestBody ConfirmPaymentRequest request,
                                   @AuthenticationPrincipal AuthenticatedUser caller) {
        return paymentService.confirm(paymentId, request, caller);
    }

    /** Club owner or admin refunds a successful payment; the member's registration is cancelled. */
    @PostMapping("/{paymentId}/refund")
    public PaymentResponse refund(@PathVariable UUID paymentId,
                                  @Valid @RequestBody(required = false) RefundRequest request,
                                  @AuthenticationPrincipal AuthenticatedUser caller) {
        return paymentService.refund(paymentId, request == null ? null : request.reason(), caller);
    }

    /** Development only (PAYMENT_PROVIDER=mock): returns the signature a real checkout would produce. */
    @PostMapping("/{paymentId}/mock-signature")
    public MockSignatureResponse mockSignature(@PathVariable UUID paymentId,
                                               @Valid @RequestBody MockSignatureRequest request,
                                               @AuthenticationPrincipal AuthenticatedUser caller) {
        return paymentService.mockSignature(paymentId, request.providerPaymentId(), caller);
    }

    /**
     * Razorpay webhook (public). The body is read as raw bytes because the signature covers the exact payload.
     */
    @PostMapping("/webhooks/razorpay")
    public ResponseEntity<Void> razorpayWebhook(@RequestBody byte[] body,
                                                @RequestHeader(value = "X-Razorpay-Signature", required = false) String signature,
                                                @RequestHeader(value = "X-Razorpay-Event-Id", required = false) String eventId) {
        if (!webhooks.signatureValid(body, signature)) {
            throw ApiException.badRequest("Invalid webhook signature");
        }
        try {
            webhooks.process(eventId, body);
        } catch (DataIntegrityViolationException duplicate) {
            // a concurrent delivery of the same event won the insert; it has been processed
        }
        return ResponseEntity.ok().build();
    }
}

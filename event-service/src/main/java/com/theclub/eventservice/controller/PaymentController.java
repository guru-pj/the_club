package com.theclub.eventservice.controller;

import com.theclub.common.security.AuthenticatedUser;
import com.theclub.eventservice.dto.ConfirmPaymentRequest;
import com.theclub.eventservice.dto.MockSignatureRequest;
import com.theclub.eventservice.dto.MockSignatureResponse;
import com.theclub.eventservice.dto.PaymentResponse;
import com.theclub.eventservice.service.PaymentService;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/payments")
public class PaymentController {

    private final PaymentService paymentService;

    public PaymentController(PaymentService paymentService) {
        this.paymentService = paymentService;
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

    /** Development only (PAYMENT_PROVIDER=mock): returns the signature a real checkout would produce. */
    @PostMapping("/{paymentId}/mock-signature")
    public MockSignatureResponse mockSignature(@PathVariable UUID paymentId,
                                               @Valid @RequestBody MockSignatureRequest request,
                                               @AuthenticationPrincipal AuthenticatedUser caller) {
        return paymentService.mockSignature(paymentId, request.providerPaymentId(), caller);
    }
}

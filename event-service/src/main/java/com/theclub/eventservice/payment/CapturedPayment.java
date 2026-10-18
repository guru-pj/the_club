package com.theclub.eventservice.payment;

/**
 * A payment the provider reports as captured for an order.
 *
 * @param amountMinor amount in the currency's minor unit (paise for INR).
 */
public record CapturedPayment(String providerPaymentId, long amountMinor, String currency) {
}

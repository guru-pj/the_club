package com.theclub.eventservice.model;

/**
 * CREATED → SUCCEEDED → (REFUND_PENDING →) REFUNDED; CREATED → FAILED | EXPIRED.
 * A capture reported later for a FAILED or EXPIRED payment still settles it, because the money was taken.
 */
public enum PaymentStatus {
    CREATED,
    SUCCEEDED,
    FAILED,
    /** Not paid within the hold window; its registration was released. */
    EXPIRED,
    /** Refund requested but not yet accepted by the provider; retried by the reconciliation job. */
    REFUND_PENDING,
    REFUNDED;

    public boolean isSettled() {
        return this == SUCCEEDED || this == REFUND_PENDING || this == REFUNDED;
    }
}

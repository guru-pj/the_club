package com.theclub.notificationservice.model;

public enum EmailStatus {
    /** Waiting for its first (or next) delivery attempt. */
    PENDING,
    /** Claimed by a dispatcher; reset to PENDING if it is stuck (e.g. the instance crashed mid-send). */
    SENDING,
    SENT,
    /** Permanent failure or retries exhausted; an admin can retry it. */
    FAILED
}

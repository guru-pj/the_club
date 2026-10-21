package com.theclub.eventservice.model;

public enum EventStatus {
    /** Being prepared: only the club owner sees it; nobody can register yet. */
    DRAFT,
    /** Published: listed, and open for registration within its window. */
    SCHEDULED,
    CANCELLED,
    COMPLETED
}

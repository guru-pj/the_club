package com.theclub.eventservice.model;

/** Whether someone can register for an event right now, and if not, why not (shown on event cards). */
public enum RegistrationState {
    OPEN,
    NOT_YET_OPEN,
    CLOSED,
    FULL,
    /** The event has started or finished. */
    PAST,
    CANCELLED
}

package com.theclub.clubservice.model;

public enum PostType {
    /** The owner's news for the club */
    ANNOUNCEMENT,
    /** The owner's write-up of one of the club's events */
    EVENT_RECAP,
    /** A subscriber's post */
    POST,
    /** The owner sharing another club's post or an event into this club's feed */
    RESHARE
}

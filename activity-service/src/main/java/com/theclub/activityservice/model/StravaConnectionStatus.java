package com.theclub.activityservice.model;

public enum StravaConnectionStatus {
    /** Tokens are valid (or refreshable). */
    ACTIVE,
    /** The athlete revoked access on Strava, or the refresh token stopped working; tokens were cleared. */
    REVOKED
}

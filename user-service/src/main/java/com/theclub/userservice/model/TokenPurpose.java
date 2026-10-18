package com.theclub.userservice.model;

import java.time.Duration;

/** Single-use account tokens sent by email, with how long each stays valid. */
public enum TokenPurpose {
    EMAIL_VERIFICATION(Duration.ofHours(48)),
    PASSWORD_RESET(Duration.ofMinutes(30));

    private final Duration validity;

    TokenPurpose(Duration validity) {
        this.validity = validity;
    }

    public Duration validity() {
        return validity;
    }
}

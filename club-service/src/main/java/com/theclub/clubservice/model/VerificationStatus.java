package com.theclub.clubservice.model;

import java.util.EnumSet;
import java.util.Set;

public enum VerificationStatus {
    PENDING,
    NEEDS_INFO,
    UNDER_REVIEW,
    APPROVED,
    REJECTED;

    /** Statuses in which a request is still being processed; a user may only have one open request. */
    public static final Set<VerificationStatus> OPEN = EnumSet.of(PENDING, NEEDS_INFO, UNDER_REVIEW);
}

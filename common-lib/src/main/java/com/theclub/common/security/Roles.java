package com.theclub.common.security;

/**
 * Role names carried in the JWT "roles" claim. Spring authorities are these names prefixed with "ROLE_".
 */
public final class Roles {

    public static final String USER = "USER";
    public static final String CLUB_OWNER = "CLUB_OWNER";
    public static final String ADMIN = "ADMIN";
    /** Granted to service-to-service calls that present the internal API key. */
    public static final String INTERNAL = "INTERNAL";

    private Roles() {
    }
}

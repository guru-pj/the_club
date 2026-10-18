package com.theclub.authservice.controller;

import com.theclub.authservice.config.AuthProperties;
import com.theclub.common.security.JwtProperties;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * The refresh token cookie: HttpOnly so scripts can't read it, SameSite=Strict against CSRF,
 * and scoped to the auth endpoints that need it.
 */
@Component
public class RefreshCookies {

    public static final String NAME = "theclub_refresh";
    static final String PATH = "/api/v1/auth";

    private final boolean secure;
    private final Duration maxAge;

    public RefreshCookies(AuthProperties authProperties, JwtProperties jwtProperties) {
        this.secure = authProperties.cookieSecure();
        this.maxAge = jwtProperties.refreshExpiration();
    }

    public String issue(String refreshToken) {
        return build(refreshToken, maxAge).toString();
    }

    public String clear() {
        return build("", Duration.ZERO).toString();
    }

    private ResponseCookie build(String value, Duration age) {
        return ResponseCookie.from(NAME, value)
                .httpOnly(true)
                .secure(secure)
                .sameSite("Strict")
                .path(PATH)
                .maxAge(age)
                .build();
    }
}

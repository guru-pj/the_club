package com.theclub.authservice.controller;

import com.theclub.authservice.dto.LoginRequest;
import com.theclub.authservice.dto.RefreshTokenRequest;
import com.theclub.authservice.dto.TokenClaimsResponse;
import com.theclub.authservice.dto.TokenResponse;
import com.theclub.authservice.service.AuthService;
import com.theclub.common.security.AuthenticatedUser;
import com.theclub.common.web.ApiException;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Login/refresh return the tokens in the body (for non-browser clients) and also set the refresh token
 * as an HttpOnly cookie; browsers should keep only the access token, in memory.
 */
@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final AuthService authService;
    private final RefreshCookies cookies;

    public AuthController(AuthService authService, RefreshCookies cookies) {
        this.authService = authService;
        this.cookies = cookies;
    }

    @PostMapping("/login")
    public ResponseEntity<TokenResponse> login(@Valid @RequestBody LoginRequest request) {
        return withCookie(authService.login(request.email(), request.password()));
    }

    @PostMapping("/refresh")
    public ResponseEntity<TokenResponse> refresh(@Valid @RequestBody(required = false) RefreshTokenRequest request,
                                                 @CookieValue(name = RefreshCookies.NAME, required = false) String cookie) {
        try {
            return withCookie(authService.refresh(tokenFrom(request, cookie)));
        } catch (ApiException e) {
            // drop a stale or revoked cookie so the browser stops presenting it
            throw e.withHeader(HttpHeaders.SET_COOKIE, cookies.clear());
        }
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(@Valid @RequestBody(required = false) RefreshTokenRequest request,
                                       @CookieValue(name = RefreshCookies.NAME, required = false) String cookie) {
        authService.logout(tokenFrom(request, cookie));
        return ResponseEntity.noContent().header(HttpHeaders.SET_COOKIE, cookies.clear()).build();
    }

    @PostMapping("/logout-all")
    public ResponseEntity<Void> logoutAll(@AuthenticationPrincipal AuthenticatedUser caller) {
        authService.revokeAllSessions(caller.id());
        return ResponseEntity.noContent().header(HttpHeaders.SET_COOKIE, cookies.clear()).build();
    }

    @GetMapping("/validate")
    public TokenClaimsResponse validate(@AuthenticationPrincipal AuthenticatedUser caller) {
        return new TokenClaimsResponse(caller.id(), caller.email(), caller.name(), caller.roles());
    }

    private ResponseEntity<TokenResponse> withCookie(TokenResponse tokens) {
        return ResponseEntity.ok().header(HttpHeaders.SET_COOKIE, cookies.issue(tokens.refreshToken())).body(tokens);
    }

    private static String tokenFrom(RefreshTokenRequest request, String cookie) {
        if (request != null && request.refreshToken() != null && !request.refreshToken().isBlank()) {
            return request.refreshToken();
        }
        return cookie;
    }
}

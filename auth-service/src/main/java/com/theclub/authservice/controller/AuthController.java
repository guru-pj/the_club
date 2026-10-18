package com.theclub.authservice.controller;

import com.theclub.authservice.dto.LoginRequest;
import com.theclub.authservice.dto.RefreshTokenRequest;
import com.theclub.authservice.dto.TokenClaimsResponse;
import com.theclub.authservice.dto.TokenResponse;
import com.theclub.authservice.service.AuthService;
import com.theclub.common.security.AuthenticatedUser;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/login")
    public TokenResponse login(@Valid @RequestBody LoginRequest request) {
        return authService.login(request.email(), request.password());
    }

    @PostMapping("/refresh")
    public TokenResponse refresh(@Valid @RequestBody RefreshTokenRequest request) {
        return authService.refresh(request.refreshToken());
    }

    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void logout(@Valid @RequestBody RefreshTokenRequest request) {
        authService.logout(request.refreshToken());
    }

    @GetMapping("/validate")
    public TokenClaimsResponse validate(@AuthenticationPrincipal AuthenticatedUser caller) {
        return new TokenClaimsResponse(caller.id(), caller.email(), caller.name(), caller.roles());
    }
}

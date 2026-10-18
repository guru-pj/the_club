package com.theclub.userservice.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;

import java.util.UUID;

@FeignClient(name = "auth-service", url = "${theclub.clients.auth-service.url:}", path = "/internal/auth")
public interface AuthClient {

    /** Revokes every refresh token of the user. */
    @DeleteMapping("/sessions/{userId}")
    void revokeSessions(@PathVariable("userId") UUID userId);
}

package com.theclub.authservice.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import java.util.UUID;

@FeignClient(name = "user-service", url = "${theclub.clients.user-service.url:}", path = "/internal/users")
public interface UserServiceClient {

    @GetMapping("/by-email/{email}")
    UserCredentials getByEmail(@PathVariable("email") String email);

    @GetMapping("/{id}")
    UserCredentials getById(@PathVariable("id") UUID id);
}

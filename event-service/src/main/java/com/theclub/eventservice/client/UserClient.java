package com.theclub.eventservice.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

import java.util.List;
import java.util.UUID;

@FeignClient(name = "user-service", contextId = "userClient", url = "${theclub.clients.user-service.url:}",
        path = "/internal/users")
public interface UserClient {

    @PostMapping("/summaries")
    List<UserSummary> summaries(@RequestBody IdsRequest request);

    /** Used when no caller JWT carries the member's email (webhooks, scheduled jobs). */
    @GetMapping("/{id}")
    UserDetails get(@PathVariable("id") UUID id);

    record UserSummary(UUID id, String name, String profilePicUrl) {
    }

    record UserDetails(UUID id, String name, String email) {
    }
}

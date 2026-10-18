package com.theclub.clubservice.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

import java.util.List;
import java.util.Set;
import java.util.UUID;

@FeignClient(name = "user-service", contextId = "userClient", url = "${theclub.clients.user-service.url:}",
        path = "/internal/users")
public interface UserClient {

    @PostMapping("/summaries")
    List<UserSummary> summaries(@RequestBody IdsRequest request);

    @PostMapping("/{id}/roles")
    UserCredentials addRole(@PathVariable("id") UUID id, @RequestBody RoleRequest request);

    record IdsRequest(List<UUID> ids) {
    }

    record UserSummary(UUID id, String name, String profilePicUrl) {
    }

    record RoleRequest(String role) {
    }

    record UserCredentials(UUID id, String name, String email, Set<String> roles, boolean active) {
    }
}

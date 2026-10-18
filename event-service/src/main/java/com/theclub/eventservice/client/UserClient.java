package com.theclub.eventservice.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

import java.util.List;
import java.util.UUID;

@FeignClient(name = "user-service", contextId = "userClient", url = "${theclub.clients.user-service.url:}",
        path = "/internal/users")
public interface UserClient {

    @PostMapping("/summaries")
    List<UserSummary> summaries(@RequestBody IdsRequest request);

    record UserSummary(UUID id, String name, String profilePicUrl) {
    }
}

package com.theclub.eventservice.service;

import com.theclub.eventservice.client.ClubClient;
import com.theclub.eventservice.client.IdsRequest;
import com.theclub.eventservice.client.UserClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Best-effort display names for leaderboards and lists; a failing lookup yields no names rather than an error.
 */
@Component
public class NameResolver {

    private static final Logger log = LoggerFactory.getLogger(NameResolver.class);

    private final UserClient userClient;
    private final ClubClient clubClient;

    public NameResolver(UserClient userClient, ClubClient clubClient) {
        this.userClient = userClient;
        this.clubClient = clubClient;
    }

    public Map<UUID, String> userNames(Collection<UUID> ids) {
        Map<UUID, String> names = new HashMap<>();
        if (ids.isEmpty()) {
            return names;
        }
        try {
            userClient.summaries(IdsRequest.of(ids)).forEach(u -> names.put(u.id(), u.name()));
        } catch (RuntimeException e) {
            log.warn("Could not resolve user names: {}", e.getMessage());
        }
        return names;
    }

    public Map<UUID, String> clubNames(Collection<UUID> ids) {
        Map<UUID, String> names = new HashMap<>();
        if (ids.isEmpty()) {
            return names;
        }
        try {
            clubClient.summaries(IdsRequest.of(ids)).forEach(c -> names.put(c.id(), c.name()));
        } catch (RuntimeException e) {
            log.warn("Could not resolve club names: {}", e.getMessage());
        }
        return names;
    }
}

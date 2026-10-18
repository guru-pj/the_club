package com.theclub.eventservice.service;

import com.theclub.eventservice.client.UserClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Looks up a member's email for notifications sent without a caller JWT. Best effort: null on failure,
 * in which case only the in-app notification is delivered.
 */
@Component
public class MemberEmails {

    private static final Logger log = LoggerFactory.getLogger(MemberEmails.class);

    private final UserClient userClient;

    public MemberEmails(UserClient userClient) {
        this.userClient = userClient;
    }

    public String emailOf(UUID userId) {
        try {
            UserClient.UserDetails details = userClient.get(userId);
            return details == null ? null : details.email();
        } catch (RuntimeException e) {
            log.warn("Could not look up email for user {}: {}", userId, e.getMessage());
            return null;
        }
    }
}

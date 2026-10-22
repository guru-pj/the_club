package com.theclub.clubservice.service;

import com.theclub.clubservice.dto.ChatDtos;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.Set;
import java.util.UUID;

/**
 * Delivers to this replica's connections only. Other replicas pick the message up from the database within a couple
 * of seconds ({@link ChatRelay}). The default; use {@link RedisChatFanout} with many replicas.
 */
@Component
@ConditionalOnProperty(name = "theclub.chat.fanout", havingValue = "database", matchIfMissing = true)
public class LocalChatFanout implements ChatFanout {

    private final ChatHub hub;

    public LocalChatFanout(ChatHub hub) {
        this.hub = hub;
    }

    @Override
    public void publish(Set<UUID> recipients, ChatDtos.Message message) {
        if (hub.claim(message)) {
            hub.deliver(recipients, message);
        }
    }
}

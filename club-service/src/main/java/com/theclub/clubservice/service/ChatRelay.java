package com.theclub.clubservice.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * With several club-service replicas, a message is sent to one of them while its readers may be connected to
 * another. Every replica therefore checks the database every couple of seconds for messages it hasn't passed on.
 */
@Component
public class ChatRelay {

    private static final Logger log = LoggerFactory.getLogger(ChatRelay.class);

    private final ChatService chat;

    public ChatRelay(ChatService chat) {
        this.chat = chat;
    }

    @Scheduled(fixedDelayString = "${theclub.chat.relay-interval:PT2S}")
    void relay() {
        try {
            chat.relayRecent();
        } catch (RuntimeException e) {
            log.warn("Passing on chat messages failed: {}", e.getMessage());
        }
    }

    @Configuration(proxyBeanMethods = false)
    @EnableScheduling
    static class SchedulingConfiguration {
    }
}

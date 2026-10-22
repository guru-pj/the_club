package com.theclub.clubservice.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * With several club-service replicas, a message is sent to one of them while its readers may be connected to
 * another. Every replica therefore checks the database every couple of seconds for messages it hasn't passed on.
 */
@Component
public class ChatRelay {

    private static final Logger log = LoggerFactory.getLogger(ChatRelay.class);

    private final ChatService chat;
    /** Looks back a little further than it waits, so nothing falls between two runs. */
    private final Duration window;

    public ChatRelay(ChatService chat, @Value("${theclub.chat.relay-interval:PT2S}") Duration interval) {
        this.chat = chat;
        this.window = interval.plusSeconds(8);
    }

    /** Every couple of seconds; with CHAT_FANOUT=redis only a safety net, so CHAT_RELAY_INTERVAL can be longer. */
    @Scheduled(fixedDelayString = "${theclub.chat.relay-interval:PT2S}")
    void relay() {
        try {
            chat.relayRecent(window);
        } catch (RuntimeException e) {
            log.warn("Passing on chat messages failed: {}", e.getMessage());
        }
    }

    @Configuration(proxyBeanMethods = false)
    @EnableScheduling
    static class SchedulingConfiguration {
    }
}

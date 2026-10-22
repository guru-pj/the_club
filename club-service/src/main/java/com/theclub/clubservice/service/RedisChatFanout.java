package com.theclub.clubservice.service;

import com.theclub.clubservice.dto.ChatDtos;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import tools.jackson.databind.ObjectMapper;

import java.nio.charset.StandardCharsets;
import java.util.Set;
import java.util.UUID;

/**
 * Every replica publishes stored messages to one Redis channel and delivers what it receives to its own connections,
 * so a message reaches people on any replica at once. If Redis is unavailable the message is delivered here, and
 * {@link ChatRelay} still catches up the other replicas from the database.
 */
public class RedisChatFanout implements ChatFanout, MessageListener {

    private static final Logger log = LoggerFactory.getLogger(RedisChatFanout.class);
    static final String CHANNEL = "theclub:chat";

    /** What travels on the channel: the message and who may receive it, worked out once by the sender's replica. */
    record Broadcast(Set<UUID> recipients, ChatDtos.Message message) {
    }

    private final StringRedisTemplate redis;
    private final ObjectMapper json;
    private final ChatHub hub;

    RedisChatFanout(StringRedisTemplate redis, ObjectMapper json, ChatHub hub) {
        this.redis = redis;
        this.json = json;
        this.hub = hub;
    }

    @Override
    public void publish(Set<UUID> recipients, ChatDtos.Message message) {
        try {
            redis.convertAndSend(CHANNEL, json.writeValueAsString(new Broadcast(recipients, message)));
        } catch (RuntimeException e) {
            log.warn("Publishing chat message {} to Redis failed, delivering locally: {}", message.id(), e.getMessage());
            deliver(recipients, message);
        }
    }

    @Override
    public void onMessage(Message message, byte[] pattern) {
        try {
            Broadcast broadcast = json.readValue(new String(message.getBody(), StandardCharsets.UTF_8), Broadcast.class);
            deliver(broadcast.recipients(), broadcast.message());
        } catch (RuntimeException e) {
            log.warn("Ignoring an unreadable chat broadcast: {}", e.getMessage());
        }
    }

    private void deliver(Set<UUID> recipients, ChatDtos.Message message) {
        if (hub.claim(message)) {
            hub.deliver(recipients, message);
        }
    }

    @Configuration(proxyBeanMethods = false)
    @ConditionalOnProperty(name = "theclub.chat.fanout", havingValue = "redis")
    static class RedisChatConfiguration {

        @Bean
        RedisChatFanout redisChatFanout(StringRedisTemplate redis, ObjectMapper json, ChatHub hub) {
            return new RedisChatFanout(redis, json, hub);
        }

        @Bean
        RedisMessageListenerContainer chatListener(RedisConnectionFactory connections, RedisChatFanout fanout) {
            RedisMessageListenerContainer container = new RedisMessageListenerContainer();
            container.setConnectionFactory(connections);
            container.addMessageListener(fanout, new ChannelTopic(CHANNEL));
            return container;
        }
    }
}

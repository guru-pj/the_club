package com.theclub.clubservice.service;

import com.theclub.clubservice.dto.ChatDtos;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.SmartLifecycle;
import org.springframework.http.MediaType;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.time.Duration;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * The live chat connections of this replica (Server-Sent Events). Messages are always stored first; this only tells
 * whoever is connected right now. Messages written on another replica reach these connections through
 * {@link ChatRelay}, so each one is delivered at most once per replica (by its ID and whether it was deleted).
 */
@Component
public class ChatHub implements SmartLifecycle {

    private static final Logger log = LoggerFactory.getLogger(ChatHub.class);

    /** Below nginx's and load balancers' idle timeouts (60s). */
    static final long HEARTBEAT_MILLIS = 20_000;
    static final int MAX_CONNECTIONS_PER_USER = 5;
    static final Duration REMEMBER_DELIVERED = Duration.ofMinutes(2);

    /** Clients reconnect after this; it keeps a token that expired meanwhile from holding a connection forever. */
    private final Duration connectionTimeout;
    private final Map<UUID, List<SseEmitter>> connections = new ConcurrentHashMap<>();
    private final Map<String, Long> delivered = new ConcurrentHashMap<>();
    private volatile boolean running;

    public ChatHub(@Value("${theclub.chat.stream-timeout:PT15M}") Duration connectionTimeout) {
        this.connectionTimeout = connectionTimeout;
    }

    public SseEmitter connect(UUID userId) {
        SseEmitter emitter = new SseEmitter(connectionTimeout.toMillis());
        List<SseEmitter> mine = connections.computeIfAbsent(userId, id -> new CopyOnWriteArrayList<>());
        mine.add(emitter);
        while (mine.size() > MAX_CONNECTIONS_PER_USER) {
            SseEmitter oldest = mine.removeFirst();
            oldest.complete();
        }
        Runnable remove = () -> remove(userId, emitter);
        emitter.onCompletion(remove);
        emitter.onTimeout(remove);
        emitter.onError(e -> remove.run());
        send(userId, emitter, SseEmitter.event().name("ready").data("{}", MediaType.APPLICATION_JSON));
        return emitter;
    }

    /** Whether anyone is connected to this replica. */
    public boolean idle() {
        return connections.isEmpty();
    }

    public Set<UUID> connectedUsers() {
        return Set.copyOf(connections.keySet());
    }

    /** True the first time a message (in this state) is offered; false when it was delivered already. */
    public boolean claim(ChatDtos.Message message) {
        long expires = System.currentTimeMillis() + REMEMBER_DELIVERED.toMillis();
        return delivered.putIfAbsent(key(message), expires) == null;
    }

    public boolean alreadyDelivered(UUID messageId, boolean deleted) {
        return delivered.containsKey(messageId + (deleted ? ":deleted" : ":new"));
    }

    /** Sends the message to those of {@code recipients} connected here; call {@link #claim} first. */
    public void deliver(Collection<UUID> recipients, ChatDtos.Message message) {
        ChatDtos.Event event = new ChatDtos.Event("message", message.conversationId(), message);
        for (UUID userId : recipients) {
            for (SseEmitter emitter : connections.getOrDefault(userId, List.of())) {
                send(userId, emitter, SseEmitter.event().name(event.type()).id(message.id().toString())
                        .data(event, MediaType.APPLICATION_JSON));
            }
        }
    }

    @Scheduled(fixedRate = HEARTBEAT_MILLIS, initialDelay = HEARTBEAT_MILLIS)
    void heartbeat() {
        connections.forEach((userId, emitters) -> emitters.forEach(
                emitter -> send(userId, emitter, SseEmitter.event().comment("keep-alive"))));
        long now = System.currentTimeMillis();
        delivered.values().removeIf(expires -> expires < now);
    }

    private void send(UUID userId, SseEmitter emitter, SseEmitter.SseEventBuilder event) {
        try {
            emitter.send(event);
        } catch (IOException | IllegalStateException e) {
            // the client went away; Tomcat tells the emitter, which removes itself
            remove(userId, emitter);
            log.debug("Chat connection of {} closed: {}", userId, e.getMessage());
        }
    }

    private void remove(UUID userId, SseEmitter emitter) {
        connections.computeIfPresent(userId, (id, emitters) -> {
            emitters.remove(emitter);
            return emitters.isEmpty() ? null : emitters;
        });
    }

    private static String key(ChatDtos.Message message) {
        return message.id() + (message.deleted() ? ":deleted" : ":new");
    }

    // Stops before the web server's graceful shutdown, which would otherwise wait for these never-ending responses.

    @Override
    public void start() {
        running = true;
    }

    @Override
    public void stop() {
        running = false;
        connections.values().forEach(emitters -> emitters.forEach(SseEmitter::complete));
        connections.clear();
    }

    @Override
    public boolean isRunning() {
        return running;
    }

    @Override
    public int getPhase() {
        return Integer.MAX_VALUE;
    }
}

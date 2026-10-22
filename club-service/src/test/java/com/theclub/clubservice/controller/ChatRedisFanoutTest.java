package com.theclub.clubservice.controller;

import com.theclub.clubservice.client.ActivityClient;
import com.theclub.clubservice.client.EventClient;
import com.theclub.clubservice.client.NotificationClient;
import com.theclub.clubservice.client.UserClient;
import com.theclub.clubservice.service.ChatFanout;
import com.theclub.clubservice.service.RedisChatFanout;
import com.theclub.common.media.MediaStore;
import com.theclub.common.security.JwtService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import tools.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * CHAT_FANOUT=redis: a new message reaches live connections through Redis pub/sub, as it would on another replica.
 * The database relay runs only hourly here, so it can't be what delivered the message.
 */
@SpringBootTest(properties = {"theclub.chat.fanout=redis", "theclub.chat.relay-interval=PT1H", "REDIS_ENABLED=true"})
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Testcontainers(disabledWithoutDocker = true)
class ChatRedisFanoutTest {

    @Container
    static final GenericContainer<?> REDIS = new GenericContainer<>("redis:7.4-alpine").withExposedPorts(6379);

    @DynamicPropertySource
    static void redis(DynamicPropertyRegistry registry) {
        registry.add("spring.data.redis.host", REDIS::getHost);
        registry.add("spring.data.redis.port", () -> REDIS.getMappedPort(6379));
    }

    @Autowired
    MockMvc mvc;
    @Autowired
    ObjectMapper json;
    @Autowired
    JwtService jwtService;
    @Autowired
    ChatFanout fanout;

    @MockitoBean
    UserClient userClient;
    @MockitoBean
    ActivityClient activityClient;
    @MockitoBean
    EventClient eventClient;
    @MockitoBean
    NotificationClient notificationClient;
    @MockitoBean
    MediaStore media;

    private String token(UUID id, String... roles) {
        return "Bearer " + jwtService.issueAccessToken(id, id + "@example.com", "User", List.of(roles));
    }

    @Test
    void messagesTravelThroughRedis() throws Exception {
        assertThat(fanout).isInstanceOf(RedisChatFanout.class);
        UUID owner = UUID.randomUUID();
        UUID member = UUID.randomUUID();
        String club = json.readTree(mvc.perform(post("/api/v1/clubs").header("Authorization", token(UUID.randomUUID(), "USER", "ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Redis %s\",\"city\":\"Chennai\",\"ownerId\":\"%s\"}".formatted(UUID.randomUUID(), owner)))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString()).get("id").asString();
        mvc.perform(post("/api/v1/clubs/{id}/join", club).header("Authorization", token(member, "USER"))).andExpect(status().isCreated());
        String chat = json.readTree(mvc.perform(get("/api/v1/chat/clubs/{id}", club).header("Authorization", token(member, "USER")))
                .andReturn().getResponse().getContentAsString()).get("id").asString();

        MvcResult stream = mvc.perform(get("/api/v1/chat/stream").header("Authorization", token(member, "USER")))
                .andExpect(request().asyncStarted()).andReturn();
        mvc.perform(post("/api/v1/chat/conversations/{id}/messages", chat).header("Authorization", token(owner, "USER", "CLUB_OWNER"))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"body\":\"Through Redis\"}"))
                .andExpect(status().isCreated());

        // pub/sub is asynchronous: give it a moment
        String received = "";
        for (int i = 0; i < 50 && !received.contains("Through Redis"); i++) {
            Thread.sleep(100);
            received = stream.getResponse().getContentAsString();
        }
        assertThat(received).contains("event:message").contains("Through Redis");
    }
}

package com.theclub.authservice.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.theclub.authservice.client.UserCredentials;
import com.theclub.authservice.client.UserServiceClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Set;
import java.util.UUID;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AuthControllerIntegrationTest {

    @Autowired
    MockMvc mvc;
    @Autowired
    ObjectMapper json;
    @Autowired
    PasswordEncoder passwordEncoder;
    @MockitoBean
    UserServiceClient userClient;

    UserCredentials user;

    @BeforeEach
    void setUp() {
        user = new UserCredentials(UUID.randomUUID(), "Guru", "guru@example.com",
                passwordEncoder.encode("password123"), Set.of("USER"), true);
        when(userClient.getByEmail("guru@example.com")).thenReturn(user);
        when(userClient.getById(user.id())).thenReturn(user);
    }

    private JsonNode login() throws Exception {
        String body = mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"guru@example.com\",\"password\":\"password123\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.expiresIn").value(3600))
                .andReturn().getResponse().getContentAsString();
        return json.readTree(body);
    }

    private static String refreshBody(String token) {
        return "{\"refreshToken\":\"" + token + "\"}";
    }

    @Test
    void loginThenValidate() throws Exception {
        String accessToken = login().get("accessToken").asText();

        mvc.perform(get("/api/v1/auth/validate").header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userId").value(user.id().toString()))
                .andExpect(jsonPath("$.email").value("guru@example.com"))
                .andExpect(jsonPath("$.roles[0]").value("USER"));
    }

    @Test
    void wrongPasswordIsUnauthorized() throws Exception {
        mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"guru@example.com\",\"password\":\"bad-password\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.detail").value("Invalid email or password"));
    }

    @Test
    void refreshRotatesAndOldTokenIsRejected() throws Exception {
        String refreshToken = login().get("refreshToken").asText();

        mvc.perform(post("/api/v1/auth/refresh").contentType(MediaType.APPLICATION_JSON).content(refreshBody(refreshToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").exists());
        mvc.perform(post("/api/v1/auth/refresh").contentType(MediaType.APPLICATION_JSON).content(refreshBody(refreshToken)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void logoutRevokesRefreshTokenAndIsIdempotent() throws Exception {
        String refreshToken = login().get("refreshToken").asText();

        mvc.perform(post("/api/v1/auth/logout").contentType(MediaType.APPLICATION_JSON).content(refreshBody(refreshToken)))
                .andExpect(status().isNoContent());
        mvc.perform(post("/api/v1/auth/logout").contentType(MediaType.APPLICATION_JSON).content(refreshBody(refreshToken)))
                .andExpect(status().isNoContent());
        mvc.perform(post("/api/v1/auth/refresh").contentType(MediaType.APPLICATION_JSON).content(refreshBody(refreshToken)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void validateRequiresToken() throws Exception {
        mvc.perform(get("/api/v1/auth/validate")).andExpect(status().isUnauthorized());
    }
}

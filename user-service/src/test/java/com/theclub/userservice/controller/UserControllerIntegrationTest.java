package com.theclub.userservice.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.theclub.common.security.JwtService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class UserControllerIntegrationTest {

    @Autowired
    MockMvc mvc;
    @Autowired
    ObjectMapper json;
    @Autowired
    JwtService jwtService;

    private JsonNode register(String email) throws Exception {
        String body = mvc.perform(post("/api/v1/users/register").contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Guru","email":"%s","password":"password123"}""".formatted(email)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.roles[0]").value("USER"))
                .andReturn().getResponse().getContentAsString();
        return json.readTree(body);
    }

    private String tokenFor(String id, String... roles) {
        return "Bearer " + jwtService.issueAccessToken(UUID.fromString(id), "x@example.com", "X", List.of(roles));
    }

    @Test
    void registerThenFetchSelfAndOthers() throws Exception {
        JsonNode user = register("guru@example.com");
        String id = user.get("id").asText();

        mvc.perform(get("/api/v1/users/{id}", id).header("Authorization", tokenFor(id, "USER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("guru@example.com"));

        mvc.perform(get("/api/v1/users/{id}", id).header("Authorization", tokenFor(UUID.randomUUID().toString(), "USER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").doesNotExist());
    }

    @Test
    void duplicateRegistrationIsConflict() throws Exception {
        register("dup@example.com");
        mvc.perform(post("/api/v1/users/register").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"G\",\"email\":\"DUP@example.com\",\"password\":\"password123\"}"))
                .andExpect(status().isConflict());
    }

    @Test
    void invalidRegistrationReportsFieldErrors() throws Exception {
        mvc.perform(post("/api/v1/users/register").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"\",\"email\":\"nope\",\"password\":\"short\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.email").exists())
                .andExpect(jsonPath("$.errors.password").exists());
    }

    @Test
    void protectedEndpointsRequireToken() throws Exception {
        mvc.perform(get("/api/v1/users/me")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/users/me").header("Authorization", "Bearer garbage")).andExpect(status().isUnauthorized());
    }

    @Test
    void usersCannotUpdateOrDeleteOthers() throws Exception {
        String id = register("victim@example.com").get("id").asText();
        String other = tokenFor(UUID.randomUUID().toString(), "USER");

        mvc.perform(put("/api/v1/users/{id}", id).header("Authorization", other)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"hacked\"}"))
                .andExpect(status().isForbidden());
        mvc.perform(delete("/api/v1/users/{id}", id).header("Authorization", other))
                .andExpect(status().isForbidden());
        mvc.perform(put("/api/v1/users/{id}", id).header("Authorization", tokenFor(id, "USER"))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"city\":\"Chennai\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.city").value("Chennai"));
    }

    @Test
    void emailLookupIsAdminOnly() throws Exception {
        register("lookup@example.com");
        mvc.perform(get("/api/v1/users/email/lookup@example.com").header("Authorization", tokenFor(UUID.randomUUID().toString(), "USER")))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/users/email/lookup@example.com").header("Authorization", tokenFor(UUID.randomUUID().toString(), "USER", "ADMIN")))
                .andExpect(status().isOk());
    }

    @Test
    void internalEndpointsRequireInternalKey() throws Exception {
        String id = register("internal@example.com").get("id").asText();

        mvc.perform(get("/internal/users/by-email/internal@example.com").header("Authorization", tokenFor(id, "USER", "ADMIN")))
                .andExpect(status().isForbidden());
        mvc.perform(get("/internal/users/by-email/internal@example.com").header("X-Internal-Key", "test-internal-key"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.passwordHash").exists());
        mvc.perform(post("/internal/users/{id}/roles", id).header("X-Internal-Key", "test-internal-key")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"role\":\"CLUB_OWNER\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.roles.length()").value(2));
    }
}

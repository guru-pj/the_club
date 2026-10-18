package com.theclub.authservice.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.theclub.authservice.client.UserCredentials;
import com.theclub.authservice.client.UserServiceClient;
import com.theclub.authservice.repository.AuthTokenRepository;
import com.theclub.authservice.repository.LoginAttemptRepository;
import com.theclub.authservice.support.MutableClock;
import com.theclub.common.security.JwtService;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.math.BigInteger;
import java.security.interfaces.RSAPublicKey;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AuthControllerIntegrationTest {

    static final Instant START = Instant.parse("2026-10-18T06:00:00Z");
    static final String PASSWORD = "Str0ng-pass!";

    @TestConfiguration
    static class ClockConfig {
        @Bean
        @Primary
        MutableClock testClock() {
            return new MutableClock(START);
        }
    }

    @Autowired
    MockMvc mvc;
    @Autowired
    ObjectMapper json;
    @Autowired
    PasswordEncoder passwordEncoder;
    @Autowired
    MutableClock clock;
    @Autowired
    JwtService jwtService;
    @Autowired
    AuthTokenRepository tokens;
    @Autowired
    LoginAttemptRepository attempts;
    @MockitoBean
    UserServiceClient userClient;

    UserCredentials user;

    @BeforeEach
    void setUp() {
        clock.set(START);
        tokens.deleteAll();
        attempts.deleteAll();
        user = credentials("guru@example.com", true);
        when(userClient.getByEmail("guru@example.com")).thenReturn(user);
        when(userClient.getById(user.id())).thenReturn(user);
    }

    private UserCredentials credentials(String email, boolean verified) {
        return new UserCredentials(UUID.randomUUID(), "Guru", email, passwordEncoder.encode(PASSWORD),
                Set.of("USER"), true, verified);
    }

    private MvcResult attempt(String email, String password) throws Exception {
        return mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"password\":\"" + password + "\"}"))
                .andReturn();
    }

    private MvcResult login() throws Exception {
        return mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"guru@example.com\",\"password\":\"" + PASSWORD + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.expiresIn").value(900))
                .andReturn();
    }

    private JsonNode body(MvcResult result) throws Exception {
        return json.readTree(result.getResponse().getContentAsString());
    }

    private static String refreshBody(String token) {
        return "{\"refreshToken\":\"" + token + "\"}";
    }

    @Test
    void loginSetsHardenedRefreshCookieAndValidates() throws Exception {
        MvcResult result = login();
        String setCookie = result.getResponse().getHeader("Set-Cookie");
        String refreshToken = body(result).get("refreshToken").asText();

        assertThat(setCookie).startsWith("theclub_refresh=" + refreshToken)
                .contains("Path=/api/v1/auth", "HttpOnly", "Secure", "SameSite=Strict", "Max-Age=1209600");

        mvc.perform(get("/api/v1/auth/validate").header("Authorization", "Bearer " + body(result).get("accessToken").asText()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userId").value(user.id().toString()))
                .andExpect(jsonPath("$.roles[0]").value("USER"));
    }

    @Test
    void refreshWorksFromCookieAloneAndRotates() throws Exception {
        String refreshToken = body(login()).get("refreshToken").asText();

        MvcResult refreshed = mvc.perform(post("/api/v1/auth/refresh").cookie(new Cookie("theclub_refresh", refreshToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").exists())
                .andReturn();
        String rotated = body(refreshed).get("refreshToken").asText();
        assertThat(rotated).isNotEqualTo(refreshToken);
        assertThat(refreshed.getResponse().getHeader("Set-Cookie")).startsWith("theclub_refresh=" + rotated);

        // the consumed token is rejected and the stale cookie is cleared
        mvc.perform(post("/api/v1/auth/refresh").cookie(new Cookie("theclub_refresh", refreshToken)))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string("Set-Cookie", org.hamcrest.Matchers.containsString("Max-Age=0")));
        // the body form still works for non-browser clients
        mvc.perform(post("/api/v1/auth/refresh").contentType(MediaType.APPLICATION_JSON).content(refreshBody(rotated)))
                .andExpect(status().isOk());
    }

    @Test
    void refreshWithoutAnyTokenIsUnauthorized() throws Exception {
        mvc.perform(post("/api/v1/auth/refresh")).andExpect(status().isUnauthorized());
    }

    @Test
    void logoutViaCookieClearsCookieAndIsIdempotent() throws Exception {
        String refreshToken = body(login()).get("refreshToken").asText();
        Cookie cookie = new Cookie("theclub_refresh", refreshToken);

        mvc.perform(post("/api/v1/auth/logout").cookie(cookie))
                .andExpect(status().isNoContent())
                .andExpect(header().string("Set-Cookie", org.hamcrest.Matchers.containsString("Max-Age=0")));
        mvc.perform(post("/api/v1/auth/logout").cookie(cookie)).andExpect(status().isNoContent());
        mvc.perform(post("/api/v1/auth/logout")).andExpect(status().isNoContent());
        mvc.perform(post("/api/v1/auth/refresh").cookie(cookie)).andExpect(status().isUnauthorized());
    }

    @Test
    void logoutAllRevokesEverySession() throws Exception {
        JsonNode first = body(login());
        JsonNode second = body(login());

        mvc.perform(post("/api/v1/auth/logout-all").header("Authorization", "Bearer " + first.get("accessToken").asText()))
                .andExpect(status().isNoContent())
                .andExpect(header().string("Set-Cookie", org.hamcrest.Matchers.containsString("Max-Age=0")));

        for (JsonNode session : new JsonNode[]{first, second}) {
            mvc.perform(post("/api/v1/auth/refresh").contentType(MediaType.APPLICATION_JSON)
                            .content(refreshBody(session.get("refreshToken").asText())))
                    .andExpect(status().isUnauthorized());
        }
        mvc.perform(post("/api/v1/auth/logout-all")).andExpect(status().isUnauthorized());
    }

    @Test
    void internalSessionRevocationNeedsInternalKey() throws Exception {
        String refreshToken = body(login()).get("refreshToken").asText();

        mvc.perform(delete("/internal/auth/sessions/{id}", user.id())).andExpect(status().isUnauthorized());
        mvc.perform(delete("/internal/auth/sessions/{id}", user.id()).header("X-Internal-Key", "test-internal-key"))
                .andExpect(status().isNoContent());
        mvc.perform(post("/api/v1/auth/refresh").contentType(MediaType.APPLICATION_JSON).content(refreshBody(refreshToken)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void locksAfterFiveFailuresEvenForCorrectPasswordUntilWindowPasses() throws Exception {
        for (int i = 0; i < 5; i++) {
            assertThat(attempt("guru@example.com", "wrong-password-" + i).getResponse().getStatus()).isEqualTo(401);
        }

        MvcResult locked = attempt("guru@example.com", PASSWORD);
        assertThat(locked.getResponse().getStatus()).isEqualTo(429);
        assertThat(locked.getResponse().getHeader("Retry-After")).isEqualTo("900");
        assertThat(body(locked).get("detail").asText()).isEqualTo("Too many failed login attempts. Try again in 15 minutes.");

        clock.advance(Duration.ofMinutes(14));
        assertThat(attempt("guru@example.com", PASSWORD).getResponse().getStatus()).isEqualTo(429);

        clock.advance(Duration.ofMinutes(1));
        assertThat(attempt("guru@example.com", PASSWORD).getResponse().getStatus()).isEqualTo(200);
    }

    @Test
    void unknownEmailsAreLockedToo() throws Exception {
        for (int i = 0; i < 5; i++) {
            assertThat(attempt("ghost@example.com", "whatever-" + i).getResponse().getStatus()).isEqualTo(401);
        }
        assertThat(attempt("ghost@example.com", "whatever").getResponse().getStatus()).isEqualTo(429);
    }

    @Test
    void successfulLoginResetsTheFailureCounter() throws Exception {
        for (int i = 0; i < 4; i++) {
            attempt("guru@example.com", "wrong-" + i);
        }
        login();
        for (int i = 0; i < 4; i++) {
            assertThat(attempt("guru@example.com", "wrong-again-" + i).getResponse().getStatus()).isEqualTo(401);
        }
        assertThat(attempt("guru@example.com", PASSWORD).getResponse().getStatus()).isEqualTo(200);
    }

    @Test
    void failuresOutsideTheWindowDoNotAccumulate() throws Exception {
        for (int i = 0; i < 4; i++) {
            attempt("guru@example.com", "wrong-" + i);
        }
        clock.advance(Duration.ofMinutes(16));
        assertThat(attempt("guru@example.com", "wrong-late").getResponse().getStatus()).isEqualTo(401);
        assertThat(attempt("guru@example.com", PASSWORD).getResponse().getStatus()).isEqualTo(200);
    }

    @Test
    void unverifiedEmailCannotLogIn() throws Exception {
        UserCredentials unverified = credentials("new@example.com", false);
        when(userClient.getByEmail("new@example.com")).thenReturn(unverified);

        mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"new@example.com\",\"password\":\"" + PASSWORD + "\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("EMAIL_NOT_VERIFIED"))
                .andExpect(jsonPath("$.detail").value("Please verify your email address before logging in"));
    }

    @Test
    void wrongPasswordIsUnauthorized() throws Exception {
        mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"guru@example.com\",\"password\":\"bad-password\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.detail").value("Invalid email or password"));
    }

    @Test
    void publishesJwksMatchingTheSigningKey() throws Exception {
        MvcResult result = mvc.perform(get("/api/v1/auth/.well-known/jwks.json"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.keys.length()").value(1))
                .andExpect(jsonPath("$.keys[0].kty").value("RSA"))
                .andExpect(jsonPath("$.keys[0].alg").value("RS256"))
                .andExpect(jsonPath("$.keys[0].use").value("sig"))
                .andExpect(jsonPath("$.keys[0].kid").value("k1"))
                .andReturn();
        JsonNode key = body(result).get("keys").get(0);
        RSAPublicKey expected = jwtService.verificationKeys().get("k1");
        assertThat(new BigInteger(1, Base64.getUrlDecoder().decode(key.get("n").asText()))).isEqualTo(expected.getModulus());
        assertThat(new BigInteger(1, Base64.getUrlDecoder().decode(key.get("e").asText()))).isEqualTo(expected.getPublicExponent());
    }

    @Test
    void validateRequiresToken() throws Exception {
        mvc.perform(get("/api/v1/auth/validate")).andExpect(status().isUnauthorized());
    }
}

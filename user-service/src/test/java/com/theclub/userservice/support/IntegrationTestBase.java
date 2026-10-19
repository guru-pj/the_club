package com.theclub.userservice.support;

import com.theclub.common.security.JwtService;
import com.theclub.userservice.client.AuthClient;
import com.theclub.userservice.client.NotificationClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Shared Spring context for MockMvc tests: downstream Feign clients are mocked and time is controllable.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(IntegrationTestBase.ClockConfig.class)
public abstract class IntegrationTestBase {

    public static final Instant START = Instant.parse("2026-10-18T06:00:00Z");

    @TestConfiguration
    public static class ClockConfig {
        @Bean
        @Primary
        MutableClock testClock() {
            return new MutableClock(START);
        }
    }

    @Autowired
    protected MockMvc mvc;
    @Autowired
    protected JwtService jwtService;
    @Autowired
    protected MutableClock clock;
    @MockitoBean
    protected NotificationClient notificationClient;
    @MockitoBean
    protected AuthClient authClient;

    protected String tokenFor(String id, String... roles) {
        return "Bearer " + jwtService.issueAccessToken(UUID.fromString(id), "x@example.com", "X", List.of(roles));
    }
}

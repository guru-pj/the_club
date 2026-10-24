package com.theclub.common.config;

import com.theclub.common.security.SecurityProperties;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ProductionSafetyCheckTest {

    private static final String STRONG_KEY = "x".repeat(40);

    private static ProductionSafetyCheck check(String profile, String internalKey, String adminPassword) {
        MockEnvironment env = new MockEnvironment();
        if (profile != null) {
            env.setActiveProfiles(profile);
        }
        if (adminPassword != null) {
            env.setProperty("theclub.admin.password", adminPassword);
        }
        return new ProductionSafetyCheck(env, new SecurityProperties(List.of(), internalKey));
    }

    @Test
    void ignoresNonProductionProfiles() {
        assertThatCode(() -> check("dev", "change-me", "change-me").afterPropertiesSet()).doesNotThrowAnyException();
        assertThatCode(() -> check(null, "short", null).afterPropertiesSet()).doesNotThrowAnyException();
    }

    @Test
    void rejectsWeakOrPlaceholderSecretsInProd() {
        assertThatThrownBy(() -> check("prod", "short", null).afterPropertiesSet())
                .hasMessageContaining("at least 32");
        assertThatThrownBy(() -> check("prod", "change-me-" + STRONG_KEY, null).afterPropertiesSet())
                .hasMessageContaining("placeholder");
        assertThatThrownBy(() -> check("prod", STRONG_KEY, "change-me-admin").afterPropertiesSet())
                .hasMessageContaining("ADMIN_PASSWORD");
    }

    private static ProductionSafetyCheck checkWith(String profile, String... properties) {
        MockEnvironment env = new MockEnvironment();
        if (profile != null) {
            env.setActiveProfiles(profile);
        }
        for (int i = 0; i < properties.length; i += 2) {
            env.setProperty(properties[i], properties[i + 1]);
        }
        return new ProductionSafetyCheck(env, new SecurityProperties(List.of(), STRONG_KEY));
    }

    @Test
    void rejectsDevelopmentSwitchesInProd() {
        assertThatThrownBy(() -> checkWith("prod", "theclub.auth.cookie-secure", "false").afterPropertiesSet())
                .hasMessageContaining("AUTH_COOKIE_SECURE");
        assertThatThrownBy(() -> checkWith("prod", "theclub.auth.require-verified-email", "false").afterPropertiesSet())
                .hasMessageContaining("REQUIRE_EMAIL_VERIFICATION");
        assertThatThrownBy(() -> checkWith("prod", "theclub.admin.email", "admin@theclub.local").afterPropertiesSet())
                .hasMessageContaining("ADMIN_EMAIL");
        assertThatThrownBy(() -> checkWith("prod", "theclub.admin.email", "ops@example.com").afterPropertiesSet())
                .hasMessageContaining("ADMIN_EMAIL");
        assertThatThrownBy(() -> checkWith("prod", "theclub.app.base-url", "http://club.example.in").afterPropertiesSet())
                .hasMessageContaining("APP_BASE_URL");
        assertThatThrownBy(() -> checkWith("prod", "theclub.app.base-url", "https://localhost:3000").afterPropertiesSet())
                .hasMessageContaining("APP_BASE_URL");
        assertThatThrownBy(() -> checkWith("prod", "theclub.app.base-url", "https://theclub.example.com").afterPropertiesSet())
                .hasMessageContaining("example address");
        // every problem is reported at once
        assertThatThrownBy(() -> checkWith("prod", "theclub.auth.cookie-secure", "false",
                "theclub.auth.require-verified-email", "false").afterPropertiesSet())
                .hasMessageContaining("AUTH_COOKIE_SECURE").hasMessageContaining("REQUIRE_EMAIL_VERIFICATION");
    }

    @Test
    void acceptsProductionSettingsInProd() {
        assertThatCode(() -> checkWith("prod", "theclub.auth.cookie-secure", "true",
                "theclub.auth.require-verified-email", "true", "theclub.admin.email", "ops@club.example.in",
                "theclub.app.base-url", "https://club.example.in").afterPropertiesSet())
                .doesNotThrowAnyException();
        // services that don't read a setting aren't affected by it
        assertThatCode(() -> checkWith("prod").afterPropertiesSet()).doesNotThrowAnyException();
    }

    @Test
    void refusesTheDevProfileOnAPublicAddress() {
        assertThatThrownBy(() -> checkWith("dev", "theclub.app.base-url", "https://club.example.in").afterPropertiesSet())
                .hasMessageContaining("SPRING_PROFILES_ACTIVE=prod");
        assertThatThrownBy(() -> checkWith(null, "theclub.app.base-url", "https://club.example.in").afterPropertiesSet())
                .hasMessageContaining("SPRING_PROFILES_ACTIVE=prod");
        // a deliberate test setup can opt out
        assertThatCode(() -> checkWith("dev", "theclub.app.base-url", "https://my-tunnel.example.in",
                "theclub.allow-dev-profile-on-public-url", "true").afterPropertiesSet()).doesNotThrowAnyException();
        // local and private-network addresses are development as usual
        for (String url : List.of("http://localhost:3000", "https://localhost", "https://club.local",
                "https://192.168.1.20:3000", "https://10.0.0.5", "https://172.20.0.4", "http://club.example.in")) {
            assertThatCode(() -> checkWith("dev", "theclub.app.base-url", url).afterPropertiesSet())
                    .as(url).doesNotThrowAnyException();
        }
    }

    @Test
    void acceptsRealSecretsInProd() {
        assertThatCode(() -> check("prod", STRONG_KEY, "a-real-admin-password").afterPropertiesSet())
                .doesNotThrowAnyException();
    }
}

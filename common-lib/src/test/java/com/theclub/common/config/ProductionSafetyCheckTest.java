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

    @Test
    void acceptsRealSecretsInProd() {
        assertThatCode(() -> check("prod", STRONG_KEY, "a-real-admin-password").afterPropertiesSet())
                .doesNotThrowAnyException();
    }
}

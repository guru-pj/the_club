package com.theclub.activityservice.strava;

import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class StravaConfigurationCheckTest {

    private static StravaProperties props(String clientId, String secret, String key, String verify, String redirect) {
        return new StravaProperties(clientId, secret, redirect, verify, null, null, null, null, key, null, null);
    }

    private static MockEnvironment env(String... profiles) {
        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles(profiles);
        return environment;
    }

    @Test
    void prodRequiresSecretEncryptionKeyVerifyTokenAndHttps() {
        StravaConfigurationCheck check = new StravaConfigurationCheck(
                props("123", "", "", "change-me-strava-verify-token", "http://localhost:3000/strava/callback"),
                env("prod"));
        assertThatThrownBy(check::afterPropertiesSet)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("STRAVA_CLIENT_SECRET")
                .hasMessageContaining("STRAVA_TOKEN_ENCRYPTION_KEY")
                .hasMessageContaining("STRAVA_WEBHOOK_VERIFY_TOKEN")
                .hasMessageContaining("https");
    }

    @Test
    void completeProdConfigurationStarts() {
        assertThatCode(() -> new StravaConfigurationCheck(
                props("123", "secret", StravaTokenCipherTest.KEY, "a-real-verify-token",
                        "https://club.example.com/strava/callback"), env("prod")).afterPropertiesSet())
                .doesNotThrowAnyException();
    }

    @Test
    void disabledIntegrationOrDevProfileIsNotChecked() {
        assertThatCode(() -> new StravaConfigurationCheck(props("", "", "", "", "http://x"), env("prod"))
                .afterPropertiesSet()).doesNotThrowAnyException();
        assertThatCode(() -> new StravaConfigurationCheck(props("123", "", "", "", "http://x"), env("dev"))
                .afterPropertiesSet()).doesNotThrowAnyException();
    }
}

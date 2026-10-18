package com.theclub.activityservice.strava;

import org.springframework.beans.factory.InitializingBean;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Refuses to start under the {@code prod} profile with a half-configured Strava integration.
 * The integration stays optional: no STRAVA_CLIENT_ID means it is simply disabled.
 */
@Component
public class StravaConfigurationCheck implements InitializingBean {

    private final StravaProperties properties;
    private final Environment environment;

    public StravaConfigurationCheck(StravaProperties properties, Environment environment) {
        this.properties = properties;
        this.environment = environment;
    }

    @Override
    public void afterPropertiesSet() {
        if (!environment.acceptsProfiles(Profiles.of("prod")) || !properties.isConfigured()) {
            return;
        }
        List<String> problems = new ArrayList<>();
        if (blank(properties.clientSecret())) {
            problems.add("STRAVA_CLIENT_SECRET is empty");
        }
        if (blank(properties.tokenEncryptionKey())) {
            problems.add("STRAVA_TOKEN_ENCRYPTION_KEY is empty (tokens must be encrypted at rest)");
        }
        String verifyToken = properties.webhookVerifyToken();
        if (blank(verifyToken) || verifyToken.contains("change-me")) {
            problems.add("STRAVA_WEBHOOK_VERIFY_TOKEN is empty or still the placeholder");
        }
        if (!properties.redirectUri().startsWith("https://")) {
            problems.add("STRAVA_REDIRECT_URI must use https in production");
        }
        if (!problems.isEmpty()) {
            throw new IllegalStateException("Strava is enabled (STRAVA_CLIENT_ID set) but misconfigured for production: "
                    + String.join("; ", problems));
        }
    }

    private static boolean blank(String value) {
        return value == null || value.isBlank();
    }
}

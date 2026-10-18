package com.theclub.activityservice.strava;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Strava API application settings. Leave clientId blank to disable the integration.
 */
@ConfigurationProperties(prefix = "theclub.strava")
public record StravaProperties(String clientId, String clientSecret, String redirectUri, String webhookVerifyToken,
                               String apiBaseUrl, String oauthBaseUrl) {

    public StravaProperties {
        if (apiBaseUrl == null || apiBaseUrl.isBlank()) {
            apiBaseUrl = "https://www.strava.com/api/v3";
        }
        if (oauthBaseUrl == null || oauthBaseUrl.isBlank()) {
            oauthBaseUrl = "https://www.strava.com/oauth";
        }
        if (redirectUri == null || redirectUri.isBlank()) {
            redirectUri = "http://localhost:8080/api/v1/strava/callback";
        }
    }

    public boolean isConfigured() {
        return clientId != null && !clientId.isBlank();
    }
}

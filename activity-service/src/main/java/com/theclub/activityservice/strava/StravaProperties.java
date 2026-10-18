package com.theclub.activityservice.strava;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Strava API application settings. Leave clientId blank to disable the integration.
 *
 * @param webhookCallbackUrl  public HTTPS URL Strava pushes events to, e.g. https://club.example.com/api/v1/strava/webhook
 * @param initialSyncDays     how far back the first sync of a newly connected athlete imports
 * @param tokenEncryptionKey  base64 of 32 random bytes (AES-256-GCM) used to encrypt OAuth tokens at rest
 * @param workerEnabled       run the webhook queue worker in this instance
 * @param workerBatchSize     events processed per worker run
 */
@ConfigurationProperties(prefix = "theclub.strava")
public record StravaProperties(String clientId, String clientSecret, String redirectUri, String webhookVerifyToken,
                               String apiBaseUrl, String oauthBaseUrl, String webhookCallbackUrl,
                               Integer initialSyncDays, String tokenEncryptionKey, Boolean workerEnabled,
                               Integer workerBatchSize) {

    public StravaProperties {
        if (apiBaseUrl == null || apiBaseUrl.isBlank()) {
            apiBaseUrl = "https://www.strava.com/api/v3";
        }
        if (oauthBaseUrl == null || oauthBaseUrl.isBlank()) {
            oauthBaseUrl = "https://www.strava.com/oauth";
        }
        if (redirectUri == null || redirectUri.isBlank()) {
            redirectUri = "http://localhost:3000/strava/callback";
        }
        if (initialSyncDays == null || initialSyncDays < 1) {
            initialSyncDays = 90;
        }
        if (workerEnabled == null) {
            workerEnabled = true;
        }
        if (workerBatchSize == null || workerBatchSize < 1) {
            workerBatchSize = 25;
        }
    }

    public boolean isConfigured() {
        return clientId != null && !clientId.isBlank();
    }
}

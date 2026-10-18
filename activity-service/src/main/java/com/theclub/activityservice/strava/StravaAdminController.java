package com.theclub.activityservice.strava;

import com.theclub.common.web.ApiException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClientException;

import java.time.Clock;
import java.util.List;

/**
 * Manages the app's Strava push subscription (Strava allows exactly one per application).
 * Strava validates a new subscription synchronously by calling
 * {@code GET <callback>?hub.mode=subscribe&hub.verify_token=…&hub.challenge=…}, so the callback URL must be public.
 */
@RestController
@RequestMapping("/api/v1/strava/admin/subscription")
@PreAuthorize("hasRole('ADMIN')")
public class StravaAdminController {

    private static final Logger log = LoggerFactory.getLogger(StravaAdminController.class);

    private final StravaClient client;
    private final StravaProperties properties;
    private final StravaRateLimiter rateLimiter;
    private final Clock clock;

    public StravaAdminController(StravaClient client, StravaProperties properties, StravaRateLimiter rateLimiter,
                                 Clock clock) {
        this.client = client;
        this.properties = properties;
        this.rateLimiter = rateLimiter;
        this.clock = clock;
    }

    public record SubscriptionStatus(List<StravaSubscription> subscriptions, String expectedCallbackUrl,
                                     boolean rateLimitPaused) {
    }

    @GetMapping
    public SubscriptionStatus get() {
        requireConfigured();
        return new SubscriptionStatus(strava(client::listSubscriptions), properties.webhookCallbackUrl(),
                rateLimiter.isPaused());
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public StravaSubscription create() {
        requireConfigured();
        String callback = properties.webhookCallbackUrl();
        if (callback == null || callback.isBlank()) {
            throw ApiException.badRequest("Set STRAVA_WEBHOOK_CALLBACK_URL (e.g. https://<host>/api/v1/strava/webhook) first");
        }
        String verifyToken = properties.webhookVerifyToken();
        if (verifyToken == null || verifyToken.isBlank()) {
            throw ApiException.badRequest("Set STRAVA_WEBHOOK_VERIFY_TOKEN first");
        }
        if (!strava(client::listSubscriptions).isEmpty()) {
            throw ApiException.conflict("A Strava subscription already exists; delete it before creating a new one");
        }
        return strava(() -> client.createSubscription(callback, verifyToken));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable long id) {
        requireConfigured();
        strava(() -> {
            client.deleteSubscription(id);
            return null;
        });
    }

    private <T> T strava(java.util.function.Supplier<T> call) {
        try {
            return call.get();
        } catch (StravaRateLimitedException e) {
            throw StravaService.rateLimited(e, clock);
        } catch (HttpClientErrorException e) {
            // Strava explains validation failures (unreachable callback, wrong verify token …) in the body
            log.warn("Strava subscription call rejected ({}): {}", e.getStatusCode().value(), e.getResponseBodyAsString());
            throw ApiException.badRequest("Strava rejected the request: " + e.getResponseBodyAsString());
        } catch (RestClientException e) {
            throw ApiException.badGateway("Could not reach Strava");
        }
    }

    private void requireConfigured() {
        if (!properties.isConfigured()) {
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "Strava integration is not configured");
        }
    }
}

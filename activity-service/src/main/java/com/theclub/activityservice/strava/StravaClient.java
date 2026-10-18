package com.theclub.activityservice.strava;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.util.List;
import java.util.function.Supplier;

/**
 * HTTP client for the Strava OAuth and REST APIs (v3).
 * <ul>
 *   <li>Every call is gated by {@link StravaRateLimiter} and feeds it the rate-limit headers of the response.</li>
 *   <li>429 becomes {@link StravaRateLimitedException} (requeue the work).</li>
 *   <li>Idempotent GETs are retried on 5xx and network errors (3 attempts, exponential backoff).</li>
 *   <li>Other 4xx errors propagate as {@link org.springframework.web.client.HttpClientErrorException}.</li>
 * </ul>
 * Tokens and the client secret are never logged.
 */
@Component
public class StravaClient {

    static final int MAX_ATTEMPTS = 3;
    static final Duration BASE_BACKOFF = Duration.ofMillis(500);
    private static final Logger log = LoggerFactory.getLogger(StravaClient.class);

    private final StravaProperties properties;
    private final RestClient oauth;
    private final RestClient api;
    private final StravaRateLimiter rateLimiter;
    private final StravaMetrics metrics;
    private final Sleeper sleeper;

    static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(5);
    static final Duration READ_TIMEOUT = Duration.ofSeconds(20);

    @Autowired
    public StravaClient(StravaProperties properties, RestClient.Builder builder,
                        StravaRateLimiter rateLimiter, StravaMetrics metrics) {
        this(properties, builder.clone().requestFactory(timeouts()), rateLimiter, metrics, Thread::sleep);
    }

    /** Fail fast on connect; allow slow list responses. */
    private static SimpleClientHttpRequestFactory timeouts() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(CONNECT_TIMEOUT);
        factory.setReadTimeout(READ_TIMEOUT);
        return factory;
    }

    StravaClient(StravaProperties properties, RestClient.Builder builder, StravaRateLimiter rateLimiter,
                 StravaMetrics metrics, Sleeper sleeper) {
        this.properties = properties;
        this.oauth = builder.clone().baseUrl(properties.oauthBaseUrl()).build();
        this.api = builder.clone().baseUrl(properties.apiBaseUrl()).build();
        this.rateLimiter = rateLimiter;
        this.metrics = metrics;
        this.sleeper = sleeper;
    }

    /** Pause between retries; replaced in tests. */
    @FunctionalInterface
    interface Sleeper {
        void sleep(long millis) throws InterruptedException;
    }

    // ------------------------------------------------------------------ OAuth

    public StravaTokenResponse exchangeCode(String code) {
        MultiValueMap<String, String> form = clientCredentials();
        form.add("code", code);
        form.add("grant_type", "authorization_code");
        return call("oauth_token", false, () -> postForm(oauth, "/token", form, StravaTokenResponse.class));
    }

    public StravaTokenResponse refresh(String refreshToken) {
        MultiValueMap<String, String> form = clientCredentials();
        form.add("refresh_token", refreshToken);
        form.add("grant_type", "refresh_token");
        return call("oauth_refresh", false, () -> postForm(oauth, "/token", form, StravaTokenResponse.class));
    }

    public void deauthorize(String accessToken) {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("access_token", accessToken);
        call("oauth_deauthorize", false, () -> postForm(oauth, "/deauthorize", form, Void.class));
    }

    // ------------------------------------------------------------------ Activities

    public StravaActivity getActivity(String accessToken, long activityId) {
        return call("get_activity", true, () -> api.get().uri("/activities/{id}", activityId)
                .headers(h -> h.setBearerAuth(accessToken))
                .retrieve()
                .toEntity(StravaActivity.class));
    }

    /**
     * One page of the athlete's activities that started after {@code afterEpochSeconds}, oldest pages first
     * when paging with {@code page = 1, 2, …}. A page shorter than {@code perPage} is the last one.
     */
    public List<StravaActivity> listActivities(String accessToken, long afterEpochSeconds, int page, int perPage) {
        List<StravaActivity> result = call("list_activities", true, () -> api.get()
                .uri(uri -> uri.path("/athlete/activities")
                        .queryParam("after", afterEpochSeconds)
                        .queryParam("page", page)
                        .queryParam("per_page", perPage)
                        .build())
                .headers(h -> h.setBearerAuth(accessToken))
                .retrieve()
                .toEntity(new ParameterizedTypeReference<List<StravaActivity>>() {
                }));
        return result == null ? List.of() : result;
    }

    // ------------------------------------------------------------------ Push subscriptions

    public List<StravaSubscription> listSubscriptions() {
        List<StravaSubscription> result = call("list_subscriptions", true, () -> api.get()
                .uri(uri -> uri.path("/push_subscriptions")
                        .queryParam("client_id", properties.clientId())
                        .queryParam("client_secret", properties.clientSecret())
                        .build())
                .retrieve()
                .toEntity(new ParameterizedTypeReference<List<StravaSubscription>>() {
                }));
        return result == null ? List.of() : result;
    }

    /**
     * Strava validates the callback synchronously: it sends GET callbackUrl?hub.challenge=… before answering,
     * so the URL must be publicly reachable and serve {@code GET /api/v1/strava/webhook}.
     */
    public StravaSubscription createSubscription(String callbackUrl, String verifyToken) {
        MultiValueMap<String, String> form = clientCredentials();
        form.add("callback_url", callbackUrl);
        form.add("verify_token", verifyToken);
        return call("create_subscription", false,
                () -> postForm(api, "/push_subscriptions", form, StravaSubscription.class));
    }

    public void deleteSubscription(long subscriptionId) {
        call("delete_subscription", false, () -> api.delete()
                .uri(uri -> uri.path("/push_subscriptions/{id}")
                        .queryParam("client_id", properties.clientId())
                        .queryParam("client_secret", properties.clientSecret())
                        .build(subscriptionId))
                .retrieve()
                .toBodilessEntity());
    }

    // ------------------------------------------------------------------ plumbing

    private <T> ResponseEntity<T> postForm(RestClient client, String path, MultiValueMap<String, String> form,
                                           Class<T> type) {
        return client.post().uri(path)
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(form)
                .retrieve()
                .toEntity(type);
    }

    private <T> T call(String endpoint, boolean idempotent, Supplier<ResponseEntity<T>> request) {
        int attempt = 0;
        while (true) {
            attempt++;
            rateLimiter.checkAvailable();
            try {
                ResponseEntity<T> response = request.get();
                rateLimiter.record(response.getHeaders());
                metrics.apiCall(endpoint, "success");
                return response.getBody();
            } catch (HttpStatusCodeException e) {
                rateLimiter.record(e.getResponseHeaders());
                HttpStatusCode status = e.getStatusCode();
                if (status.value() == 429) {
                    metrics.apiCall(endpoint, "rate_limited");
                    throw new StravaRateLimitedException(rateLimiter.onTooManyRequests(e.getResponseHeaders()));
                }
                if (status.is5xxServerError() && idempotent && attempt < MAX_ATTEMPTS) {
                    metrics.apiCall(endpoint, "retry");
                    log.info("Strava {} returned {}; retrying (attempt {}/{})", endpoint, status.value(), attempt,
                            MAX_ATTEMPTS);
                    backoff(attempt);
                    continue;
                }
                metrics.apiCall(endpoint, status.is4xxClientError() ? "client_error" : "server_error");
                throw e;
            } catch (ResourceAccessException e) {
                if (idempotent && attempt < MAX_ATTEMPTS) {
                    metrics.apiCall(endpoint, "retry");
                    log.info("Strava {} unreachable ({}); retrying (attempt {}/{})", endpoint, e.getMessage(),
                            attempt, MAX_ATTEMPTS);
                    backoff(attempt);
                    continue;
                }
                metrics.apiCall(endpoint, "network_error");
                throw e;
            }
        }
    }

    private void backoff(int attempt) {
        try {
            sleeper.sleep(BASE_BACKOFF.toMillis() * (1L << (attempt - 1)));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ResourceAccessException("Interrupted while retrying a Strava call");
        }
    }

    private MultiValueMap<String, String> clientCredentials() {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("client_id", properties.clientId());
        form.add("client_secret", properties.clientSecret());
        return form;
    }
}

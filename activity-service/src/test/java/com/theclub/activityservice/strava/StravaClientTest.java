package com.theclub.activityservice.strava;

import com.theclub.activityservice.MutableClock;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.support.StaticListableBeanFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.RestClient;

import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.ExpectedCount.times;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.queryParam;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestToUriTemplate;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/**
 * StravaClient against a mocked Strava (request shapes, rate-limit headers, retries).
 */
class StravaClientTest {

    static final String API = "https://www.strava.com/api/v3";
    final MutableClock clock = new MutableClock(Instant.parse("2026-10-18T10:07:30Z"), ZoneOffset.UTC);
    final List<Long> sleeps = new ArrayList<>();
    MockRestServiceServer server;
    StravaRateLimiter limiter;
    StravaClient client;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        StravaMetrics metrics = new StravaMetrics(new StaticListableBeanFactory()
                .getBeanProvider(io.micrometer.core.instrument.MeterRegistry.class));
        limiter = new StravaRateLimiter(clock, metrics);
        StravaProperties properties = new StravaProperties("12345", "the-secret", null, "verify", null, null,
                null, null, null, null, null);
        client = new StravaClient(properties, builder, limiter, metrics, sleeps::add);
    }

    private static HttpHeaders rate(String usage) {
        HttpHeaders headers = new HttpHeaders();
        headers.add("X-RateLimit-Limit", "200,2000");
        headers.add("X-RateLimit-Usage", usage);
        return headers;
    }

    @Test
    void listActivitiesSendsPagingParametersAndRecordsUsage() {
        server.expect(once(), requestTo(org.hamcrest.Matchers.startsWith(API + "/athlete/activities")))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer tok"))
                .andExpect(queryParam("after", "1760000000"))
                .andExpect(queryParam("page", "2"))
                .andExpect(queryParam("per_page", "100"))
                .andRespond(withSuccess("[" + StravaTestData.activityJson(1, 42, "2026-10-01T06:00:00Z") + "]",
                        MediaType.APPLICATION_JSON).headers(rate("185,900")));

        List<StravaActivity> page = client.listActivities("tok", 1760000000L, 2, 100);

        assertThat(page).hasSize(1);
        assertThat(page.get(0).timezone()).isEqualTo("(GMT+05:30) Asia/Kolkata");
        assertThat(limiter.isPaused()).as("185/200 is above the 90% threshold").isTrue();
        assertThatThrownBy(() -> client.getActivity("tok", 1)).isInstanceOf(StravaRateLimitedException.class);
        server.verify();
    }

    @Test
    void tooManyRequestsBecomesRateLimitedAndPausesCalls() {
        server.expect(once(), requestToUriTemplate(API + "/activities/{id}", 5))
                .andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS).headers(rate("201,700")));

        assertThatThrownBy(() -> client.getActivity("tok", 5))
                .isInstanceOfSatisfying(StravaRateLimitedException.class,
                        e -> assertThat(e.resumeAt()).isEqualTo(Instant.parse("2026-10-18T10:15:00Z")));
        assertThat(limiter.isPaused()).isTrue();
        server.verify();
    }

    @Test
    void idempotentGetsAreRetriedOnServerErrors() {
        server.expect(times(2), requestToUriTemplate(API + "/activities/{id}", 7))
                .andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE));
        server.expect(once(), requestToUriTemplate(API + "/activities/{id}", 7))
                .andRespond(withSuccess(StravaTestData.activityJson(7, 42, "2026-10-01T06:00:00Z"),
                        MediaType.APPLICATION_JSON));

        assertThat(client.getActivity("tok", 7).id()).isEqualTo(7);
        assertThat(sleeps).containsExactly(500L, 1000L);
        server.verify();
    }

    @Test
    void retriesGiveUpAfterThreeAttempts() {
        server.expect(times(3), requestToUriTemplate(API + "/activities/{id}", 7))
                .andRespond(withStatus(HttpStatus.BAD_GATEWAY));

        assertThatThrownBy(() -> client.getActivity("tok", 7)).isInstanceOf(HttpServerErrorException.class);
        server.verify();
    }

    @Test
    void clientErrorsAndNonIdempotentCallsAreNotRetried() {
        server.expect(once(), requestToUriTemplate(API + "/activities/{id}", 8))
                .andRespond(withStatus(HttpStatus.NOT_FOUND));
        server.expect(once(), requestTo("https://www.strava.com/oauth/token"))
                .andRespond(withStatus(HttpStatus.INTERNAL_SERVER_ERROR));

        assertThatThrownBy(() -> client.getActivity("tok", 8)).isInstanceOf(HttpClientErrorException.NotFound.class);
        assertThatThrownBy(() -> client.refresh("r")).isInstanceOf(HttpServerErrorException.class);
        assertThat(sleeps).isEmpty();
        server.verify();
    }

    @Test
    void refreshPostsTheRefreshGrant() {
        server.expect(once(), requestTo("https://www.strava.com/oauth/token"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().formDataContains(java.util.Map.of("client_id", "12345",
                        "client_secret", "the-secret", "grant_type", "refresh_token", "refresh_token", "old-refresh")))
                .andRespond(withSuccess("""
                        {"access_token":"new-access","refresh_token":"new-refresh","expires_at":1760021600}""",
                        MediaType.APPLICATION_JSON));

        StravaTokenResponse response = client.refresh("old-refresh");

        assertThat(response.refreshToken()).isEqualTo("new-refresh");
        assertThat(response.toString()).doesNotContain("new-access").doesNotContain("new-refresh");
    }

    @Test
    void subscriptionCalls() {
        server.expect(once(), requestTo(API + "/push_subscriptions?client_id=12345&client_secret=the-secret"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("""
                        [{"id":120475,"callback_url":"https://club.example.com/api/v1/strava/webhook",
                          "created_at":"2026-10-18T10:00:00Z","updated_at":"2026-10-18T10:00:00Z"}]""",
                        MediaType.APPLICATION_JSON));
        server.expect(once(), requestTo(API + "/push_subscriptions"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().formDataContains(java.util.Map.of("client_id", "12345",
                        "client_secret", "the-secret", "callback_url", "https://club.example.com/api/v1/strava/webhook",
                        "verify_token", "verify")))
                .andRespond(withSuccess("{\"id\":120476}", MediaType.APPLICATION_JSON));
        server.expect(once(), requestTo(API + "/push_subscriptions/120476?client_id=12345&client_secret=the-secret"))
                .andExpect(method(HttpMethod.DELETE))
                .andRespond(withStatus(HttpStatus.NO_CONTENT));

        assertThat(client.listSubscriptions()).extracting(StravaSubscription::id).containsExactly(120475L);
        assertThat(client.createSubscription("https://club.example.com/api/v1/strava/webhook", "verify").id())
                .isEqualTo(120476L);
        client.deleteSubscription(120476L);
        server.verify();
    }
}

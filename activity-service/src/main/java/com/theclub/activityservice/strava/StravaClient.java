package com.theclub.activityservice.strava;

import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;

import java.util.List;

/**
 * Thin HTTP client for the Strava OAuth and REST APIs.
 */
@Component
public class StravaClient {

    private final StravaProperties properties;
    private final RestClient oauth;
    private final RestClient api;

    public StravaClient(StravaProperties properties, RestClient.Builder builder) {
        this.properties = properties;
        this.oauth = builder.clone().baseUrl(properties.oauthBaseUrl()).build();
        this.api = builder.clone().baseUrl(properties.apiBaseUrl()).build();
    }

    public StravaTokenResponse exchangeCode(String code) {
        MultiValueMap<String, String> form = clientCredentials();
        form.add("code", code);
        form.add("grant_type", "authorization_code");
        return postToken(form);
    }

    public StravaTokenResponse refresh(String refreshToken) {
        MultiValueMap<String, String> form = clientCredentials();
        form.add("refresh_token", refreshToken);
        form.add("grant_type", "refresh_token");
        return postToken(form);
    }

    public void deauthorize(String accessToken) {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("access_token", accessToken);
        oauth.post().uri("/deauthorize")
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(form)
                .retrieve()
                .toBodilessEntity();
    }

    public StravaActivity getActivity(String accessToken, long activityId) {
        return api.get().uri("/activities/{id}", activityId)
                .headers(h -> h.setBearerAuth(accessToken))
                .retrieve()
                .body(StravaActivity.class);
    }

    public List<StravaActivity> listActivities(String accessToken, long afterEpochSeconds, int perPage) {
        List<StravaActivity> result = api.get()
                .uri(uri -> uri.path("/athlete/activities")
                        .queryParam("after", afterEpochSeconds)
                        .queryParam("per_page", perPage)
                        .build())
                .headers(h -> h.setBearerAuth(accessToken))
                .retrieve()
                .body(new ParameterizedTypeReference<List<StravaActivity>>() {
                });
        return result == null ? List.of() : result;
    }

    private StravaTokenResponse postToken(MultiValueMap<String, String> form) {
        return oauth.post().uri("/token")
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(form)
                .retrieve()
                .body(StravaTokenResponse.class);
    }

    private MultiValueMap<String, String> clientCredentials() {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("client_id", properties.clientId());
        form.add("client_secret", properties.clientSecret());
        return form;
    }
}

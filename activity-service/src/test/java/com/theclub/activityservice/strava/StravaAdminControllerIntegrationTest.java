package com.theclub.activityservice.strava;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.TestPropertySource;
import org.springframework.web.client.HttpClientErrorException;

import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@TestPropertySource(properties = "theclub.strava.webhook-callback-url=https://club.example.com/api/v1/strava/webhook")
class StravaAdminControllerIntegrationTest extends StravaIntegrationTestBase {

    static final String PATH = "/api/v1/strava/admin/subscription";
    static final String CALLBACK = "https://club.example.com/api/v1/strava/webhook";

    @Test
    void onlyAdminsManageTheSubscription() throws Exception {
        String user = token(UUID.randomUUID(), "USER");
        mvc.perform(get(PATH).header("Authorization", user)).andExpect(status().isForbidden());
        mvc.perform(post(PATH).header("Authorization", user)).andExpect(status().isForbidden());
        mvc.perform(delete(PATH + "/1").header("Authorization", user)).andExpect(status().isForbidden());
        mvc.perform(get(PATH)).andExpect(status().isUnauthorized());
    }

    @Test
    void adminCreatesListsAndDeletesTheSubscription() throws Exception {
        String admin = token(UUID.randomUUID(), "USER", "ADMIN");
        when(stravaClient.listSubscriptions()).thenReturn(List.of());
        when(stravaClient.createSubscription(CALLBACK, "test-verify-token"))
                .thenReturn(new StravaSubscription(120476, CALLBACK, null, null));

        mvc.perform(post(PATH).header("Authorization", admin))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(120476));
        verify(stravaClient).createSubscription(CALLBACK, "test-verify-token");

        when(stravaClient.listSubscriptions()).thenReturn(List.of(new StravaSubscription(120476, CALLBACK, null, null)));
        mvc.perform(get(PATH).header("Authorization", admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.subscriptions[0].id").value(120476))
                .andExpect(jsonPath("$.expectedCallbackUrl").value(CALLBACK));

        mvc.perform(delete(PATH + "/120476").header("Authorization", admin)).andExpect(status().isNoContent());
        verify(stravaClient).deleteSubscription(120476L);
    }

    @Test
    void secondSubscriptionIsRefusedAndStravaValidationErrorsAreReported() throws Exception {
        String admin = token(UUID.randomUUID(), "USER", "ADMIN");
        when(stravaClient.listSubscriptions()).thenReturn(List.of(new StravaSubscription(1, CALLBACK, null, null)));
        mvc.perform(post(PATH).header("Authorization", admin)).andExpect(status().isConflict());
        verify(stravaClient, never()).createSubscription(anyString(), anyString());

        when(stravaClient.listSubscriptions()).thenReturn(List.of());
        when(stravaClient.createSubscription(anyString(), anyString())).thenThrow(HttpClientErrorException.create(
                HttpStatus.BAD_REQUEST, "Bad Request", null,
                "{\"message\":\"Bad Request\",\"errors\":[{\"field\":\"callback url\",\"code\":\"GET to callback URL does not return 200\"}]}"
                        .getBytes(), null));
        mvc.perform(post(PATH).header("Authorization", admin))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value(org.hamcrest.Matchers.containsString("does not return 200")));
    }
}

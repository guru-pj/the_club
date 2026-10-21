package com.theclub.clubservice.controller;

import com.theclub.clubservice.client.ActivityClient;
import com.theclub.clubservice.client.EventClient;
import com.theclub.clubservice.client.NotificationClient;
import com.theclub.clubservice.client.UserClient;
import com.theclub.common.media.MediaStore;
import com.theclub.common.security.JwtService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The live stream on a real server: it ends cleanly when it times out (the async dispatch that completes it must
 * not be rejected by security), so clients simply reconnect.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = "theclub.chat.stream-timeout=PT1S")
@ActiveProfiles("test")
@ExtendWith(OutputCaptureExtension.class)
class ChatStreamServerTest {

    @LocalServerPort
    int port;
    @Autowired
    JwtService jwtService;

    @MockitoBean
    UserClient userClient;
    @MockitoBean
    ActivityClient activityClient;
    @MockitoBean
    EventClient eventClient;
    @MockitoBean
    NotificationClient notificationClient;
    @MockitoBean
    MediaStore media;

    @Test
    void theStreamEndsCleanlyWhenItTimesOut(CapturedOutput output) throws Exception {
        UUID me = UUID.randomUUID();
        String token = jwtService.issueAccessToken(me, me + "@example.com", "Me", List.of("USER"));
        HttpClient http = HttpClient.newHttpClient();
        HttpResponse<String> response = http.send(HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/v1/chat/stream"))
                .header("Authorization", "Bearer " + token).timeout(Duration.ofSeconds(10)).GET().build(),
                HttpResponse.BodyHandlers.ofString());

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.headers().firstValue("Content-Type")).hasValueSatisfying(v -> assertThat(v).startsWith("text/event-stream"));
        assertThat(response.body()).contains("event:ready");
        assertThat(output.getAll()).doesNotContain("response is already committed").doesNotContain("AccessDenied");
    }
}

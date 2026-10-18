package com.theclub.activityservice.strava;

import com.theclub.activityservice.dto.ActivityResponse;
import com.theclub.activityservice.dto.PageResponse;
import com.theclub.activityservice.model.ActivitySource;
import com.theclub.activityservice.model.StravaConnection;
import com.theclub.activityservice.service.ActivityService;
import com.theclub.common.security.AuthenticatedUser;
import com.theclub.common.web.ApiException;
import com.fasterxml.jackson.annotation.JsonInclude;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/strava")
public class StravaController {

    private final StravaService stravaService;
    private final StravaWebhookProcessor webhookProcessor;
    private final ActivityService activityService;

    public StravaController(StravaService stravaService, StravaWebhookProcessor webhookProcessor,
                            ActivityService activityService) {
        this.stravaService = stravaService;
        this.webhookProcessor = webhookProcessor;
        this.activityService = activityService;
    }

    @GetMapping("/connect")
    public Map<String, String> connect(@AuthenticationPrincipal AuthenticatedUser caller) {
        return Map.of("authorizeUrl", stravaService.authorizeUrl(caller.id()));
    }

    /** Public: Strava redirects the browser here; the signed state identifies the user. */
    @GetMapping("/callback")
    public ConnectionStatus callback(@RequestParam(required = false) String code,
                                     @RequestParam(required = false) String state,
                                     @RequestParam(required = false) String scope,
                                     @RequestParam(required = false) String error) {
        return ConnectionStatus.of(stravaService.completeAuthorization(code, state, scope, error));
    }

    @GetMapping("/connection")
    public ConnectionStatus connection(@AuthenticationPrincipal AuthenticatedUser caller) {
        return stravaService.connection(caller.id()).map(ConnectionStatus::of)
                .orElse(new ConnectionStatus(false, null, null));
    }

    @DeleteMapping("/connection")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void disconnect(@AuthenticationPrincipal AuthenticatedUser caller) {
        stravaService.disconnect(caller.id());
    }

    @PostMapping("/sync")
    public StravaService.SyncResult sync(@AuthenticationPrincipal AuthenticatedUser caller) {
        return stravaService.sync(caller.id());
    }

    @GetMapping("/activities/{userId}")
    public PageResponse<ActivityResponse> activities(@PathVariable UUID userId,
                                                     @AuthenticationPrincipal AuthenticatedUser caller,
                                                     @RequestParam(defaultValue = "0") int page,
                                                     @RequestParam(defaultValue = "20") int size) {
        if (!caller.id().equals(userId) && !caller.isAdmin()) {
            throw ApiException.forbidden("You can only view your own Strava activities");
        }
        return PageResponse.of(activityService.list(userId, ActivitySource.STRAVA, null, null, page, size),
                ActivityResponse::from);
    }

    /** Public: Strava's subscription validation handshake. */
    @GetMapping("/webhook")
    public Map<String, String> verifyWebhook(@RequestParam(name = "hub.mode", required = false) String mode,
                                             @RequestParam(name = "hub.verify_token", required = false) String token,
                                             @RequestParam(name = "hub.challenge", required = false) String challenge) {
        if (!"subscribe".equals(mode) || challenge == null || !stravaService.verifyWebhookToken(token)) {
            throw ApiException.forbidden("Webhook verification failed");
        }
        return Map.of("hub.challenge", challenge);
    }

    /** Public: Strava push events. Acknowledged immediately, processed asynchronously. */
    @PostMapping("/webhook")
    public void receiveWebhook(@RequestBody StravaWebhookEvent event) {
        webhookProcessor.processAsync(event);
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record ConnectionStatus(boolean connected, Long athleteId, LocalDateTime connectedAt) {

        static ConnectionStatus of(StravaConnection connection) {
            return new ConnectionStatus(true, connection.getAthleteId(), connection.getCreatedAt());
        }
    }
}

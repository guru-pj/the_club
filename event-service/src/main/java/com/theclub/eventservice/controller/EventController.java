package com.theclub.eventservice.controller;

import com.theclub.common.security.AuthenticatedUser;
import com.theclub.common.web.ApiException;
import com.theclub.eventservice.dto.CheckInRequest;
import com.theclub.eventservice.dto.CheckInResponse;
import com.theclub.eventservice.dto.CreateEventRequest;
import com.theclub.eventservice.dto.EventRegistrationRow;
import com.theclub.eventservice.dto.EventCard;
import com.theclub.eventservice.dto.EventResponse;
import com.theclub.eventservice.dto.EventStats;
import com.theclub.eventservice.dto.PagedResponse;
import com.theclub.eventservice.dto.RegisterResponse;
import com.theclub.eventservice.dto.RegistrationWindowRequest;
import com.theclub.eventservice.dto.TicketResponse;
import com.theclub.eventservice.dto.UpdateEventRequest;
import com.theclub.eventservice.dto.UserEventRegistration;
import com.theclub.eventservice.service.CheckInService;
import com.theclub.eventservice.service.EventService;
import com.theclub.eventservice.service.EventStatsService;
import com.theclub.eventservice.service.FeedService;
import com.theclub.eventservice.service.RegistrationService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/events")
public class EventController {

    private final EventService eventService;
    private final RegistrationService registrationService;
    private final CheckInService checkInService;
    private final FeedService feedService;
    private final EventStatsService statsService;

    public EventController(EventService eventService, RegistrationService registrationService,
                           CheckInService checkInService, FeedService feedService, EventStatsService statsService) {
        this.statsService = statsService;
        this.eventService = eventService;
        this.registrationService = registrationService;
        this.checkInService = checkInService;
        this.feedService = feedService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public EventResponse create(@Valid @RequestBody CreateEventRequest request,
                                @AuthenticationPrincipal AuthenticatedUser caller) {
        return eventService.create(request, caller);
    }

    @GetMapping("/{eventId}")
    public EventResponse get(@PathVariable UUID eventId, @AuthenticationPrincipal AuthenticatedUser caller) {
        return eventService.get(eventId, caller);
    }

    @PutMapping("/{eventId}")
    public EventResponse update(@PathVariable UUID eventId, @Valid @RequestBody UpdateEventRequest request,
                                @AuthenticationPrincipal AuthenticatedUser caller) {
        return eventService.update(eventId, request, caller);
    }

    /** Multipart upload (field "file"): JPEG, PNG or WebP. */
    @PostMapping(value = "/{eventId}/image", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public EventResponse uploadImage(@PathVariable UUID eventId, @RequestParam("file") MultipartFile file,
                                     @AuthenticationPrincipal AuthenticatedUser caller) {
        return eventService.uploadImage(eventId, file, caller);
    }

    @DeleteMapping("/{eventId}/image")
    public EventResponse removeImage(@PathVariable UUID eventId, @AuthenticationPrincipal AuthenticatedUser caller) {
        return eventService.removeImage(eventId, caller);
    }

    /**
     * Upcoming events from clubs the caller follows or subscribes to, plus (with lat/lng) clubs within
     * radiusKm (default 25), soonest first.
     */
    @GetMapping("/upcoming")
    public List<EventCard> upcoming(@RequestParam(required = false) Double lat,
                                    @RequestParam(required = false) Double lng,
                                    @RequestParam(required = false) Double radiusKm,
                                    @RequestParam(required = false) Integer limit,
                                    @AuthenticationPrincipal AuthenticatedUser caller) {
        return feedService.upcoming(caller, lat, lng, radiusKm, limit);
    }

    @PatchMapping("/{eventId}/registration-window")
    public EventResponse updateRegistrationWindow(@PathVariable UUID eventId,
                                                  @Valid @RequestBody RegistrationWindowRequest request,
                                                  @AuthenticationPrincipal AuthenticatedUser caller) {
        return eventService.updateRegistrationWindow(eventId, request, caller);
    }

    /** Publishes a draft: it becomes visible, and registration opens within its window. */
    @PostMapping("/{eventId}/publish")
    public EventResponse publish(@PathVariable UUID eventId, @AuthenticationPrincipal AuthenticatedUser caller) {
        return eventService.publish(eventId, caller);
    }

    /** Marks a finished event completed; it moves to the club's past events. */
    @PostMapping("/{eventId}/complete")
    public EventResponse complete(@PathVariable UUID eventId, @AuthenticationPrincipal AuthenticatedUser caller) {
        return eventService.complete(eventId, caller);
    }

    /** Club owner: registrations, check-ins, attendance and revenue of one event. */
    @GetMapping("/{eventId}/stats")
    public EventStats.Event stats(@PathVariable UUID eventId, @AuthenticationPrincipal AuthenticatedUser caller) {
        return statsService.event(eventId, caller);
    }

    /** Club owner: the same for all of the club's events, plus the most recent ones. */
    @GetMapping("/club/{clubId}/stats")
    public EventStats.Club clubStats(@PathVariable UUID clubId, @AuthenticationPrincipal AuthenticatedUser caller) {
        return statsService.club(clubId, caller);
    }

    @PostMapping("/{eventId}/cancel")
    public EventResponse cancel(@PathVariable UUID eventId, @AuthenticationPrincipal AuthenticatedUser caller) {
        return eventService.cancel(eventId, caller);
    }

    @GetMapping("/club/{clubId}")
    public PagedResponse<EventResponse> byClub(@PathVariable UUID clubId,
                                               @RequestParam(defaultValue = "true") boolean upcoming,
                                               @RequestParam(defaultValue = "0") int page,
                                               @RequestParam(defaultValue = "20") int size,
                                               @AuthenticationPrincipal AuthenticatedUser caller) {
        return eventService.listByClub(clubId, upcoming, page, size, caller);
    }

    @GetMapping("/user/{userId}")
    public List<UserEventRegistration> byUser(@PathVariable UUID userId,
                                              @AuthenticationPrincipal AuthenticatedUser caller) {
        if (!caller.id().equals(userId) && !caller.isAdmin()) {
            throw ApiException.forbidden("You can only view your own registrations");
        }
        return eventService.registrationsOf(userId);
    }

    @GetMapping("/me/registrations")
    public List<UserEventRegistration> myRegistrations(@AuthenticationPrincipal AuthenticatedUser caller) {
        return eventService.registrationsOf(caller.id());
    }

    @PostMapping("/{eventId}/register")
    @ResponseStatus(HttpStatus.CREATED)
    public RegisterResponse register(@PathVariable UUID eventId, @AuthenticationPrincipal AuthenticatedUser caller) {
        return registrationService.register(eventId, caller);
    }

    @DeleteMapping("/{eventId}/registrations/me")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void cancelMyRegistration(@PathVariable UUID eventId, @AuthenticationPrincipal AuthenticatedUser caller) {
        registrationService.cancelOwn(eventId, caller);
    }

    @GetMapping("/{eventId}/registrations")
    public List<EventRegistrationRow> registrations(@PathVariable UUID eventId,
                                                    @AuthenticationPrincipal AuthenticatedUser caller) {
        return checkInService.registrations(eventId, caller);
    }

    @GetMapping("/registrations/{registrationId}/ticket")
    public TicketResponse ticket(@PathVariable UUID registrationId, @AuthenticationPrincipal AuthenticatedUser caller) {
        return registrationService.ticket(registrationId, caller);
    }

    @GetMapping(value = "/registrations/{registrationId}/ticket.png", produces = MediaType.IMAGE_PNG_VALUE)
    public byte[] ticketPng(@PathVariable UUID registrationId, @AuthenticationPrincipal AuthenticatedUser caller) {
        return registrationService.ticketPng(registrationId, caller);
    }

    @PostMapping("/{eventId}/check-in")
    public CheckInResponse checkIn(@PathVariable UUID eventId, @Valid @RequestBody CheckInRequest request,
                                   @AuthenticationPrincipal AuthenticatedUser caller) {
        return checkInService.checkIn(eventId, request.ticketCode(), caller);
    }
}

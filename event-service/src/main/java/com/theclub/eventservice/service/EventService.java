package com.theclub.eventservice.service;

import com.theclub.common.media.MediaStore;
import com.theclub.common.security.AuthenticatedUser;
import com.theclub.common.web.ApiException;
import com.theclub.eventservice.client.ClubClient.ClubInfo;
import com.theclub.eventservice.dto.CreateEventRequest;
import com.theclub.eventservice.dto.EventResponse;
import com.theclub.eventservice.dto.PagedResponse;
import com.theclub.eventservice.dto.RegistrationWindowRequest;
import com.theclub.eventservice.dto.UpdateEventRequest;
import com.theclub.eventservice.dto.UserEventRegistration;
import com.theclub.eventservice.model.Event;
import com.theclub.eventservice.model.EventStatus;
import com.theclub.eventservice.model.Registration;
import com.theclub.eventservice.model.RegistrationStatus;
import com.theclub.eventservice.repository.EventRepository;
import com.theclub.eventservice.repository.RegistrationRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class EventService {

    static final Set<RegistrationStatus> ATTENDING = EnumSet.of(RegistrationStatus.CONFIRMED, RegistrationStatus.CHECKED_IN);
    private static final UUID NOBODY = new UUID(0, 0);
    private static final int MAX_PAGE_SIZE = 100;

    private final EventRepository events;
    private final RegistrationRepository registrations;
    private final ClubAccess clubAccess;
    private final ConfirmationNotifier notifier;
    private final PaymentService paymentService;
    private final MediaStore media;
    private final Clock clock;

    public EventService(EventRepository events, RegistrationRepository registrations, ClubAccess clubAccess,
                        ConfirmationNotifier notifier, PaymentService paymentService, MediaStore media, Clock clock) {
        this.events = events;
        this.registrations = registrations;
        this.clubAccess = clubAccess;
        this.notifier = notifier;
        this.paymentService = paymentService;
        this.media = media;
        this.clock = clock;
    }

    @Transactional
    public EventResponse create(CreateEventRequest request, AuthenticatedUser caller) {
        ClubInfo club = clubAccess.requireOwnerOrAdmin(request.clubId(), caller);
        LocalDateTime now = LocalDateTime.now(clock);
        Event event = new Event(request.clubId(), caller.id());
        event.setTitle(request.title().trim());
        event.setDescription(request.description());
        event.setLocation(request.location());
        event.setStartTime(request.startTime());
        event.setRegistrationOpensAt(request.registrationOpensAt() != null ? request.registrationOpensAt() : now);
        event.setRegistrationClosesAt(request.registrationClosesAt());
        event.setCapacity(request.capacity());
        if (request.price() != null) {
            event.setPrice(request.price());
        }
        if (request.currency() != null) {
            event.setCurrency(request.currency());
        }
        if (request.points() != null) {
            event.setPoints(request.points());
        }
        event.setRules(blankToNull(request.rules()));
        event.setSubscribersOnly(Boolean.TRUE.equals(request.subscribersOnly()));
        EventRules.validate(event, now, true);
        boolean publish = !Boolean.FALSE.equals(request.publish());
        event.setStatus(publish ? EventStatus.SCHEDULED : EventStatus.DRAFT);
        Event saved = events.save(event);
        if (publish) {
            notifier.eventAnnounced(saved, club.name(), caller.id());
        }
        return view(saved, caller.id());
    }

    /** Makes a draft visible and open for registration (within its window), and tells the club's audience. */
    @Transactional
    public EventResponse publish(UUID eventId, AuthenticatedUser caller) {
        Event event = find(eventId);
        ClubInfo club = clubAccess.requireOwnerOrAdmin(event.getClubId(), caller);
        if (event.getStatus() != EventStatus.DRAFT) {
            throw ApiException.conflict("Only a draft can be published; this event is " + event.getStatus())
                    .withCode("NOT_A_DRAFT");
        }
        EventRules.validate(event, LocalDateTime.now(clock), true);
        event.setStatus(EventStatus.SCHEDULED);
        notifier.eventAnnounced(event, club.name(), caller.id());
        return view(event, caller.id());
    }

    /** After the event: it leaves upcoming listings and stays in the club's past events with its registrations. */
    @Transactional
    public EventResponse complete(UUID eventId, AuthenticatedUser caller) {
        Event event = find(eventId);
        clubAccess.requireOwnerOrAdmin(event.getClubId(), caller);
        if (event.getStatus() != EventStatus.SCHEDULED) {
            throw ApiException.conflict("Only a published event can be completed; this event is " + event.getStatus())
                    .withCode("NOT_SCHEDULED");
        }
        if (LocalDateTime.now(clock).isBefore(event.getStartTime())) {
            throw ApiException.conflict("The event hasn't started yet").withCode("NOT_STARTED");
        }
        event.setStatus(EventStatus.COMPLETED);
        return view(event, caller.id());
    }

    @Transactional
    public EventResponse update(UUID eventId, UpdateEventRequest request, AuthenticatedUser caller) {
        Event event = requireManageable(eventId, caller);
        LocalDateTime now = LocalDateTime.now(clock);
        boolean startChanged = request.startTime() != null && !request.startTime().equals(event.getStartTime());
        if (request.title() != null) {
            event.setTitle(request.title().trim());
        }
        if (request.description() != null) {
            event.setDescription(request.description());
        }
        if (request.location() != null) {
            event.setLocation(request.location());
        }
        if (request.startTime() != null) {
            event.setStartTime(request.startTime());
        }
        if (request.registrationOpensAt() != null) {
            event.setRegistrationOpensAt(request.registrationOpensAt());
        }
        if (request.registrationClosesAt() != null) {
            event.setRegistrationClosesAt(request.registrationClosesAt());
        }
        if (request.capacity() != null) {
            long taken = spotsTaken(event.getId(), now);
            if (request.capacity() < taken) {
                throw ApiException.conflict("capacity cannot be lower than the " + taken + " current registrations");
            }
            event.setCapacity(request.capacity());
        }
        if (request.price() != null) {
            event.setPrice(request.price());
        }
        if (request.currency() != null) {
            event.setCurrency(request.currency());
        }
        if (request.points() != null) {
            event.setPoints(request.points());
        }
        if (request.rules() != null) {
            event.setRules(blankToNull(request.rules()));
        }
        if (request.subscribersOnly() != null) {
            event.setSubscribersOnly(request.subscribersOnly());
        }
        EventRules.validate(event, now, startChanged);
        return view(event, caller.id());
    }

    @Transactional
    public EventResponse updateRegistrationWindow(UUID eventId, RegistrationWindowRequest request,
                                                  AuthenticatedUser caller) {
        Event event = requireManageable(eventId, caller);
        event.setRegistrationOpensAt(request.registrationOpensAt());
        event.setRegistrationClosesAt(request.registrationClosesAt());
        EventRules.validate(event, LocalDateTime.now(clock), false);
        return view(event, caller.id());
    }

    @Transactional
    public EventResponse cancel(UUID eventId, AuthenticatedUser caller) {
        Event event = requireManageable(eventId, caller);
        event.setStatus(EventStatus.CANCELLED);
        List<UUID> affected = registrations.findByEventIdAndStatusIn(eventId,
                        EnumSet.of(RegistrationStatus.CONFIRMED, RegistrationStatus.PENDING_PAYMENT))
                .stream().map(Registration::getUserId).toList();
        notifier.eventCancelled(event, affected);
        if (!event.isFree()) {
            paymentService.refundAllForCancelledEvent(event);
        }
        return view(event, caller.id());
    }

    @Transactional(readOnly = true)
    public EventResponse get(UUID eventId, AuthenticatedUser caller) {
        return view(findVisible(eventId, caller), caller.id());
    }

    /** Drafts exist only for the club owner and admins; everyone else gets a 404. */
    @Transactional(readOnly = true)
    public Event findVisible(UUID eventId, AuthenticatedUser caller) {
        Event event = find(eventId);
        if (event.getStatus() == EventStatus.DRAFT && !clubAccess.isOwnerOrAdmin(event.getClubId(), caller)) {
            throw ApiException.notFound("Event not found");
        }
        return event;
    }

    @Transactional(readOnly = true)
    public Event find(UUID eventId) {
        return events.findById(eventId).orElseThrow(() -> ApiException.notFound("Event not found"));
    }

    @Transactional(readOnly = true)
    public PagedResponse<EventResponse> listByClub(UUID clubId, boolean upcoming, int page, int size,
                                                   AuthenticatedUser caller) {
        int pageSize = Math.clamp(size, 1, MAX_PAGE_SIZE);
        PageRequest pageable = PageRequest.of(Math.max(page, 0), pageSize,
                upcoming ? Sort.by("startTime").ascending() : Sort.by("startTime").descending());
        boolean withDrafts = clubAccess.isOwnerOrAdmin(clubId, caller);
        LocalDateTime now = LocalDateTime.now(clock);
        Page<Event> result;
        if (withDrafts) {
            result = upcoming ? events.findByClubIdAndStartTimeGreaterThanEqual(clubId, now, pageable)
                    : events.findByClubId(clubId, pageable);
        } else {
            result = upcoming
                    ? events.findByClubIdAndStatusNotAndStartTimeGreaterThanEqual(clubId, EventStatus.DRAFT, now, pageable)
                    : events.findByClubIdAndStatusNot(clubId, EventStatus.DRAFT, pageable);
        }
        List<EventResponse> items = result.getContent().stream().map(e -> view(e, caller.id())).toList();
        return new PagedResponse<>(items, pageable.getPageNumber(), pageSize, result.getTotalElements());
    }

    @Transactional(readOnly = true)
    public List<UserEventRegistration> registrationsOf(UUID userId) {
        List<Registration> mine = registrations.findByUserIdOrderByRegisteredAtDesc(userId);
        Map<UUID, Event> byId = events.findAllById(mine.stream().map(Registration::getEventId).toList()).stream()
                .collect(Collectors.toMap(Event::getId, Function.identity()));
        return mine.stream()
                .filter(r -> byId.containsKey(r.getEventId()))
                .map(r -> {
                    Event e = byId.get(r.getEventId());
                    return new UserEventRegistration(r.getId(), r.getStatus(), r.getRegisteredAt(), r.getCheckedInAt(),
                            e.getId(), e.getClubId(), e.getTitle(), e.getStartTime(), e.getLocation(), e.getStatus());
                })
                .toList();
    }

    private Event requireManageable(UUID eventId, AuthenticatedUser caller) {
        Event event = find(eventId);
        clubAccess.requireOwnerOrAdmin(event.getClubId(), caller);
        if (event.getStatus() == EventStatus.CANCELLED) {
            throw ApiException.conflict("Event is cancelled");
        }
        if (event.getStatus() == EventStatus.COMPLETED) {
            throw ApiException.conflict("Event is completed").withCode("EVENT_COMPLETED");
        }
        return event;
    }

    private long spotsTaken(UUID eventId, LocalDateTime now) {
        return registrations.countSpotsTaken(eventId, NOBODY, now.minus(paymentService.pendingHold()));
    }

    /** Stores an uploaded event image and replaces the previous one. */
    @Transactional
    public EventResponse uploadImage(UUID eventId, MultipartFile file, AuthenticatedUser caller) {
        Event event = requireManageable(eventId, caller);
        String previous = event.getImageUrl();
        event.setImageUrl(media.storeImage("events/" + eventId, file).url());
        media.deleteByUrl(previous);
        return view(event, caller.id());
    }

    @Transactional
    public EventResponse removeImage(UUID eventId, AuthenticatedUser caller) {
        Event event = requireManageable(eventId, caller);
        media.deleteByUrl(event.getImageUrl());
        event.setImageUrl(null);
        return view(event, caller.id());
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }

    EventResponse view(Event event, UUID callerId) {
        LocalDateTime now = LocalDateTime.now(clock);
        long registered = registrations.countByEventIdAndStatusIn(event.getId(), ATTENDING);
        RegistrationStatus mine = registrations.findByEventIdAndUserId(event.getId(), callerId)
                .map(Registration::getStatus).orElse(null);
        return EventResponse.of(event, registered, spotsTaken(event.getId(), now), now, mine);
    }
}

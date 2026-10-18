package com.theclub.eventservice.service;

import com.theclub.common.security.AuthenticatedUser;
import com.theclub.common.web.ApiException;
import com.theclub.eventservice.dto.CheckoutInfo;
import com.theclub.eventservice.dto.RegisterResponse;
import com.theclub.eventservice.dto.TicketResponse;
import com.theclub.eventservice.model.Event;
import com.theclub.eventservice.model.EventStatus;
import com.theclub.eventservice.model.Payment;
import com.theclub.eventservice.model.Registration;
import com.theclub.eventservice.model.RegistrationStatus;
import com.theclub.eventservice.repository.EventRepository;
import com.theclub.eventservice.repository.RegistrationRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Base64;
import java.util.UUID;

@Service
public class RegistrationService {

    /** How long an unpaid registration holds a spot. */
    public static final Duration PAYMENT_HOLD = Duration.ofMinutes(15);

    private final EventRepository events;
    private final RegistrationRepository registrations;
    private final ClubAccess clubAccess;
    private final PaymentService paymentService;
    private final TicketIssuer ticketIssuer;
    private final ConfirmationNotifier notifier;
    private final QrCodeService qrCodeService;
    private final Clock clock;

    public RegistrationService(EventRepository events, RegistrationRepository registrations, ClubAccess clubAccess,
                               PaymentService paymentService, TicketIssuer ticketIssuer,
                               ConfirmationNotifier notifier, QrCodeService qrCodeService, Clock clock) {
        this.events = events;
        this.registrations = registrations;
        this.clubAccess = clubAccess;
        this.paymentService = paymentService;
        this.ticketIssuer = ticketIssuer;
        this.notifier = notifier;
        this.qrCodeService = qrCodeService;
        this.clock = clock;
    }

    @Transactional
    public RegisterResponse register(UUID eventId, AuthenticatedUser caller) {
        Event unlocked = events.findById(eventId).orElseThrow(() -> ApiException.notFound("Event not found"));
        if (!clubAccess.isMember(unlocked.getClubId(), caller.id())) {
            throw ApiException.forbidden("Join the club to register");
        }
        Event event = events.findByIdForUpdate(eventId).orElseThrow(() -> ApiException.notFound("Event not found"));
        LocalDateTime now = LocalDateTime.now(clock);
        if (event.getStatus() != EventStatus.SCHEDULED) {
            throw ApiException.conflict("Event is not open for registration");
        }
        if (now.isBefore(event.getRegistrationOpensAt())) {
            throw ApiException.conflict("Registration opens at "
                    + event.getRegistrationOpensAt().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
        }
        if (!now.isBefore(event.effectiveRegistrationClosesAt())) {
            throw ApiException.conflict("Registration is closed");
        }
        Registration registration = registrations.findByEventIdAndUserId(eventId, caller.id())
                .orElseGet(() -> new Registration(eventId, caller.id()));
        if (registration.hasTicket()) {
            throw ApiException.conflict("You are already registered for this event");
        }
        if (event.getCapacity() != null
                && registrations.countSpotsTaken(eventId, caller.id(), now.minus(PAYMENT_HOLD)) >= event.getCapacity()) {
            throw ApiException.conflict("Event is full");
        }
        registration.setRegisteredAt(now);
        registration.setTicketCode(null);
        if (event.isFree()) {
            ticketIssuer.confirm(registration, now);
            registrations.save(registration);
            notifier.registrationConfirmed(caller.id(), caller.email(), event, registration.getTicketCode());
            return new RegisterResponse(registration.getId(), registration.getStatus(), null);
        }
        registration.setStatus(RegistrationStatus.PENDING_PAYMENT);
        registrations.save(registration);
        Payment payment = paymentService.createFor(registration, event);
        CheckoutInfo checkout = CheckoutInfo.of(payment, paymentService.providerKeyId());
        return new RegisterResponse(registration.getId(), registration.getStatus(), checkout);
    }

    @Transactional
    public void cancelOwn(UUID eventId, AuthenticatedUser caller) {
        Event event = events.findById(eventId).orElseThrow(() -> ApiException.notFound("Event not found"));
        Registration registration = registrations.findByEventIdAndUserId(eventId, caller.id())
                .orElseThrow(() -> ApiException.notFound("You are not registered for this event"));
        if (!LocalDateTime.now(clock).isBefore(event.getStartTime())) {
            throw ApiException.conflict("The event has already started");
        }
        switch (registration.getStatus()) {
            case PENDING_PAYMENT -> { }
            case CONFIRMED -> {
                if (!event.isFree()) {
                    throw ApiException.conflict("Contact the club owner for refunds");
                }
            }
            case CANCELLED -> throw ApiException.conflict("Registration is already cancelled");
            case CHECKED_IN -> throw ApiException.conflict("You have already checked in");
        }
        registration.setStatus(RegistrationStatus.CANCELLED);
        registration.setTicketCode(null);
    }

    @Transactional(readOnly = true)
    public TicketResponse ticket(UUID registrationId, AuthenticatedUser caller) {
        Registration registration = ticketRegistration(registrationId, caller);
        Event event = events.findById(registration.getEventId())
                .orElseThrow(() -> ApiException.notFound("Event not found"));
        String qr = Base64.getEncoder().encodeToString(qrCodeService.ticketPng(registration.getTicketCode()));
        return new TicketResponse(registration.getId(), event.getId(), event.getTitle(), event.getStartTime(),
                event.getLocation(), registration.getStatus(), registration.getTicketCode(), qr);
    }

    @Transactional(readOnly = true)
    public byte[] ticketPng(UUID registrationId, AuthenticatedUser caller) {
        return qrCodeService.ticketPng(ticketRegistration(registrationId, caller).getTicketCode());
    }

    private Registration ticketRegistration(UUID registrationId, AuthenticatedUser caller) {
        Registration registration = registrations.findById(registrationId)
                .orElseThrow(() -> ApiException.notFound("Registration not found"));
        if (!registration.getUserId().equals(caller.id())) {
            Event event = events.findById(registration.getEventId())
                    .orElseThrow(() -> ApiException.notFound("Event not found"));
            if (!clubAccess.isOwnerOrAdmin(event.getClubId(), caller)) {
                throw ApiException.forbidden("This ticket belongs to another member");
            }
        }
        if (!registration.hasTicket()) {
            throw ApiException.conflict("No ticket: registration is " + registration.getStatus());
        }
        return registration;
    }
}

package com.theclub.eventservice.service;

import com.theclub.eventservice.model.Registration;
import com.theclub.eventservice.model.RegistrationStatus;
import org.springframework.stereotype.Component;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Base64;

/**
 * Confirms a registration and gives it an unguessable ticket code (the secret behind the QR ticket).
 */
@Component
public class TicketIssuer {

    private static final SecureRandom RANDOM = new SecureRandom();

    public void confirm(Registration registration, LocalDateTime now) {
        registration.setStatus(RegistrationStatus.CONFIRMED);
        registration.setConfirmedAt(now);
        registration.setTicketCode(newTicketCode());
    }

    /** 24 random bytes as 32 URL-safe characters. */
    static String newTicketCode() {
        byte[] bytes = new byte[24];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}

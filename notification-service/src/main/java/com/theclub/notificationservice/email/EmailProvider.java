package com.theclub.notificationservice.email;

/**
 * Delivers one rendered email. Implementations must be thread-safe.
 */
public interface EmailProvider {

    /** Short name used in logs, metrics and the outbox ("zeptomail", "smtp", "log"). */
    String name();

    /**
     * @return the provider's message/request id, if it returns one.
     * @throws EmailDeliveryException when the email was not accepted.
     */
    String send(OutgoingEmail email);
}

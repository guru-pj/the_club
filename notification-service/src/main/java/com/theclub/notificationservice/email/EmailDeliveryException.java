package com.theclub.notificationservice.email;

/**
 * A failed delivery attempt.
 *
 * @see #isRetryable()
 */
public class EmailDeliveryException extends RuntimeException {

    private final boolean retryable;

    public EmailDeliveryException(String message, boolean retryable) {
        super(message);
        this.retryable = retryable;
    }

    public EmailDeliveryException(String message, boolean retryable, Throwable cause) {
        super(message, cause);
        this.retryable = retryable;
    }

    /** True for temporary problems (network, 429, 5xx) worth retrying; false when retrying can't help. */
    public boolean isRetryable() {
        return retryable;
    }
}

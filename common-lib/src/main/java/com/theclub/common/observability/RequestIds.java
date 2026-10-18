package com.theclub.common.observability;

import org.slf4j.MDC;

import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Correlation id shared by the gateway and every service: read from / written to the {@value #HEADER} header,
 * kept in the logging MDC under {@value #MDC_KEY} and added to problem responses.
 */
public final class RequestIds {

    public static final String HEADER = "X-Request-Id";
    public static final String MDC_KEY = "requestId";

    /** Accept caller ids only if they are short and harmless in logs and headers. */
    private static final Pattern VALID = Pattern.compile("[A-Za-z0-9._:-]{1,64}");

    private RequestIds() {
    }

    public static boolean isValid(String value) {
        return value != null && VALID.matcher(value).matches();
    }

    public static String newId() {
        return UUID.randomUUID().toString();
    }

    /** The current request's id, or null outside a request. */
    public static String current() {
        return MDC.get(MDC_KEY);
    }
}

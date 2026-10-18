package com.theclub.common.web;

import org.springframework.http.HttpStatus;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Business error carrying the HTTP status it should be reported with, plus optional machine-readable
 * {@code code}, per-field validation messages and response headers (e.g. Retry-After).
 */
public class ApiException extends RuntimeException {

    private final HttpStatus status;
    private String code;
    private final Map<String, String> fieldErrors = new LinkedHashMap<>();
    private final Map<String, String> headers = new LinkedHashMap<>();

    public ApiException(HttpStatus status, String message) {
        super(message);
        this.status = status;
    }

    public HttpStatus getStatus() {
        return status;
    }

    public String getCode() {
        return code;
    }

    public Map<String, String> getFieldErrors() {
        return fieldErrors;
    }

    public Map<String, String> getHeaders() {
        return headers;
    }

    /** Machine-readable error code reported as the problem property {@code code}. */
    public ApiException withCode(String code) {
        this.code = code;
        return this;
    }

    /** Reported in the problem property {@code errors}, like bean validation failures. */
    public ApiException withFieldError(String field, String message) {
        fieldErrors.put(field, message);
        return this;
    }

    public ApiException withHeader(String name, String value) {
        headers.put(name, value);
        return this;
    }

    public static ApiException notFound(String message) {
        return new ApiException(HttpStatus.NOT_FOUND, message);
    }

    public static ApiException badRequest(String message) {
        return new ApiException(HttpStatus.BAD_REQUEST, message);
    }

    public static ApiException forbidden(String message) {
        return new ApiException(HttpStatus.FORBIDDEN, message);
    }

    public static ApiException conflict(String message) {
        return new ApiException(HttpStatus.CONFLICT, message);
    }

    public static ApiException unauthorized(String message) {
        return new ApiException(HttpStatus.UNAUTHORIZED, message);
    }

    public static ApiException badGateway(String message) {
        return new ApiException(HttpStatus.BAD_GATEWAY, message);
    }

    public static ApiException tooManyRequests(String message) {
        return new ApiException(HttpStatus.TOO_MANY_REQUESTS, message);
    }
}

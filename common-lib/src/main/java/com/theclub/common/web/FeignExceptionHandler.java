package com.theclub.common.web;

import feign.FeignException;
import feign.RetryableException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Downstream failures that a service did not handle itself surface as 502/503 instead of 500.
 * Registered only when Feign is on the classpath.
 */
@RestControllerAdvice
@Order(Ordered.LOWEST_PRECEDENCE)
public class FeignExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(FeignExceptionHandler.class);

    @ExceptionHandler(RetryableException.class)
    public ProblemDetail handleUnavailable(RetryableException ex) {
        log.warn("Downstream service unavailable: {}", ex.getMessage());
        return ProblemDetail.forStatusAndDetail(HttpStatus.SERVICE_UNAVAILABLE, "A dependent service is unavailable");
    }

    @ExceptionHandler(FeignException.class)
    public ProblemDetail handleFeign(FeignException ex) {
        log.warn("Downstream call failed with status {}: {}", ex.status(), ex.getMessage());
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_GATEWAY, "A dependent service returned an error");
    }
}

package com.theclub.common.observability;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;

import static org.assertj.core.api.Assertions.assertThat;

class RequestIdProblemDetailAdviceTest {

    private final RequestIdProblemDetailAdvice advice = new RequestIdProblemDetailAdvice();

    @AfterEach
    void clear() {
        MDC.clear();
    }

    private Object write(Object body) {
        return advice.beforeBodyWrite(body, null, null, null, null, null);
    }

    @Test
    void addsTheRequestIdToProblems() {
        MDC.put(RequestIds.MDC_KEY, "req-42");
        ProblemDetail problem = (ProblemDetail) write(ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, "nope"));

        assertThat(problem.getProperties()).containsEntry("requestId", "req-42");
    }

    @Test
    void leavesOtherBodiesAndRequestlessProblemsAlone() {
        MDC.put(RequestIds.MDC_KEY, "req-42");
        assertThat(write("plain")).isEqualTo("plain");

        MDC.clear();
        ProblemDetail problem = (ProblemDetail) write(ProblemDetail.forStatus(HttpStatus.NOT_FOUND));
        assertThat(problem.getProperties()).isNull();
    }
}

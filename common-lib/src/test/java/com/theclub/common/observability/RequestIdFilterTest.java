package com.theclub.common.observability;

import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class RequestIdFilterTest {

    private final RequestIdFilter filter = new RequestIdFilter();

    private String runAndCaptureMdc(MockHttpServletRequest request, MockHttpServletResponse response) throws Exception {
        AtomicReference<String> seen = new AtomicReference<>();
        filter.doFilter(request, response, new MockFilterChain() {
            @Override
            public void doFilter(jakarta.servlet.ServletRequest req, jakarta.servlet.ServletResponse res) {
                seen.set(MDC.get(RequestIds.MDC_KEY));
            }
        });
        return seen.get();
    }

    @Test
    void generatesAnIdWhenNoneIsSent() throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();
        String inChain = runAndCaptureMdc(new MockHttpServletRequest(), response);

        assertThat(inChain).matches("[0-9a-f-]{36}");
        assertThat(response.getHeader(RequestIds.HEADER)).isEqualTo(inChain);
    }

    @Test
    void propagatesAValidIncomingId() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(RequestIds.HEADER, "gw-1234_abc:5.6");
        MockHttpServletResponse response = new MockHttpServletResponse();

        assertThat(runAndCaptureMdc(request, response)).isEqualTo("gw-1234_abc:5.6");
        assertThat(response.getHeader(RequestIds.HEADER)).isEqualTo("gw-1234_abc:5.6");
    }

    @Test
    void replacesUnsafeIncomingIds() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(RequestIds.HEADER, "x\"}\n{\"evil\":1");
        String id = runAndCaptureMdc(request, new MockHttpServletResponse());

        assertThat(id).matches("[0-9a-f-]{36}");
    }

    @Test
    void rejectsOverlongIds() {
        assertThat(RequestIds.isValid("a".repeat(64))).isTrue();
        assertThat(RequestIds.isValid("a".repeat(65))).isFalse();
        assertThat(RequestIds.isValid(null)).isFalse();
    }

    @Test
    void clearsTheMdcAfterTheRequest() throws Exception {
        runAndCaptureMdc(new MockHttpServletRequest(), new MockHttpServletResponse());
        assertThat(MDC.get(RequestIds.MDC_KEY)).isNull();
    }
}

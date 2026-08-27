package com.luuhoa.fincore.observability;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class CorrelationIdFilterTest {

    private final CorrelationIdFilter filter = new CorrelationIdFilter(false);

    @Test
    void preservesASafeClientCorrelationIdForTheResponseAndLogContext() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/wallets");
        request.addHeader(CorrelationIdFilter.CORRELATION_ID_HEADER, "web-20260827-request-42");
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicReference<String> idVisibleInsideRequest = new AtomicReference<>();

        filter.doFilter(request, response, (ignoredRequest, ignoredResponse) ->
                idVisibleInsideRequest.set(MDC.get(CorrelationIdFilter.MDC_KEY)));

        assertThat(idVisibleInsideRequest).hasValue("web-20260827-request-42");
        assertThat(response.getHeader(CorrelationIdFilter.CORRELATION_ID_HEADER))
                .isEqualTo("web-20260827-request-42");
        assertThat(MDC.get(CorrelationIdFilter.MDC_KEY)).isNull();
    }

    @Test
    void generatesASafeCorrelationIdWhenTheHeaderIsMissingOrInvalid() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/actuator/health");
        request.addHeader(CorrelationIdFilter.CORRELATION_ID_HEADER, "too short");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, (ignoredRequest, ignoredResponse) -> {
        });

        String generatedId = response.getHeader(CorrelationIdFilter.CORRELATION_ID_HEADER);
        assertThat(generatedId).matches("[A-Za-z0-9._-]{8,100}");
        assertThat(generatedId).isNotEqualTo("too short");
        assertThat(MDC.get(CorrelationIdFilter.MDC_KEY)).isNull();
    }
}

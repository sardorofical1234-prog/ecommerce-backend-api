package com.sardor.ecommerce.config;

import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

class RequestCorrelationFilterTest {
    private final RequestCorrelationFilter filter = new RequestCorrelationFilter();

    @Test
    void preservesSafeIncomingRequestIdAndReturnsItToCaller() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setMethod("GET");
        request.setRequestURI("/api/v1/products");
        request.addHeader(RequestCorrelationFilter.HEADER_NAME, "trace-123.safe");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, new MockFilterChain());

        assertEquals("trace-123.safe", response.getHeader(RequestCorrelationFilter.HEADER_NAME));
        assertNull(MDC.get("requestId"));
    }

    @Test
    void replacesInvalidRequestIdWithGeneratedValueAndClearsMdc() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setMethod("POST");
        request.setRequestURI("/api/v1/checkout");
        request.addHeader(RequestCorrelationFilter.HEADER_NAME, "bad id\nfor-logs");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, new MockFilterChain());

        String requestId = response.getHeader(RequestCorrelationFilter.HEADER_NAME);
        assertNotNull(requestId);
        assertFalse(requestId.contains(" "));
        assertFalse(requestId.contains("\n"));
        assertNull(MDC.get("requestId"));
    }
}

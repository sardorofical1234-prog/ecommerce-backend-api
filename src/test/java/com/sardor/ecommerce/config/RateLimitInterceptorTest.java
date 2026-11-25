package com.sardor.ecommerce.config;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RateLimitInterceptorTest {

    @Test
    void rejectsRequestsAfterTheConfiguredWindowLimit() throws Exception {
        RateLimitInterceptor interceptor = new RateLimitInterceptor(true, Duration.ofSeconds(30), 2, 2, 2);

        MockHttpServletRequest firstRequest = request();
        MockHttpServletResponse firstResponse = new MockHttpServletResponse();
        assertTrue(interceptor.preHandle(firstRequest, firstResponse, new Object()));
        assertEquals("1", firstResponse.getHeader("X-RateLimit-Remaining"));

        MockHttpServletRequest secondRequest = request();
        MockHttpServletResponse secondResponse = new MockHttpServletResponse();
        assertTrue(interceptor.preHandle(secondRequest, secondResponse, new Object()));
        assertEquals("0", secondResponse.getHeader("X-RateLimit-Remaining"));

        MockHttpServletRequest thirdRequest = request();
        MockHttpServletResponse thirdResponse = new MockHttpServletResponse();
        assertFalse(interceptor.preHandle(thirdRequest, thirdResponse, new Object()));
        assertEquals(429, thirdResponse.getStatus());
        int retryAfter = Integer.parseInt(thirdResponse.getHeader("Retry-After"));
        assertTrue(retryAfter >= 1 && retryAfter <= 30);
    }

    @Test
    void doesNotLimitReadOnlyRequestsOrUnconfiguredRoutes() throws Exception {
        RateLimitInterceptor interceptor = new RateLimitInterceptor(true, Duration.ofSeconds(30), 1, 1, 1);

        MockHttpServletRequest healthRequest = new MockHttpServletRequest("GET", "/actuator/health");
        MockHttpServletResponse healthResponse = new MockHttpServletResponse();
        assertTrue(interceptor.preHandle(healthRequest, healthResponse, new Object()));

        MockHttpServletRequest productRequest = new MockHttpServletRequest("POST", "/api/v1/admin/products");
        MockHttpServletResponse productResponse = new MockHttpServletResponse();
        assertTrue(interceptor.preHandle(productRequest, productResponse, new Object()));
    }

    private MockHttpServletRequest request() {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/auth/login");
        request.setRemoteAddr("192.0.2.10");
        return request;
    }
}

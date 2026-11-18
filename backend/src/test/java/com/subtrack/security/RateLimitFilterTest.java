package com.subtrack.security;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class RateLimitFilterTest {

    private static MockHttpServletRequest post(String path, String ip) {
        MockHttpServletRequest req = new MockHttpServletRequest("POST", path);
        req.setRemoteAddr(ip);
        return req;
    }

    @Test
    void eleventhLoginInAMinuteFromOneAddressIs429() throws Exception {
        RateLimitFilter filter = new RateLimitFilter();
        FilterChain chain = (req, res) -> { };

        for (int i = 0; i < 10; i++) {
            MockHttpServletResponse res = new MockHttpServletResponse();
            filter.doFilter(post("/api/auth/login", "10.0.0.5"), res, chain);
            assertThat(res.getStatus()).isEqualTo(200);
        }
        MockHttpServletResponse blocked = new MockHttpServletResponse();
        filter.doFilter(post("/api/auth/login", "10.0.0.5"), blocked, chain);
        assertThat(blocked.getStatus()).isEqualTo(429);
        assertThat(blocked.getHeader("Retry-After")).isEqualTo("60");

        // Another address is not affected
        MockHttpServletResponse other = new MockHttpServletResponse();
        filter.doFilter(post("/api/auth/login", "10.0.0.6"), other, chain);
        assertThat(other.getStatus()).isEqualTo(200);
    }

    @Test
    void otherEndpointsAreNotLimited() throws Exception {
        RateLimitFilter filter = new RateLimitFilter();
        FilterChain chain = (req, res) -> { };

        for (int i = 0; i < 50; i++) {
            MockHttpServletResponse res = new MockHttpServletResponse();
            filter.doFilter(post("/api/subscriptions", "10.0.0.5"), res, chain);
            assertThat(res.getStatus()).isEqualTo(200);
        }
    }
}

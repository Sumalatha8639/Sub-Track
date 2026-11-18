package com.subtrack.security;

import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/** Per-IP rate limits on the auth endpoints that attackers like to hammer. In memory. */
@Component
public class RateLimitFilter extends OncePerRequestFilter {

    /** Path -> requests allowed per minute per IP. */
    private static final Map<String, Integer> LIMITS = Map.of(
            "/api/auth/login", 10,
            "/api/auth/register", 5,
            "/api/auth/forgot-password", 5,
            "/api/auth/resend-verification", 5,
            "/api/auth/reset-password", 10);

    /** Simple guard so the map can't grow without bound. */
    private static final int MAX_TRACKED_KEYS = 10_000;

    private final Map<String, Bucket> buckets = new ConcurrentHashMap<>();

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !"POST".equals(request.getMethod()) || !LIMITS.containsKey(request.getRequestURI());
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String path = request.getRequestURI();
        if (buckets.size() > MAX_TRACKED_KEYS) {
            buckets.clear();
        }
        Bucket bucket = buckets.computeIfAbsent(request.getRemoteAddr() + "|" + path,
                k -> newBucket(LIMITS.get(path)));

        if (bucket.tryConsume(1)) {
            chain.doFilter(request, response);
            return;
        }
        response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setHeader("Retry-After", "60");
        response.getWriter().write("""
                {"status":429,"error":"Too Many Requests","message":"Too many attempts. Please wait a minute and try again.","path":"%s"}"""
                .formatted(path));
    }

    /**
     * A strict "N per minute": the full allowance comes back once a minute. (Greedy refill would
     * hand back one attempt every 60/N seconds, letting slow, steady guessing through.)
     */
    private static Bucket newBucket(int perMinute) {
        return Bucket.builder()
                .addLimit(Bandwidth.builder().capacity(perMinute).refillIntervally(perMinute, Duration.ofMinutes(1)).build())
                .build();
    }

    /** Clears all counters; used by integration tests so they don't trip the limits. */
    public void reset() {
        buckets.clear();
    }
}

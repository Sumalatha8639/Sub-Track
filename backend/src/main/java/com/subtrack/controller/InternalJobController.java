package com.subtrack.controller;

import com.subtrack.config.AppProperties;
import com.subtrack.exception.UnauthorizedException;
import com.subtrack.jobs.JobRunner;
import com.subtrack.jobs.JobRunner.JobSummary;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Called by the free external cron (cron-job.org) every 15 minutes. Protected by a shared secret
 * header instead of a user token.
 */
@RestController
@RequestMapping("/internal")
@RequiredArgsConstructor
public class InternalJobController {

    static final String SECRET_HEADER = "X-Job-Secret";

    private final JobRunner jobRunner;
    private final AppProperties props;

    @PostMapping("/jobs/run")
    public ResponseEntity<JobSummary> run(@RequestHeader(name = SECRET_HEADER, required = false) String secret) {
        if (!matches(secret, props.jobs().secret())) {
            throw new UnauthorizedException("Invalid job secret");
        }
        JobSummary summary = jobRunner.runAll();
        return ResponseEntity.status(summary.ran() ? HttpStatus.OK : HttpStatus.ACCEPTED).body(summary);
    }

    /** Cheap endpoint to wake a sleeping instance. */
    @GetMapping("/health")
    public Map<String, String> health() {
        return Map.of("status", "ok");
    }

    /** Constant-time comparison so the secret can't be guessed byte by byte from response times. */
    static boolean matches(String given, String expected) {
        if (given == null || expected == null || expected.isBlank()) {
            return false;
        }
        return MessageDigest.isEqual(given.getBytes(StandardCharsets.UTF_8), expected.getBytes(StandardCharsets.UTF_8));
    }
}

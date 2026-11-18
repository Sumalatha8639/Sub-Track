package com.subtrack.jobs;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

/**
 * Local development only: runs the jobs every 15 minutes. In production the free host sleeps,
 * so cron-job.org calls POST /internal/jobs/run instead (project plan §10).
 */
@Configuration
@Profile("local")
@EnableScheduling
@RequiredArgsConstructor
public class LocalJobScheduler {

    private final JobRunner jobRunner;

    @Scheduled(initialDelayString = "PT1M", fixedDelayString = "PT15M")
    public void run() {
        jobRunner.runAll();
    }
}

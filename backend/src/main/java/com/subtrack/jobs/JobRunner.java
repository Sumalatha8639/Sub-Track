package com.subtrack.jobs;

import com.subtrack.notification.NotificationService;
import com.subtrack.notification.NotificationService.RunResult;
import com.subtrack.service.RenewalService;
import java.time.Clock;
import java.time.Duration;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.core.LockConfiguration;
import net.javacrumbs.shedlock.core.LockingTaskExecutor;
import net.javacrumbs.shedlock.core.LockingTaskExecutor.TaskResult;
import org.springframework.stereotype.Component;

/**
 * Runs all background jobs in order, under one ShedLock lock (project plan §7):
 * renewal first (so reminders see the new due dates), then reminders, then retries.
 * Triggered by the cron endpoint in production and by @Scheduled locally.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class JobRunner {

    static final String LOCK_NAME = "subtrack-jobs";
    /** If a run dies without releasing the lock, it frees itself after this long. */
    static final Duration LOCK_AT_MOST = Duration.ofMinutes(10);

    private final LockingTaskExecutor lockingExecutor;
    private final RenewalService renewalService;
    private final NotificationService notificationService;
    private final Clock clock;

    public record JobSummary(boolean ran, int renewed, RunResult reminders, RunResult retries) {

        static JobSummary skipped() {
            return new JobSummary(false, 0, null, null);
        }
    }

    public JobSummary runAll() {
        LockConfiguration lock = new LockConfiguration(clock.instant(), LOCK_NAME, LOCK_AT_MOST, Duration.ZERO);
        try {
            TaskResult<JobSummary> result = lockingExecutor.executeWithLock(this::runUnlocked, lock);
            if (!result.wasExecuted()) {
                log.info("Jobs already running elsewhere; skipped");
                return JobSummary.skipped();
            }
            return result.getResult();
        } catch (RuntimeException e) {
            throw e;
        } catch (Throwable t) {
            throw new IllegalStateException("Job run failed", t);
        }
    }

    private JobSummary runUnlocked() {
        int renewed = renewalService.renewOverdueAutoRenewals();
        RunResult reminders = notificationService.sendDueReminders();
        RunResult retries = notificationService.retryFailed();
        return new JobSummary(true, renewed, reminders, retries);
    }
}

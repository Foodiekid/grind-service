package com.grindandtrain.worker.account;

import java.time.Clock;
import java.time.Instant;
import java.util.List;

import com.grindandtrain.common.domain.UserId;
import com.grindandtrain.core.account.service.AccountService;
import com.grindandtrain.worker.common.config.WorkerProperties;
import com.grindandtrain.worker.common.jobs.JobReport;
import com.grindandtrain.worker.common.jobs.ScheduledJob;

import org.springframework.stereotype.Component;

/**
 * Hourly: publishes again every account deletion that is still incomplete after
 * {@code grind.worker.jobs.deletion-resume-after}, for example because its event ended in the dead-letter topic. An
 * account deletion must always finish (App Store rule 5.1.1(v)); the handlers are idempotent, so resuming is safe.
 *
 * @author Dheeraj_Edupuganti
 */
@Component
public class AccountDeletionSweepJob implements ScheduledJob {

    private static final int BATCH_SIZE = 500;

    private final AccountService accountService;
    private final WorkerProperties properties;
    private final Clock clock;

    public AccountDeletionSweepJob(AccountService accountService, WorkerProperties properties, Clock clock) {
        this.accountService = accountService;
        this.properties = properties;
        this.clock = clock;
    }

    @Override
    public String name() {
        return "account-deletion-sweep";
    }

    @Override
    public JobReport run(Instant deadline) {
        Instant requestedBefore = Instant.now(clock).minus(properties.jobs().deletionResumeAfter());
        List<UserId> stuck = accountService.findStuckDeletions(requestedBefore, BATCH_SIZE);
        int resumed = 0;
        for (UserId user : stuck) {
            if (!Instant.now(clock).isBefore(deadline)) {
                return new JobReport(name(), stuck.size(), resumed, false);
            }
            accountService.resumeDeletion(user);
            resumed++;
        }
        return new JobReport(name(), stuck.size(), resumed, stuck.size() < BATCH_SIZE);
    }
}

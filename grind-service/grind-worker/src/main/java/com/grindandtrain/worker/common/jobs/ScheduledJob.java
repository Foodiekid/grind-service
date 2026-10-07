package com.grindandtrain.worker.common.jobs;

import java.time.Instant;

/**
 * A housekeeping job started by Cloud Scheduler through {@link JobController}. Implementations live in their
 * feature's package, like event handlers.
 * <p>
 * A run works until it is done or the deadline passes, whichever comes first. It must be safe to stop at any point
 * and to run again: the next scheduled run simply carries on. It touches user data only through grind-core services,
 * one user at a time.
 *
 * @author Dheeraj_Edupuganti
 */
public interface ScheduledJob {

    /** Name in the job's URL ({@code /internal/jobs/<name>}) and in the logs. Lower case, words joined by dashes. */
    String name();

    JobReport run(Instant deadline);
}

package com.grindandtrain.worker.common.jobs;

/**
 * What one job run did, for the response and the log line. Counts only: never ids or user data.
 *
 * @param examined how many items (users, deletions) the run looked at
 * @param changed  how many of those it changed
 * @param finished whether it got through everything, or stopped at its deadline and leaves the rest to the next run
 *
 * @author Dheeraj_Edupuganti
 */
public record JobReport(String job, int examined, int changed, boolean finished) {
}

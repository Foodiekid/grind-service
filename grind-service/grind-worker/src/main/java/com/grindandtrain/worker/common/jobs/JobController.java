package com.grindandtrain.worker.common.jobs;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import com.grindandtrain.common.error.GrindException;
import com.grindandtrain.common.logging.LogFields;
import com.grindandtrain.common.logging.MdcScope;
import com.grindandtrain.core.common.error.BusinessErrorCode;
import com.grindandtrain.worker.common.config.WorkerProperties;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Starts scheduled jobs. Cloud Scheduler calls {@code POST /internal/jobs/<name>} with its own Google-signed token
 * (only it may, see SecurityConfig). The run gets the configured time budget; a failure answers 500 and Scheduler
 * retries, which is safe because every job can be repeated.
 *
 * @author Dheeraj_Edupuganti
 */
@RestController
public class JobController {

    public static final String PATH = "/internal/jobs/{job}";

    private static final Logger log = LoggerFactory.getLogger(JobController.class);

    private final Map<String, ScheduledJob> jobs;
    private final WorkerProperties properties;
    private final Clock clock;

    public JobController(List<ScheduledJob> jobs, WorkerProperties properties, Clock clock) {
        this.jobs = jobs.stream().collect(Collectors.toUnmodifiableMap(ScheduledJob::name, Function.identity()));
        this.properties = properties;
        this.clock = clock;
    }

    @PostMapping(PATH)
    public JobReport run(@PathVariable("job") String name) {
        ScheduledJob job = jobs.get(name);
        if (job == null) {
            throw new GrindException(BusinessErrorCode.NOT_FOUND);
        }
        try (MdcScope scope = MdcScope.open().put(LogFields.JOB, job.name())) {
            Instant deadline = Instant.now(clock).plus(properties.jobs().timeBudget());
            JobReport report = job.run(deadline);
            log.info("Job run: examined {}, changed {}, finished {}", report.examined(), report.changed(), report.finished());
            return report;
        }
    }
}

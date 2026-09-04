package com.restapi.asyncjobs;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.*;

/**
 * Service managing Long-Running Asynchronous Jobs (202 Accepted Pattern).
 * Background workers execute concurrently over Java 21 Virtual Threads.
 */
@Service
public class AsyncJobService implements AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(AsyncJobService.class);

    private final Map<String, AsyncJob> jobStore = new ConcurrentHashMap<>();
    private final Map<String, Future<?>> runningFutures = new ConcurrentHashMap<>();
    private final ExecutorService virtualExecutor = Executors.newVirtualThreadPerTaskExecutor();

    public AsyncJob createJob(String type) {
        String id = "job_" + UUID.randomUUID().toString().substring(0, 8);
        Instant now = Instant.now();
        AsyncJob job = new AsyncJob(id, type, JobStatus.PENDING, 0, null, null, now, now);
        jobStore.put(id, job);

        // Submit background processing on a lightweight Virtual Thread
        Future<?> future = virtualExecutor.submit(() -> runJobTask(id));
        runningFutures.put(id, future);

        return job;
    }

    private void runJobTask(String id) {
        try {
            log.info("Starting async background job [{}] on Virtual Thread: {}", id, Thread.currentThread());
            updateJob(id, 25, JobStatus.RUNNING);
            Thread.sleep(150); // Simulate stage 1

            updateJob(id, 65, JobStatus.RUNNING);
            Thread.sleep(150); // Simulate stage 2

            updateJob(id, 90, JobStatus.RUNNING);
            Thread.sleep(100); // Simulate stage 3

            // Job completed successfully
            AsyncJob current = jobStore.get(id);
            if (current != null && current.status() != JobStatus.CANCELLED) {
                jobStore.put(id, current.withCompletion("/api/v1/jobs/" + id + "/result.csv"));
                log.info("Async job [{}] completed successfully", id);
            }
        } catch (InterruptedException ie) {
            log.warn("Async job [{}] was cancelled or interrupted", id);
            AsyncJob current = jobStore.get(id);
            if (current != null) {
                jobStore.put(id, current.withCancellation());
            }
            Thread.currentThread().interrupt();
        } catch (Exception ex) {
            log.error("Async job [{}] failed: {}", id, ex.getMessage());
            AsyncJob current = jobStore.get(id);
            if (current != null) {
                jobStore.put(id, current.withFailure(ex.getMessage()));
            }
        } finally {
            runningFutures.remove(id);
        }
    }

    private void updateJob(String id, int progress, JobStatus status) {
        AsyncJob current = jobStore.get(id);
        if (current != null && current.status() != JobStatus.CANCELLED) {
            jobStore.put(id, current.withProgress(progress, status));
        }
    }

    public Optional<AsyncJob> getJob(String id) {
        return Optional.ofNullable(jobStore.get(id));
    }

    public boolean cancelJob(String id) {
        AsyncJob current = jobStore.get(id);
        if (current == null || current.status() == JobStatus.COMPLETED || current.status() == JobStatus.FAILED) {
            return false;
        }

        Future<?> future = runningFutures.get(id);
        if (future != null) {
            future.cancel(true);
        }
        jobStore.put(id, current.withCancellation());
        return true;
    }

    @Override
    public void close() {
        virtualExecutor.close();
    }
}

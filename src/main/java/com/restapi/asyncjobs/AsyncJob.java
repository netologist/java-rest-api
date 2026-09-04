package com.restapi.asyncjobs;

import java.time.Instant;

/**
 * Immutable Asynchronous Job representation (Java Record).
 */
public record AsyncJob(
        String id,
        String type,
        JobStatus status,
        int progressPercent,
        String resultUrl,
        String errorMessage,
        Instant createdAt,
        Instant updatedAt
) {
    public AsyncJob withProgress(int newProgress, JobStatus newStatus) {
        return new AsyncJob(id, type, newStatus, newProgress, resultUrl, errorMessage, createdAt, Instant.now());
    }

    public AsyncJob withCompletion(String resultUrl) {
        return new AsyncJob(id, type, JobStatus.COMPLETED, 100, resultUrl, null, createdAt, Instant.now());
    }

    public AsyncJob withFailure(String errorMessage) {
        return new AsyncJob(id, type, JobStatus.FAILED, progressPercent, null, errorMessage, createdAt, Instant.now());
    }

    public AsyncJob withCancellation() {
        return new AsyncJob(id, type, JobStatus.CANCELLED, progressPercent, null, "Cancelled by client", createdAt, Instant.now());
    }
}

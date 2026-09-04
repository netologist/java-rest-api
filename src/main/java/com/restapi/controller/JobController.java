package com.restapi.controller;

import com.restapi.asyncjobs.AsyncJob;
import com.restapi.asyncjobs.AsyncJobService;
import com.restapi.problem.OrderNotFoundException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.Map;

/**
 * Controller implementing the 202 Accepted Async Jobs Pattern.
 */
@RestController
@RequestMapping("/api/v1/jobs")
public class JobController {

    private final AsyncJobService jobService;

    public JobController(AsyncJobService jobService) {
        this.jobService = jobService;
    }

    public record CreateJobRequest(String type) {}

    @PostMapping
    public ResponseEntity<AsyncJob> createJob(@RequestBody(required = false) CreateJobRequest req) {
        String type = (req == null || req.type() == null) ? "DEFAULT_REPORT" : req.type();
        AsyncJob job = jobService.createJob(type);

        return ResponseEntity.status(HttpStatus.ACCEPTED)
                .header(HttpHeaders.LOCATION, "/api/v1/jobs/" + job.id())
                .header("Retry-After", "2") // Poll every 2 seconds
                .body(job);
    }

    @GetMapping("/{id}")
    public ResponseEntity<AsyncJob> getJob(@PathVariable String id) {
        return jobService.getJob(id)
                .map(ResponseEntity::ok)
                .orElseThrow(() -> new OrderNotFoundException("Job with ID '" + id + "' not found"));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Map<String, String>> cancelJob(@PathVariable String id) {
        boolean cancelled = jobService.cancelJob(id);
        if (!cancelled) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("error", "Job cannot be cancelled (already completed, failed, or not found)"));
        }
        return ResponseEntity.ok(Map.of("status", "CANCELLED", "id", id));
    }
}

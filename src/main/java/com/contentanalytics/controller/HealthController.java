package com.contentanalytics.controller;

import com.contentanalytics.dto.HealthCheckResponseDto;
import com.contentanalytics.entity.HealthMetric;
import com.contentanalytics.service.HealthCheckService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/health")
@RequiredArgsConstructor
@Slf4j
public class HealthController {

    private final HealthCheckService healthCheckService;

    /**
     * GET /api/health
     * Quick health check - used by load balancers
     *
     * Response: 200 OK if healthy, 503 if not
     */
    @GetMapping
    public ResponseEntity<HealthCheckResponseDto> healthCheck() {
        log.debug("Health check requested");

        HealthCheckResponseDto health = healthCheckService.performHealthCheck();

        // Return 503 if service is down, 200 otherwise
        HttpStatus status = "DOWN".equals(health.getStatus())
                ? HttpStatus.SERVICE_UNAVAILABLE
                : HttpStatus.OK;

        return ResponseEntity.status(status).body(health);
    }

    /**
     * GET /api/health/live
     * Kubernetes liveness probe
     * Check if app process is still running
     */
    @GetMapping("/live")
    public ResponseEntity<String> livenessProbe() {
        return ResponseEntity.ok("UP");
    }

    /**
     * GET /api/health/ready
     * Kubernetes readiness probe
     * Check if app is ready to handle traffic
     */
    @GetMapping("/ready")
    public ResponseEntity<HealthCheckResponseDto> readinessProbe() {
        HealthCheckResponseDto health = healthCheckService.performHealthCheck();

        // Only ready if database and Redis are up
        boolean isReady = "UP".equals(health.getStatus()) || "DEGRADED".equals(health.getStatus());

        if (!isReady) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(health);
        }

        return ResponseEntity.ok(health);
    }

    /**
     * GET /api/health/history/{checkType}
     * Get health history for a specific component
     *
     * Query parameters:
     * - days: number of days to look back (default 7)
     */
    @GetMapping("/history/{checkType}")
    public ResponseEntity<List<HealthMetric>> getHealthHistory(
            @PathVariable String checkType,
            @RequestParam(value = "days", defaultValue = "7") int days) {

        log.debug("Fetching health history for {} - last {} days", checkType, days);

        List<HealthMetric> history = healthCheckService.getHealthHistory(checkType, days);

        return ResponseEntity.ok(history);
    }

}

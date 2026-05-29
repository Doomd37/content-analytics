package com.contentanalytics.service;

import com.contentanalytics.dto.HealthCheckResponseDto;
import com.contentanalytics.entity.HealthMetric;
import com.contentanalytics.entity.HealthStatus;
import com.contentanalytics.repository.HealthMetricRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.sql.DataSource;
import java.io.File;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import com.sun.management.OperatingSystemMXBean;
//import java.lang.management.OperatingSystemMXBean;
import java.time.LocalDateTime;
import java.util.*;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class HealthCheckService {

    private final HealthMetricRepository healthMetricRepository;
    private final DataSource dataSource;
    private final RedisConnectionFactory redisConnectionFactory;

    @Value("${app.name:Content Analytics Platform}")
    private String appName;

    @Value("${app.version:1.0.0}")
    private String appVersion;

    @Value("${spring.profiles.active:local}")
    private String activeProfile;

    private static final long START_TIME = System.currentTimeMillis();

    /**
     * Perform comprehensive health check
     */
    public HealthCheckResponseDto performHealthCheck() {
        log.debug("Performing comprehensive health check");

        Map<String, HealthCheckResponseDto.ComponentHealthDto> components = new HashMap<>();

        // Check database
        components.put("database", checkDatabase());

        // Check Redis
        components.put("redis", checkRedis());

        // Check disk space
        components.put("disk", checkDiskSpace());

        // Get system resources
        HealthCheckResponseDto.SystemResourcesDto resources = getSystemResources();

        // Determine overall status
        String overallStatus = determineOverallStatus(components);

        // Build response
        HealthCheckResponseDto response = HealthCheckResponseDto.builder()
                .status(overallStatus)
                .timestamp(LocalDateTime.now())
                .uptime(getUptimeSeconds())
                .components(components)
                .resources(resources)
                .version(appVersion)
                .environment(activeProfile)
                .build();

        // Log unhealthy components
        if (!overallStatus.equals("UP")) {
            log.warn("Health check returned status: {} with components: {}", overallStatus, components);
        }

        return response;
    }

    /**
     * Check database connectivity
     */
    private HealthCheckResponseDto.ComponentHealthDto checkDatabase() {
        long startTime = System.currentTimeMillis();

        try {
            // Try to get connection
            var connection = dataSource.getConnection();
            connection.isValid(5); // 5 second timeout
            connection.close();

            long responseTime = System.currentTimeMillis() - startTime;

            // Save metric
            saveHealthMetric("database", HealthStatus.HEALTHY, responseTime, "Database connection successful");

            return HealthCheckResponseDto.ComponentHealthDto.builder()
                    .status("UP")
                    .message("Database connection successful")
                    .responseTimeMs(responseTime)
                    .lastCheckedAt(LocalDateTime.now())
                    .build();

        } catch (Exception e) {
            log.error("Database health check failed", e);

            saveHealthMetric("database", HealthStatus.UNHEALTHY,
                    System.currentTimeMillis() - startTime,
                    "Database connection failed: " + e.getMessage());

            return HealthCheckResponseDto.ComponentHealthDto.builder()
                    .status("DOWN")
                    .message("Database connection failed: " + e.getMessage())
                    .responseTimeMs(System.currentTimeMillis() - startTime)
                    .lastCheckedAt(LocalDateTime.now())
                    .build();
        }
    }

    /**
     * Check Redis connectivity
     */
    private HealthCheckResponseDto.ComponentHealthDto checkRedis() {
        long startTime = System.currentTimeMillis();

        try {
            // Try to get Redis connection
            var connection = redisConnectionFactory.getConnection();
            connection.ping();
            connection.close();

            long responseTime = System.currentTimeMillis() - startTime;

            saveHealthMetric("redis", HealthStatus.HEALTHY, responseTime, "Redis connection successful");

            return HealthCheckResponseDto.ComponentHealthDto.builder()
                    .status("UP")
                    .message("Redis connection successful")
                    .responseTimeMs(responseTime)
                    .lastCheckedAt(LocalDateTime.now())
                    .build();

        } catch (Exception e) {
            log.error("Redis health check failed", e);

            saveHealthMetric("redis", HealthStatus.UNHEALTHY,
                    System.currentTimeMillis() - startTime,
                    "Redis connection failed: " + e.getMessage());

            return HealthCheckResponseDto.ComponentHealthDto.builder()
                    .status("DOWN")
                    .message("Redis connection failed: " + e.getMessage())
                    .responseTimeMs(System.currentTimeMillis() - startTime)
                    .lastCheckedAt(LocalDateTime.now())
                    .build();
        }
    }

    /**
     * Check disk space availability
     */
    private HealthCheckResponseDto.ComponentHealthDto checkDiskSpace() {
        try {
            File root = new File("/");
            long diskAvailableBytes = root.getFreeSpace();
            long diskTotalBytes = root.getTotalSpace();

            // Consider unhealthy if less than 100MB available
            HealthStatus status = diskAvailableBytes > (100 * 1024 * 1024)
                    ? HealthStatus.HEALTHY
                    : HealthStatus.UNHEALTHY;

            long diskAvailableGb = diskAvailableBytes / (1024 * 1024 * 1024);

            saveHealthMetric("disk", status, 0L, "Disk space: " + diskAvailableGb + "GB available");

            String componentStatus = status == HealthStatus.HEALTHY ? "UP" : "DEGRADED";

            return HealthCheckResponseDto.ComponentHealthDto.builder()
                    .status(componentStatus)
                    .message("Disk space available: " + diskAvailableGb + "GB")
                    .lastCheckedAt(LocalDateTime.now())
                    .build();

        } catch (Exception e) {
            log.error("Disk space check failed", e);

            saveHealthMetric("disk", HealthStatus.UNHEALTHY, 0L, "Disk check failed: " + e.getMessage());

            return HealthCheckResponseDto.ComponentHealthDto.builder()
                    .status("DOWN")
                    .message("Disk space check failed")
                    .lastCheckedAt(LocalDateTime.now())
                    .build();
        }
    }

    /**
     * Get system resources (CPU, memory)
     */
    private HealthCheckResponseDto.SystemResourcesDto getSystemResources() {

        OperatingSystemMXBean osMXBean =
                (OperatingSystemMXBean) ManagementFactory.getOperatingSystemMXBean();

        MemoryMXBean memoryMXBean = ManagementFactory.getMemoryMXBean();

        // CPU usage (correct method)
        double cpuLoad = osMXBean.getCpuLoad();
        float cpuUsage = cpuLoad < 0 ? 0 : (float) (cpuLoad * 100);

        long heapUsed = memoryMXBean.getHeapMemoryUsage().getUsed();
        long heapMax = memoryMXBean.getHeapMemoryUsage().getMax();

        float memoryUsagePercent = (float) ((heapUsed * 100.0) / heapMax);
        float memoryUsageGb = (float) (heapUsed / (1024.0 * 1024.0 * 1024.0));
        float memoryMaxGb = (float) (heapMax / (1024.0 * 1024.0 * 1024.0));

        File root = new File("/");
        long diskAvailableGb = root.getFreeSpace() / (1024 * 1024 * 1024);
        long diskTotalGb = root.getTotalSpace() / (1024 * 1024 * 1024);

        saveSystemMetrics(cpuUsage, memoryUsagePercent, root.getFreeSpace());

        return HealthCheckResponseDto.SystemResourcesDto.builder()
                .cpuUsagePercent(cpuUsage)
                .memoryUsagePercent(memoryUsagePercent)
                .memoryUsageGb(memoryUsageGb)
                .memoryMaxGb(memoryMaxGb)
                .diskAvailableGb(diskAvailableGb)
                .diskTotalGb(diskTotalGb)
                .build();
    }

    /**
     * Determine overall health status
     */
    private String determineOverallStatus(Map<String, HealthCheckResponseDto.ComponentHealthDto> components) {
        boolean hasDown = components.values().stream().anyMatch(c -> "DOWN".equals(c.getStatus()));
        if (hasDown) {
            return "DOWN";
        }

        boolean hasDegraded = components.values().stream().anyMatch(c -> "DEGRADED".equals(c.getStatus()));
        if (hasDegraded) {
            return "DEGRADED";
        }

        return "UP";
    }

    /**
     * Get uptime in seconds
     */
    private Long getUptimeSeconds() {
        return (System.currentTimeMillis() - START_TIME) / 1000;
    }

    /**
     * Save health metric to database
     */
    private void saveHealthMetric(String checkType, HealthStatus status, Long responseTimeMs, String message) {
        try {
            HealthMetric metric = HealthMetric.builder()
                    .checkType(checkType)
                    .status(status)
                    .responseTimeMs(responseTimeMs)
                    .message(message)
                    .build();

            healthMetricRepository.save(metric);
        } catch (Exception e) {
            log.error("Failed to save health metric", e);
            // Don't throw - we don't want health check to fail because of metric saving
        }
    }

    /**
     * Save system metrics
     */
    private void saveSystemMetrics(Float cpuUsage, Float memoryUsage, Long diskAvailable) {
        try {
            HealthMetric metric = HealthMetric.builder()
                    .checkType("system")
                    .status(HealthStatus.HEALTHY)
                    .cpuUsagePercent(cpuUsage)
                    .memoryUsagePercent(memoryUsage)
                    .diskAvailableBytes(diskAvailable)
                    .message("System metrics")
                    .build();

            healthMetricRepository.save(metric);
        } catch (Exception e) {
            log.error("Failed to save system metrics", e);
        }
    }

    /**
     * Get health history for a component
     */
    public List<HealthMetric> getHealthHistory(String checkType, int days) {
        LocalDateTime startDate = LocalDateTime.now().minusDays(days);
        LocalDateTime endDate = LocalDateTime.now();

        var page = healthMetricRepository.findByCheckTypeAndCreatedAtBetween(
                checkType,
                startDate,
                endDate,
                org.springframework.data.domain.PageRequest.of(0, 1000)
        );

        return page.getContent();
    }

}
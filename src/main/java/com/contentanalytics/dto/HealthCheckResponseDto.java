package com.contentanalytics.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class HealthCheckResponseDto {

    // Overall status
    private String status; // UP, DEGRADED, DOWN
    private LocalDateTime timestamp;
    private Long uptime; // in seconds

    // Component status
    private Map<String, ComponentHealthDto> components;

    // System resources
    private SystemResourcesDto resources;

    // Additional info
    private String version;
    private String environment;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class ComponentHealthDto {
        private String status; // UP, DEGRADED, DOWN
        private String message;
        private Long responseTimeMs;
        private LocalDateTime lastCheckedAt;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SystemResourcesDto {
        private Float cpuUsagePercent;
        private Float memoryUsagePercent;
        private Float memoryUsageGb;
        private Float memoryMaxGb;
        private Long diskAvailableGb;
        private Long diskTotalGb;
    }

}

package com.contentanalytics.entity;

public enum HealthStatus {
    HEALTHY,      // Everything is fine
    DEGRADED,     // Partially working, but acceptable
    UNHEALTHY     // Critical issue, service may fail
}

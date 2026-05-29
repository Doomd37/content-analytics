package com.contentanalytics.repository;

import com.contentanalytics.entity.HealthMetric;
import com.contentanalytics.entity.HealthStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface HealthMetricRepository extends JpaRepository<HealthMetric, String> {

    /**
     * Find latest health check for a specific check type
     */
    Optional<HealthMetric> findTopByCheckTypeOrderByCreatedAtDesc(String checkType);

    /**
     * Find all health metrics for a check type in time range
     */
    Page<HealthMetric> findByCheckTypeAndCreatedAtBetween(
            String checkType,
            LocalDateTime startDate,
            LocalDateTime endDate,
            Pageable pageable
    );

    /**
     * Find unhealthy metrics
     */
    List<HealthMetric> findByStatusOrderByCreatedAtDesc(HealthStatus status);

    /**
     * Get latest checks for all types
     */
    @Query("""
        SELECT h FROM HealthMetric h
        WHERE h.id IN (
            SELECT h2.id FROM HealthMetric h2
            WHERE h2.checkType = h.checkType
            ORDER BY h2.createdAt DESC
            LIMIT 1
        )
        """)
    List<HealthMetric> findLatestChecksForAllTypes();

}
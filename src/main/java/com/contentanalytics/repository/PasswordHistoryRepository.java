package com.contentanalytics.repository;

import com.contentanalytics.entity.PasswordHistory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface PasswordHistoryRepository extends JpaRepository<PasswordHistory, String> {

    /**
     * Get last N passwords for user (to prevent reuse)
     */
    @Query("SELECT ph FROM PasswordHistory ph WHERE ph.user.id = :userId ORDER BY ph.createdAt DESC LIMIT 5")
    List<PasswordHistory> findLastNPasswordsForUser(@Param("userId") String userId);

    /**
     * Count passwords changed by user in last X hours
     */
    @Query("SELECT COUNT(ph) FROM PasswordHistory ph WHERE ph.user.id = :userId AND ph.createdAt > :since")
    long countPasswordChangesInTimeRange(@Param("userId") String userId, @Param("since") java.time.LocalDateTime since);

}

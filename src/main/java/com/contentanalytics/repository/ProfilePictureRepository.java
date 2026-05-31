package com.contentanalytics.repository;

import com.contentanalytics.entity.ProfilePicture;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface ProfilePictureRepository extends JpaRepository<ProfilePicture, String> {

    /**
     * Find profile picture by user ID
     */
    Optional<ProfilePicture> findByUserId(String userId);

    /**
     * Check if user has profile picture
     */
    boolean existsByUserIdAndIsActiveTrue(String userId);

    /**
     * Delete all inactive pictures older than X days
     */
    void deleteByIsActiveFalseAndCreatedAtBefore(java.time.LocalDateTime date);

}
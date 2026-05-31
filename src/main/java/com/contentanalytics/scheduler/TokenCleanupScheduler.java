package com.contentanalytics.scheduler;

import com.contentanalytics.repository.EmailVerificationTokenRepository;
import com.contentanalytics.repository.PasswordResetTokenRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Component
@RequiredArgsConstructor
@Slf4j
public class TokenCleanupScheduler {

    private final EmailVerificationTokenRepository emailVerificationTokenRepository;
    private final PasswordResetTokenRepository passwordResetTokenRepository;

    /**
     * Clean up expired tokens every day at 2 AM
     * Removes tokens that have expired to keep database clean
     */
    @Scheduled(cron = "0 0 2 * * *") // 2 AM every day
    @Transactional
    public void cleanupExpiredTokens() {
        log.info("Starting cleanup of expired tokens");

        LocalDateTime now = LocalDateTime.now();

        // Delete expired email verification tokens
        emailVerificationTokenRepository.deleteByExpiryDateBefore(now);
        log.debug("Expired email verification tokens cleaned up");

        // Delete expired password reset tokens
        passwordResetTokenRepository.deleteByExpiryDateBefore(now);
        log.debug("Expired password reset tokens cleaned up");

        log.info("Token cleanup completed");
    }

}

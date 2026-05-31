package com.contentanalytics.util;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.security.SecureRandom;
import java.util.Base64;

@Component
@Slf4j
public class TokenGenerationUtil {

    private static final SecureRandom secureRandom = new SecureRandom();
    private static final Base64.Encoder encoder = Base64.getUrlEncoder().withoutPadding();

    /**
     * Generate secure random token for email verification and password reset
     * Returns 32-byte (256-bit) random string, base64 encoded
     */
    public String generateSecureToken() {
        byte[] randomBytes = new byte[32];
        secureRandom.nextBytes(randomBytes);
        String token = encoder.encodeToString(randomBytes);
        log.debug("Generated secure token");
        return token;
    }

    /**
     * Validate token format
     */
    public boolean isValidTokenFormat(String token) {
        return token != null && !token.isBlank() && token.length() >= 32;
    }

}

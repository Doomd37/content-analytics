package com.contentanalytics.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

@Service
@Slf4j
public class GravatarService {

    private static final String GRAVATAR_URL = "https://www.gravatar.com/avatar/";
    private static final String DEFAULT_GRAVATAR = "?d=mp&s="; // Default: mystery person

    /**
     * Get Gravatar URL for email
     * If user has Gravatar, returns their avatar
     * Otherwise returns default mystery person avatar
     */
    public String getGravatarUrl(String email, int size) {
        try {
            // Gravatar uses MD5 hash of lowercase email
            String emailHash = md5(email.toLowerCase().trim());
            return GRAVATAR_URL + emailHash + DEFAULT_GRAVATAR + size;
        } catch (NoSuchAlgorithmException e) {
            log.error("Error generating Gravatar URL", e);
            return getDefaultAvatarUrl(size);
        }
    }

    /**
     * Generate MD5 hash of email
     */
    private String md5(String input) throws NoSuchAlgorithmException {
        MessageDigest md = MessageDigest.getInstance("MD5");
        byte[] messageDigest = md.digest(input.getBytes());

        StringBuilder sb = new StringBuilder();
        for (byte b : messageDigest) {
            sb.append(String.format("%02x", b));
        }

        return sb.toString();
    }

    /**
     * Get default avatar URL (DiceBear service)
     * Creates unique avatar based on seed
     */
    public String getDefaultAvatarUrl(String seed, int size) {
        return String.format("https://api.dicebear.com/7.x/avataaars/svg?seed=%s&size=%d", seed, size);
    }

    /**
     * Simple default avatar URL
     */
    private String getDefaultAvatarUrl(int size) {
        return GRAVATAR_URL + "00000000000000000000000000000000" + DEFAULT_GRAVATAR + size;
    }

}
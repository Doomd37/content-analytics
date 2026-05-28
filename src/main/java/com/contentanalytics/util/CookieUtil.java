package com.contentanalytics.util;

import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class CookieUtil {

    @Value("${server.servlet.context-path:}")
    private String contextPath;

    @Value("${app.cors.allowed-origins}")
    private String allowedOrigins;

    /**
     * Set refresh token in HTTP-only secure cookie
     * Frontend cannot access this via JavaScript
     */
    public void setRefreshTokenCookie(HttpServletResponse response, String refreshToken, long maxAgeSeconds) {
        boolean isProduction = allowedOrigins.contains("https");

        ResponseCookie cookie = ResponseCookie
                .from("refreshToken", refreshToken)
                .httpOnly(true) // Cannot be accessed by JavaScript
                .secure(isProduction) // Only send over HTTPS in production
                .path(contextPath + "/api/auth") // Only sent to auth endpoints
                .maxAge(maxAgeSeconds)
                .sameSite("Strict") // CSRF protection
                .build();

        response.addHeader("Set-Cookie", cookie.toString());
        log.debug("Refresh token cookie set");
    }

    /**
     * Clear refresh token cookie (on logout)
     */
    public void clearRefreshTokenCookie(HttpServletResponse response) {
        ResponseCookie cookie = ResponseCookie
                .from("refreshToken", "")
                .httpOnly(true)
                .secure(allowedOrigins.contains("https"))
                .path(contextPath + "/api/auth")
                .maxAge(0) // Tells browser to delete cookie
                .sameSite("Strict")
                .build();

        response.addHeader("Set-Cookie", cookie.toString());
        log.debug("Refresh token cookie cleared");
    }

    /**
     * Set CSRF token in cookie (frontend reads this and sends in header)
     */
    public void setCsrfTokenCookie(HttpServletResponse response, String csrfToken) {
        ResponseCookie cookie = ResponseCookie
                .from("XSRF-TOKEN", csrfToken)
                .httpOnly(false) // Frontend needs to read this
                .secure(allowedOrigins.contains("https"))
                .path("/")
                .sameSite("Strict")
                .build();

        response.addHeader("Set-Cookie", cookie.toString());
    }

}

package com.contentanalytics.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

@Component
@RequiredArgsConstructor
@Slf4j
public class JwtTokenProvider {

    @Value("${app.jwt.secret}")
    private String jwtSecret;

    @Value("${app.jwt.expiration}")
    private long jwtExpirationMs; // Access token expiration (15 minutes recommended)

    @Value("${app.jwt.refresh.expiration}")
    private long refreshTokenExpirationMs; // Refresh token expiration (7 days)

    /**
     * Generate Access Token (short-lived)
     * Used for API requests
     */
    public String generateAccessToken(String userId, String username, String email) {
        Map<String, Object> claims = new HashMap<>();
        claims.put("type", "ACCESS");
        claims.put("email", email);
        claims.put("userId", userId);

        return createToken(claims, username, jwtExpirationMs);
    }

    /**
     * Generate Refresh Token (long-lived)
     * Used to get new access tokens
     */
    public String generateRefreshToken(String userId, String username) {
        Map<String, Object> claims = new HashMap<>();
        claims.put("type", "REFRESH");
        claims.put("userId", userId);
        claims.put("tokenVersion", 1); // For token rotation

        return createToken(claims, username, refreshTokenExpirationMs);
    }

    /**
     * Create JWT token with claims
     */
    private String createToken(Map<String, Object> claims, String subject, long expirationTime) {
        Date now = new Date();
        Date expiryDate = new Date(now.getTime() + expirationTime);

        SecretKey key = Keys.hmacShaKeyFor(jwtSecret.getBytes());

        return Jwts.builder()
                .setClaims(claims)
                .setSubject(subject)
                .setIssuedAt(now)
                .setExpiration(expiryDate)
                .signWith(key, SignatureAlgorithm.HS512)
                .compact();
    }

    /**
     * Extract claims from token
     */
    public Claims getClaimsFromToken(String token) {
        try {
            SecretKey key = Keys.hmacShaKeyFor(jwtSecret.getBytes());
            return Jwts.parserBuilder()
                    .setSigningKey(key)
                    .build()
                    .parseClaimsJws(token)
                    .getBody();
        } catch (Exception e) {
            log.error("Error extracting claims from token", e);
            return null;
        }
    }

    /**
     * Get username from token
     */
    public String getUsernameFromToken(String token) {
        Claims claims = getClaimsFromToken(token);
        return claims != null ? claims.getSubject() : null;
    }

    /**
     * Get user ID from token
     */
    public String getUserIdFromToken(String token) {
        Claims claims = getClaimsFromToken(token);
        return claims != null ? (String) claims.get("userId") : null;
    }

    /**
     * Get token details including user ID
     */
    public Map<String, Object> getTokenDetails(String token) {
        Claims claims = getClaimsFromToken(token);
        if (claims == null) return null;

        Map<String, Object> details = new HashMap<>();
        details.put("userId", claims.get("userId"));
        details.put("username", claims.getSubject());
        details.put("email", claims.get("email"));
        details.put("type", claims.get("type"));

        return details;
    }


    /**
     * Validate token signature and expiration
     */
    public boolean validateToken(String token) {
        try {
            Claims claims = getClaimsFromToken(token);
            return claims != null && !isTokenExpired(claims);
        } catch (Exception e) {
            log.error("Token validation failed", e);
            return false;
        }
    }

    /**
     * Check if token is expired
     */
    public boolean isTokenExpired(Claims claims) {
        return claims.getExpiration().before(new Date());
    }

    /**
     * Get remaining time before expiration (in seconds)
     */
    public long getTokenTimeToLive(String token) {
        Claims claims = getClaimsFromToken(token);
        if (claims == null) return 0;

        long expirationTime = claims.getExpiration().getTime();
        long currentTime = System.currentTimeMillis();
        return (expirationTime - currentTime) / 1000;
    }

    /**
     * Check if token type is correct
     */
    public boolean isAccessToken(String token) {
        Claims claims = getClaimsFromToken(token);
        return claims != null && "ACCESS".equals(claims.get("type"));
    }

    public boolean isRefreshToken(String token) {
        Claims claims = getClaimsFromToken(token);
        return claims != null && "REFRESH".equals(claims.get("type"));
    }

}

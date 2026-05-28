package com.contentanalytics.security;

import io.jsonwebtoken.Claims;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.ArrayList;

@Component
@RequiredArgsConstructor
@Slf4j
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtTokenProvider tokenProvider;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        try {
            // Extract token from Authorization header
            String token = getJwtFromRequest(request);

            // If token exists and is valid
            if (token != null && tokenProvider.validateToken(token)) {
                // Only process ACCESS tokens, not refresh tokens
                if (tokenProvider.isAccessToken(token)) {
                    String username = tokenProvider.getUsernameFromToken(token);
                    Claims claims = tokenProvider.getClaimsFromToken(token);

                    // Create authentication object
                    UsernamePasswordAuthenticationToken authentication =
                            new UsernamePasswordAuthenticationToken(username, null, new ArrayList<>());

                    // Store user info in authentication object
                    authentication.setDetails(claims);

                    // Set authentication in security context
                    SecurityContextHolder.getContext().setAuthentication(authentication);
                    log.debug("Set Spring Security authentication for user: {}", username);
                }
            }
        } catch (Exception e) {
            log.error("Could not set user authentication in security context", e);
        }

        // Continue with filter chain
        filterChain.doFilter(request, response);
    }

    /**
     * Extract JWT token from Authorization header
     * Expected format: Bearer <token>
     */
    private String getJwtFromRequest(HttpServletRequest request) {
        String bearerToken = request.getHeader(HttpHeaders.AUTHORIZATION);

        if (bearerToken != null && bearerToken.startsWith("Bearer ")) {
            return bearerToken.substring(7);
        }

        return null;
    }

}

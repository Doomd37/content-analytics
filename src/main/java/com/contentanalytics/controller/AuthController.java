package com.contentanalytics.controller;

import com.contentanalytics.dto.*;
import com.contentanalytics.entity.User;
import com.contentanalytics.exception.InvalidTokenException;
import com.contentanalytics.exception.UserNotFoundException;
import com.contentanalytics.service.AuthService;
import com.contentanalytics.util.CookieUtil;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import com.contentanalytics.dto.VerifyEmailRequestDto;
import com.contentanalytics.dto.ResendVerificationEmailRequestDto;
import com.contentanalytics.dto.ForgotPasswordRequestDto;
import com.contentanalytics.dto.ResetPasswordRequestDto;
import com.contentanalytics.repository.EmailVerificationTokenRepository;
import com.contentanalytics.repository.PasswordResetTokenRepository;
import java.time.LocalDateTime;
import java.util.Map;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
@Slf4j
public class AuthController {

    private final AuthService authService;
    private final CookieUtil cookieUtil;
    private final EmailVerificationTokenRepository emailVerificationTokenRepository;
    private final PasswordResetTokenRepository passwordResetTokenRepository;

    /**
     * POST /api/auth/register
     * Register a new user
     *
     * Request body:
     * {
     *   "username": "john_doe",
     *   "email": "john@example.com",
     *   "password": "SecurePass123!",
     *   "firstName": "John",
     *   "lastName": "Doe"
     * }
     */
    @PostMapping("/register")
    public ResponseEntity<TokenResponseDto> register(
            @Valid @RequestBody RegisterRequestDto request,
            HttpServletResponse response) {

        log.info("Register request for user: {}", request.getUsername());

        // Register user and get tokens
        TokenResponseDto tokenResponse = authService.register(request);

        // Set refresh token in HTTP-only secure cookie
        long refreshTokenExpirationSeconds = 7 * 24 * 60 * 60; // 7 days
        cookieUtil.setRefreshTokenCookie(response, tokenResponse.getRefreshToken(), refreshTokenExpirationSeconds);

        // Return 201 CREATED status
        return ResponseEntity.status(HttpStatus.CREATED).body(tokenResponse);
    }

    /**
     * POST /api/auth/login
     * Authenticate user and return tokens
     *
     * Request body:
     * {
     *   "username": "john_doe",
     *   "password": "SecurePass123!"
     * }
     */
    @PostMapping("/login")
    public ResponseEntity<TokenResponseDto> login(
            @Valid @RequestBody LoginRequestDto request,
            HttpServletResponse response) {

        log.info("Login request for user: {}", request.getUsername());

        // Authenticate user and get tokens
        TokenResponseDto tokenResponse = authService.login(request);

        // Set refresh token in HTTP-only secure cookie
        long refreshTokenExpirationSeconds = 7 * 24 * 60 * 60; // 7 days
        cookieUtil.setRefreshTokenCookie(response, tokenResponse.getRefreshToken(), refreshTokenExpirationSeconds);

        // Return 200 OK with tokens
        return ResponseEntity.ok(tokenResponse);
    }

    /**
     * POST /api/auth/verify-email
     * Verify user's email with verification token
     *
     * Request body:
     * {
     *   "token": "eyJhbGciOiJIUzUxMiJ9..."
     * }
     *
     * Response: 200 OK with success message
     */
    @PostMapping("/verify-email")
    public ResponseEntity<ApiResponse> verifyEmail(
            @Valid @RequestBody VerifyEmailRequestDto request) {

        log.info("Email verification request");

        try {
            authService.verifyEmail(request.getToken());

            ApiResponse response = ApiResponse.builder()
                    .status(HttpStatus.OK.value())
                    .message("Email verified successfully. You can now login.")
                    .build();

            return ResponseEntity.ok(response);

        } catch (InvalidTokenException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(ApiResponse.builder()
                            .status(HttpStatus.BAD_REQUEST.value())
                            .message(e.getMessage())
                            .build());
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(ApiResponse.builder()
                            .status(HttpStatus.CONFLICT.value())
                            .message(e.getMessage())
                            .build());
        }
    }

    /**
     * POST /api/auth/resend-verification-email
     * Resend verification email to user
     *
     * Request body:
     * {
     *   "email": "user@example.com"
     * }
     *
     * Response: 200 OK with success message
     */
    @PostMapping("/resend-verification-email")
    public ResponseEntity<ApiResponse> resendVerificationEmail(
            @Valid @RequestBody ResendVerificationEmailRequestDto request) {

        log.info("Resend verification email request for: {}", request.getEmail());

        try {
            authService.resendVerificationEmail(request.getEmail());

            ApiResponse response = ApiResponse.builder()
                    .status(HttpStatus.OK.value())
                    .message("Verification email sent. Please check your inbox.")
                    .build();

            return ResponseEntity.ok(response);

        } catch (UserNotFoundException e) {
            // Don't reveal if email exists (security best practice)
            log.warn("Resend verification email for non-existent email: {}", request.getEmail());
            ApiResponse response = ApiResponse.builder()
                    .status(HttpStatus.OK.value())
                    .message("If email exists in our system, verification email will be sent.")
                    .build();
            return ResponseEntity.ok(response);

        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(ApiResponse.builder()
                            .status(HttpStatus.CONFLICT.value())
                            .message(e.getMessage())
                            .build());
        }
    }

    /**
     * POST /api/auth/forgot-password
     * Request password reset link
     *
     * Request body:
     * {
     *   "email": "user@example.com"
     * }
     *
     * Response: 200 OK (always, for security - don't reveal if email exists)
     */
    @PostMapping("/forgot-password")
    public ResponseEntity<ApiResponse> forgotPassword(
            @Valid @RequestBody ForgotPasswordRequestDto request) {

        log.info("Forgot password request for: {}", request.getEmail());

        try {
            authService.requestPasswordReset(request.getEmail());

            // Always return success (don't reveal if email exists)
            ApiResponse response = ApiResponse.builder()
                    .status(HttpStatus.OK.value())
                    .message("If an account exists with that email, a password reset link will be sent.")
                    .build();

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("Error processing forgot password request", e);

            // Still return generic message for security
            ApiResponse response = ApiResponse.builder()
                    .status(HttpStatus.OK.value())
                    .message("If an account exists with that email, a password reset link will be sent.")
                    .build();

            return ResponseEntity.ok(response);
        }
    }

    /**
     * POST /api/auth/reset-password
     * Reset password with valid reset token
     *
     * Request body:
     * {
     *   "token": "eyJhbGciOiJIUzUxMiJ9...",
     *   "newPassword": "NewSecurePass123!"
     * }
     *
     * Response: 200 OK with success message
     */
    @PostMapping("/reset-password")
    public ResponseEntity<ApiResponse> resetPassword(
            @Valid @RequestBody ResetPasswordRequestDto request) {

        log.info("Password reset request");

        try {
            authService.resetPassword(request.getToken(), request.getNewPassword());

            ApiResponse response = ApiResponse.builder()
                    .status(HttpStatus.OK.value())
                    .message("Password reset successfully. You can now login with your new password.")
                    .build();

            return ResponseEntity.ok(response);

        } catch (InvalidTokenException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(ApiResponse.builder()
                            .status(HttpStatus.BAD_REQUEST.value())
                            .message(e.getMessage())
                            .build());

        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(ApiResponse.builder()
                            .status(HttpStatus.BAD_REQUEST.value())
                            .message(e.getMessage())
                            .build());
        }
    }

    /**
     * GET /api/auth/verify-token/{token}
     * Check if a reset/verification token is valid
     *
     * Path parameters:
     * - token: verification or reset token
     *
     * Response: 200 OK if valid, 400 if invalid
     */
    @GetMapping("/verify-token/{token}")
    public ResponseEntity<ApiResponse> verifyToken(
            @PathVariable String token) {

        log.debug("Token validation request");

        try {
            // Try as verification token first
            var verificationToken = emailVerificationTokenRepository
                    .findValidToken(token, LocalDateTime.now());

            if (verificationToken.isPresent()) {
                ApiResponse response = ApiResponse.builder()
                        .status(HttpStatus.OK.value())
                        .message("Valid verification token")
                        .data(Map.of("type", "email_verification"))
                        .build();
                return ResponseEntity.ok(response);
            }

            // Try as password reset token
            var resetToken = passwordResetTokenRepository
                    .findValidToken(token, LocalDateTime.now());

            if (resetToken.isPresent()) {
                ApiResponse response = ApiResponse.builder()
                        .status(HttpStatus.OK.value())
                        .message("Valid password reset token")
                        .data(Map.of("type", "password_reset"))
                        .build();
                return ResponseEntity.ok(response);
            }

            // Token not found or expired
            return ResponseEntity.badRequest()
                    .body(ApiResponse.builder()
                            .status(HttpStatus.BAD_REQUEST.value())
                            .message("Invalid or expired token")
                            .build());

        } catch (Exception e) {
            log.error("Error verifying token", e);
            return ResponseEntity.badRequest()
                    .body(ApiResponse.builder()
                            .status(HttpStatus.BAD_REQUEST.value())
                            .message("Invalid or expired token")
                            .build());
        }
    }

    /**
     * POST /api/auth/refresh
     * Refresh expired access token using refresh token
     * Refresh token can be sent either in:
     * 1. Request body (for mobile apps)
     * 2. Cookie (for web browsers - automatic)
     *
     * Request body (optional):
     * {
     *   "refreshToken": "eyJhbGciOiJIUzUxMiJ9..."
     * }
     */
    @PostMapping("/refresh")
    public ResponseEntity<TokenResponseDto> refreshToken(
            @RequestBody(required = false) TokenRefreshRequestDto request,
            @CookieValue(value = "refreshToken", required = false) String cookieRefreshToken,
            HttpServletResponse response) {

        log.info("Token refresh request");

        // Get refresh token from request body or cookie
        String refreshToken = null;
        if (request != null && request.getRefreshToken() != null) {
            refreshToken = request.getRefreshToken();
        } else if (cookieRefreshToken != null) {
            refreshToken = cookieRefreshToken;
        }

        if (refreshToken == null) {
            log.warn("Refresh token not found in request body or cookie");
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(TokenResponseDto.builder()
                            .build());
        }

        // Refresh the access token
        TokenRefreshRequestDto refreshRequest = new TokenRefreshRequestDto(refreshToken);
        TokenResponseDto tokenResponse = authService.refreshAccessToken(refreshRequest);

        // Update refresh token cookie
        long refreshTokenExpirationSeconds = 7 * 24 * 60 * 60; // 7 days
        cookieUtil.setRefreshTokenCookie(response, tokenResponse.getRefreshToken(), refreshTokenExpirationSeconds);

        // Return 200 OK with new tokens
        return ResponseEntity.ok(tokenResponse);
    }

    /**
     * POST /api/auth/logout
     * Logout the authenticated user
     * Clears the refresh token cookie on client side
     */
    @PostMapping("/logout")
    public ResponseEntity<ApiResponse> logout(HttpServletResponse response) {
        // Get authenticated user
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        if (authentication != null && authentication.isAuthenticated()) {
            String username = authentication.getName();
            log.info("Logout request for user: {}", username);
        }

        // Clear refresh token cookie
        cookieUtil.clearRefreshTokenCookie(response);

        // Clear security context on server side
        SecurityContextHolder.clearContext();

        // Return success response
        ApiResponse apiResponse = ApiResponse.builder()
                .status(HttpStatus.OK.value())
                .message("Logged out successfully")
                .build();

        return ResponseEntity.ok(apiResponse);
    }

    /**
     * GET /api/auth/me
     * Get current authenticated user details
     * Requires valid JWT access token
     */
    @GetMapping("/me")
    public ResponseEntity<CurrentUserDto> getCurrentUser() {
        // Get authenticated user from security context
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        if (authentication == null || !authentication.isAuthenticated()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        String username = authentication.getName();
        log.debug("Fetching current user details: {}", username);

        // Get user details from service
        com.contentanalytics.entity.User user = authService.getCurrentUser(username);

        CurrentUserDto userDto = CurrentUserDto.builder()
                .id(user.getId())
                .username(user.getUsername())
                .email(user.getEmail())
                .firstName(user.getFirstName())
                .lastName(user.getLastName())
                .role(user.getRole().name())
                .status(user.getStatus().name())
                .emailVerified(user.getEmailVerified())
                .createdAt(user.getCreatedAt())
                .lastLoginAt(user.getLastLoginAt())
                .build();

        return ResponseEntity.ok(userDto);
    }

    /**
     * POST /api/auth/invalidate-tokens
     * Invalidate all tokens for current user (security incident, password change)
     * Forces user to login again
     */
    @PostMapping("/invalidate-tokens")
    public ResponseEntity<ApiResponse> invalidateAllTokens(HttpServletResponse response) {
        // Get authenticated user
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        if (authentication == null || !authentication.isAuthenticated()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        String username = authentication.getName();
        log.info("Invalidating all tokens for user: {}", username);

        // Get user details and invalidate tokens
        User user = authService.getCurrentUser(username);

                    authService.invalidateAllTokens(user.getId());

        // Clear refresh token cookie
        cookieUtil.clearRefreshTokenCookie(response);

        // Clear security context
        SecurityContextHolder.clearContext();

        ApiResponse apiResponse = ApiResponse.builder()
                .status(HttpStatus.OK.value())
                .message("All tokens have been invalidated. Please login again.")
                .build();

        return ResponseEntity.ok(apiResponse);
    }

    /**
     * GET /api/auth/validate-token
     * Validate if current access token is still valid
     * Used by frontend to check token status
     */
    @GetMapping("/validate-token")
    public ResponseEntity<TokenValidationDto> validateToken() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        boolean isValid = authentication != null && authentication.isAuthenticated();

        TokenValidationDto validationDto = TokenValidationDto.builder()
                .valid(isValid)
                .username(isValid ? authentication.getName() : null)
                .build();

        return ResponseEntity.ok(validationDto);
    }

}

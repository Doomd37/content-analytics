package com.contentanalytics.controller;

import com.contentanalytics.dto.*;
import com.contentanalytics.service.ProfileService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.util.Map;

@RestController
@RequestMapping("/api/profile")
@RequiredArgsConstructor
@Slf4j
public class ProfileController {

    private final ProfileService profileService;

    /**
     * GET /api/profile
     * Get authenticated user's complete profile
     *
     * Response: UserProfileDto with all profile information
     */
    @GetMapping
    public ResponseEntity<UserProfileDto> getProfile() {
        String userId = getCurrentUserId();
        log.debug("Fetching profile for user: {}", userId);

        UserProfileDto profile = profileService.getUserProfile(userId);

        return ResponseEntity.ok(profile);
    }

    /**
     * PUT /api/profile
     * Update authenticated user's profile information
     *
     * Request body: UpdateProfileRequestDto
     * {
     *   "firstName": "Jane",
     *   "lastName": "Doe",
     *   "bio": "Data scientist interested in AI",
     *   "location": "San Francisco, USA",
     *   "timezone": "PST",
     *   "website": "https://janedoe.com",
     *   "company": "TechCorp",
     *   "jobTitle": "Senior Data Scientist",
     *   "emailNotifications": true,
     *   "publicProfile": false
     * }
     *
     * Response: Updated UserProfileDto
     */
    @PutMapping
    public ResponseEntity<UserProfileDto> updateProfile(
            @Valid @RequestBody UpdateProfileRequestDto request) {

        String userId = getCurrentUserId();
        log.info("Profile update request from user: {}", userId);

        try {
            UserProfileDto updatedProfile = profileService.updateProfile(userId, request);

            return ResponseEntity.ok(updatedProfile);

        } catch (IllegalArgumentException e) {
            log.warn("Invalid profile update: {}", e.getMessage());
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * POST /api/profile/picture
     * Upload profile picture
     *
     * Multipart form data:
     * - file: Image file (JPEG, PNG, WebP, max 5MB)
     *
     * Server processes:
     * - Creates 3 sizes: 150x150 (thumbnail), 400x400 (medium), 800x800 (full)
     * - Optimizes images (compression, quality)
     * - Stores to S3 or local storage
     * - Deactivates old picture
     *
     * Response: Updated UserProfileDto with new avatar URLs
     */
    @PostMapping("/picture")
    public ResponseEntity<UserProfileDto> uploadProfilePicture(
            @RequestParam("file") MultipartFile file,
            HttpServletRequest httpRequest) {

        String userId = getCurrentUserId();
        String ipAddress = getClientIp(httpRequest);

        log.info("Profile picture upload request from user: {}", userId);

        try {
            // Upload and process
            UserProfileDto profile = profileService.uploadProfilePicture(userId, file, ipAddress);

            log.info("Profile picture uploaded successfully for user: {}", userId);

            return ResponseEntity.status(HttpStatus.CREATED).body(profile);

        } catch (IllegalArgumentException e) {
            log.warn("Invalid image file: {}", e.getMessage());
            return ResponseEntity.badRequest().build();

        } catch (Exception e) {
            log.error("Error uploading profile picture", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    /**
     * DELETE /api/profile/picture
     * Delete profile picture (reverts to Gravatar)
     *
     * Response: ProfilePictureDeleteResponseDto with new avatar URL
     */
    @DeleteMapping("/picture")
    public ResponseEntity<ProfilePictureDeleteResponseDto> deleteProfilePicture() {

        String userId = getCurrentUserId();
        log.info("Delete profile picture request from user: {}", userId);

        try {
            profileService.deleteProfilePicture(userId);

            // Fetch updated profile to get new gravatar URL
            UserProfileDto updatedProfile = profileService.getUserProfile(userId);

            ProfilePictureDeleteResponseDto response = ProfilePictureDeleteResponseDto.builder()
                    .status("SUCCESS")
                    .message("Profile picture deleted successfully")
                    .avatarUrl(updatedProfile.getAvatarUrl())
                    .build();

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("Error deleting profile picture", e);
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(ProfilePictureDeleteResponseDto.builder()
                            .status("ERROR")
                            .message(e.getMessage())
                            .build());
        }
    }

    /**
     * POST /api/profile/change-password
     * Change user's password (requires current password)
     *
     * Request body: ChangePasswordRequestDto
     * {
     *   "oldPassword": "CurrentPassword123!",
     *   "newPassword": "NewPassword456!",
     *   "confirmPassword": "NewPassword456!"
     * }
     *
     * Response: 200 OK with success message
     *
     * Security:
     * - Requires verification of current password
     * - Rate limited (1 change per hour)
     * - Prevents reuse of last 5 passwords
     * - Invalidates all other sessions (forces re-login)
     * - Sends email notification
     */
    @PostMapping("/change-password")
    public ResponseEntity<PasswordChangeResponseDto> changePassword(
            @Valid @RequestBody ChangePasswordRequestDto request,
            HttpServletRequest httpRequest) {

        String userId = getCurrentUserId();
        log.info("Password change request from user: {}", userId);

        // Validate password confirmation
        if (!request.isPasswordConfirmed()) {
            log.warn("Password confirmation mismatch for user: {}", userId);
            return ResponseEntity.badRequest()
                    .body(PasswordChangeResponseDto.builder()
                            .status("ERROR")
                            .message("Passwords do not match")
                            .build());
        }

        try {
            // Get client IP and user agent for audit trail
            String ipAddress = getClientIp(httpRequest);
            String userAgent = httpRequest.getHeader("User-Agent");

            request.setIpAddress(ipAddress);
            request.setUserAgent(userAgent);

            // Change password
            profileService.changePassword(userId, request);

            PasswordChangeResponseDto response = PasswordChangeResponseDto.builder()
                    .status("SUCCESS")
                    .message("Password changed successfully. All sessions have been logged out for security.")
                    .changedAt(LocalDateTime.now())
                    .allSessionsLoggedOut(true)
                    .build();

            return ResponseEntity.ok(response);

        } catch (IllegalArgumentException e) {
            log.warn("Password change validation error: {}", e.getMessage());
            return ResponseEntity.badRequest()
                    .body(PasswordChangeResponseDto.builder()
                            .status("ERROR")
                            .message(e.getMessage())
                            .build());
        }
    }

    /**
     * GET /api/profile/validate-field/{field}
     * Validate a profile field (for real-time validation on frontend)
     *
     * Path parameters:
     * - field: Field name to validate (website, phoneNumber, etc)
     *
     * Query parameters:
     * - value: Field value to validate
     *
     * Response: ProfileValidationResponseDto
     */
    @GetMapping("/validate-field/{field}")
    public ResponseEntity<ProfileValidationResponseDto> validateField(
            @PathVariable String field,
            @RequestParam String value) {

        log.debug("Field validation request for: {}", field);

        ProfileValidationResponseDto response = validateProfileField(field, value);

        return ResponseEntity.ok(response);
    }

    /**
     * Helper method to validate individual profile fields
     */
    private ProfileValidationResponseDto validateProfileField(String field, String value) {
        return switch (field) {
            case "website" -> {
                boolean valid = value == null || value.isEmpty() ||
                        value.matches("^(https?://)?([\\da-z\\.-]+)\\.([a-z\\.]{2,6})([/\\w \\.-]*)*/?$");
                yield ProfileValidationResponseDto.builder()
                        .field(field)
                        .valid(valid)
                        .message(valid ? "Valid website URL" : "Invalid website URL format")
                        .build();
            }
            case "phoneNumber" -> {
                boolean valid = value == null || value.isEmpty() ||
                        value.matches("^[+]?[0-9]{1,20}$");
                yield ProfileValidationResponseDto.builder()
                        .field(field)
                        .valid(valid)
                        .message(valid ? "Valid phone number" : "Invalid phone number format")
                        .build();
            }
            case "timezone" -> {
                boolean valid = isValidTimezone(value);
                yield ProfileValidationResponseDto.builder()
                        .field(field)
                        .valid(valid)
                        .message(valid ? "Valid timezone" : "Invalid timezone")
                        .build();
            }
            default -> ProfileValidationResponseDto.builder()
                    .field(field)
                    .valid(true)
                    .message("Field valid")
                    .build();
        };
    }

    /**
     * Check if timezone is valid
     */
    private boolean isValidTimezone(String timezone) {
        if (timezone == null || timezone.isEmpty()) {
            return true;
        }
        try {
            java.time.ZoneId.of(timezone);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Get current authenticated user ID from JWT claims
     */
    private String getCurrentUserId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        if (authentication == null || !authentication.isAuthenticated()) {
            throw new IllegalStateException("User is not authenticated");
        }

        Object details = authentication.getDetails();
        if (details instanceof Map) {
            @SuppressWarnings("unchecked")
            Map<String, Object> detailsMap = (Map<String, Object>) details;
            String userId = (String) detailsMap.get("userId");

            if (userId == null) {
                throw new IllegalStateException("User ID not found in authentication");
            }

            return userId;
        }

        throw new IllegalStateException("Invalid authentication details");
    }

    /**
     * Get client IP address from request (handles proxies)
     */
    private String getClientIp(HttpServletRequest request) {
        String xForwardedFor = request.getHeader("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isEmpty()) {
            return xForwardedFor.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }

}

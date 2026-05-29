package com.contentanalytics.controller;

import com.contentanalytics.dto.*;
import com.contentanalytics.service.DocumentService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/documents")
@RequiredArgsConstructor
@Slf4j
public class DocumentController {

    private final DocumentService documentService;

    /**
     * POST /api/documents/upload
     * Upload a document for analysis
     *
     * Multipart form data:
     * - file: PDF, TXT, DOCX, PPTX (max 50MB)
     * - description: (optional) description of document
     *
     * Response: DocumentResponseDto with status UPLOADED
     */
    @PostMapping("/upload")
    public ResponseEntity<DocumentResponseDto> uploadDocument(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "description", required = false) String description) {

        // Get authenticated user
        String userId = getCurrentUserId();
        log.info("Document upload request from user: {}", userId);

        // Create request DTO
        DocumentUploadRequestDto request = new DocumentUploadRequestDto(file, description);

        // Upload document
        DocumentResponseDto response = documentService.uploadDocument(userId, request);

        log.info("Document uploaded successfully: {}", response.getId());

        // Return 201 CREATED
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * GET /api/documents
     * List user's documents with pagination
     *
     * Query parameters:
     * - page: page number (0-indexed, default 0)
     * - size: items per page (default 10, max 100)
     *
     * Response: PaginationResponseDto containing list of documents
     */
    @GetMapping
    public ResponseEntity<PaginationResponseDto<DocumentListItemDto>> listDocuments(
            @RequestParam(value = "page", defaultValue = "0") int page,
            @RequestParam(value = "size", defaultValue = "10") int size) {

        String userId = getCurrentUserId();
        log.debug("Listing documents for user: {} - page: {}, size: {}", userId, page, size);

        // Get paginated list
        PaginationResponseDto<DocumentListItemDto> response = documentService.listDocuments(userId, page, size);

        log.debug("Retrieved {} documents for user: {}", response.getContent().size(), userId);

        return ResponseEntity.ok(response);
    }

    /**
     * GET /api/documents/{documentId}
     * Get detailed document information with AI analysis results
     *
     * Path parameters:
     * - documentId: UUID of document
     *
     * Response: DocumentResponseDto with full analysis
     */
    @GetMapping("/{documentId}")
    public ResponseEntity<DocumentResponseDto> getDocument(
            @PathVariable String documentId) {

        String userId = getCurrentUserId();
        log.debug("Fetching document: {} for user: {}", documentId, userId);

        // Get document details
        DocumentResponseDto response = documentService.getDocument(documentId, userId);

        log.debug("Document retrieved: {}", documentId);

        return ResponseEntity.ok(response);
    }

    /**
     * POST /api/documents/search
     * Advanced search and filter documents
     *
     * Request body: DocumentSearchFilterDto
     * {
     *   "fileName": "report",
     *   "status": "COMPLETED",
     *   "sentiment": "POSITIVE",
     *   "topic": "finance",
     *   "startDate": "2024-01-01T00:00:00",
     *   "endDate": "2024-12-31T23:59:59",
     *   "pageNumber": 0,
     *   "pageSize": 20,
     *   "sortBy": "createdAt",
     *   "sortDirection": "DESC"
     * }
     *
     * Response: PaginationResponseDto with filtered results
     */
    @PostMapping("/search")
    public ResponseEntity<PaginationResponseDto<DocumentListItemDto>> searchDocuments(
            @Valid @RequestBody DocumentSearchFilterDto filter) {

        String userId = getCurrentUserId();
        log.info("Search request from user: {} with filters: {}", userId, filter);

        // Set defaults if not provided
        if (filter.getPageNumber() < 0) {
            filter.setPageNumber(0);
        }
        if (filter.getPageSize() <= 0) {
            filter.setPageSize(10);
        }
        if (filter.getSortBy() == null || filter.getSortBy().isBlank()) {
            filter.setSortBy("createdAt");
        }

        // Search documents
        PaginationResponseDto<DocumentListItemDto> response = documentService.searchDocuments(userId, filter);

        log.info("Search returned {} results for user: {}", response.getContent().size(), userId);

        return ResponseEntity.ok(response);
    }

    /**
     * DELETE /api/documents/{documentId}
     * Delete a document (removes from storage and database)
     *
     * Path parameters:
     * - documentId: UUID of document to delete
     *
     * Response: 204 NO CONTENT on success
     */
    @DeleteMapping("/{documentId}")
    public ResponseEntity<Void> deleteDocument(
            @PathVariable String documentId) {

        String userId = getCurrentUserId();
        log.info("Delete request for document: {} from user: {}", documentId, userId);

        // Delete document
        documentService.deleteDocument(documentId, userId);

        log.info("Document deleted: {} by user: {}", documentId, userId);

        // Return 204 NO CONTENT
        return ResponseEntity.noContent().build();
    }

    /**
     * GET /api/documents/stats/overview
     * Get statistics about user's documents
     *
     * Response: DocumentStatsDto
     * {
     *   "totalDocuments": 42,
     *   "completedDocuments": 38,
     *   "processingDocuments": 2,
     *   "failedDocuments": 2,
     *   "totalWords": 125450,
     *   "averageWordCount": 2987.38
     * }
     */
    @GetMapping("/stats/overview")
    public ResponseEntity<DocumentStatsDto> getDocumentStats() {

        String userId = getCurrentUserId();
        log.debug("Fetching document stats for user: {}", userId);

        // Get stats
        DocumentStatsDto stats = documentService.getDocumentStats(userId);

        log.debug("Stats retrieved for user: {}", userId);

        return ResponseEntity.ok(stats);
    }

    /**
     * GET /api/documents/{documentId}/download
     * Download original document file
     *
     * This endpoint prepares the download response
     * Frontend handles actual file download
     */
    @GetMapping("/{documentId}/download")
    public ResponseEntity<ApiResponse> getDownloadUrl(
            @PathVariable String documentId) {

        String userId = getCurrentUserId();
        log.info("Download request for document: {} from user: {}", documentId, userId);

        // Verify ownership
        DocumentResponseDto document = documentService.getDocument(documentId, userId);

        // In production, generate pre-signed S3 URL or serve from storage
        ApiResponse response = ApiResponse.builder()
                .status(HttpStatus.OK.value())
                .message("Download URL generated")
                .data(document.getFileName())
                .build();

        return ResponseEntity.ok(response);
    }

    /**
     * GET /api/documents/{documentId}/status
     * Get processing status of a document
     *
     * Used by frontend to poll for completion
     */
    @GetMapping("/{documentId}/status")
    public ResponseEntity<DocumentStatusDto> getDocumentStatus(
            @PathVariable String documentId) {

        String userId = getCurrentUserId();
        log.debug("Fetching status for document: {} from user: {}", documentId, userId);

        // Get document
        DocumentResponseDto document = documentService.getDocument(documentId, userId);

        // Create status DTO
        DocumentStatusDto statusDto = DocumentStatusDto.builder()
                .documentId(documentId)
                .fileName(document.getFileName())
                .status(document.getStatus())
                .processingProgress(document.getProcessingProgress())
                .processingError(document.getProcessingError())
                .isCompleted(document.getStatus().equals("COMPLETED"))
                .isFailed(document.getStatus().equals("FAILED"))
                .processingCompletedAt(document.getProcessingCompletedAt())
                .build();

        return ResponseEntity.ok(statusDto);
    }

    /**
     * POST /api/documents/{documentId}/retry
     */
    @PostMapping("/{documentId}/retry")
    public ResponseEntity<ApiResponse> retryProcessing(@PathVariable String documentId) {
        String userId = getCurrentUserId();
        log.info("Retry processing request for document: {} from user: {}", documentId, userId);

        try {
            documentService.retryFailedDocument(documentId, userId);

            ApiResponse response = ApiResponse.builder()
                    .status(HttpStatus.OK.value())
                    .message("Document queued for re-processing")
                    .build();

            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(
                    ApiResponse.builder()
                            .status(HttpStatus.BAD_REQUEST.value())
                            .message(e.getMessage())
                            .build()
            );
        }
    }

    /**
     * Helper method to get current authenticated user ID from security context
     */
    /**
     * Helper method to get current authenticated user ID from JWT claims
     */
    private String getCurrentUserId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        if (authentication == null || !authentication.isAuthenticated()) {
            log.error("User is not authenticated");
            throw new IllegalStateException("User is not authenticated");
        }

        // Get user ID from authentication details (set by JwtAuthenticationFilter)
        Object details = authentication.getDetails();

        if (details instanceof java.util.Map) {
            @SuppressWarnings("unchecked")
            java.util.Map<String, Object> detailsMap = (java.util.Map<String, Object>) details;
            String userId = (String) detailsMap.get("userId");

            if (userId == null) {
                log.error("User ID not found in authentication details");
                throw new IllegalStateException("User ID not found in authentication");
            }

            return userId;
        }

        log.error("Invalid authentication details format");
        throw new IllegalStateException("Invalid authentication details");
    }
}

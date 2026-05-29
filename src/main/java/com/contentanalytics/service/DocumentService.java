package com.contentanalytics.service;

import com.contentanalytics.dto.*;
import com.contentanalytics.entity.Document;
import com.contentanalytics.entity.DocumentStatus;
import com.contentanalytics.entity.User;
import com.contentanalytics.exception.DocumentNotFoundException;
import com.contentanalytics.exception.UnauthorizedException;
import com.contentanalytics.exception.UserNotFoundException;
import com.contentanalytics.repository.DocumentRepository;
import com.contentanalytics.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.util.Objects;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class DocumentService {

    private final DocumentRepository documentRepository;
    private final UserRepository userRepository;
    private final StorageService storageService; // We'll create this next
    private final DocumentProcessingService documentProcessingService; // We'll create this next

    @Value("${app.max-upload-size:52428800}") // 50MB default
    private long maxUploadSize;

    @Value("${app.allowed-file-types:pdf,txt,docx,pptx}")
    private String allowedFileTypes;

    /**
     * Upload a document
     * Validates file, saves to storage, creates DB record
     */
    public DocumentResponseDto uploadDocument(String userId, DocumentUploadRequestDto request) {
        log.info("Document upload started for user: {}", userId);

        MultipartFile file = request.getFile();

        // Validate user exists
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new UserNotFoundException("User not found"));

        // Validate file is not empty
        if (file.isEmpty()) {
            throw new IllegalArgumentException("File cannot be empty");
        }

        // Validate file size
        if (file.getSize() > maxUploadSize) {
            throw new IllegalArgumentException("File size exceeds maximum allowed size of " + maxUploadSize + " bytes");
        }

        // Validate file type
        String fileName = file.getOriginalFilename();
        String fileExtension = getFileExtension(fileName);
        if (!isAllowedFileType(fileExtension)) {
            throw new IllegalArgumentException("File type ." + fileExtension + " is not allowed");
        }

        // Save file to storage (S3, local, etc.)
        String fileKey = storageService.saveFile(file, userId);
        log.debug("File saved to storage with key: {}", fileKey);

        // Create document record in database
        Document document = Document.builder()
                .fileName(fileName)
                .fileKey(fileKey)
                .fileSize(file.getSize())
                .mimeType(file.getContentType())
                .pageCount(0) // Will be updated during processing
                .description(request.getDescription())
                .status(DocumentStatus.UPLOADED)
                .processingProgress(0f)
                .user(user)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        document = documentRepository.save(document);
        log.info("Document created with ID: {} for user: {}", document.getId(), userId);

        // Queue for async processing
        documentProcessingService.queueForProcessing(document.getId());

        return mapToResponseDto(document);
    }

    /**
     * Get document with detailed analysis
     * Verify ownership before returning
     */
    public DocumentResponseDto getDocument(String documentId, String userId) {
        log.debug("Fetching document: {} for user: {}", documentId, userId);

        Document document = documentRepository.findByIdAndUserId(documentId, userId)
                .orElseThrow(() -> {
                    log.warn("Unauthorized access attempt to document: {} by user: {}", documentId, userId);
                    return new DocumentNotFoundException("Document not found");
                });

        return mapToResponseDto(document);
    }

    /**
     * List documents for user with pagination
     */
    public PaginationResponseDto<DocumentListItemDto> listDocuments(String userId, int pageNumber, int pageSize) {
        log.debug("Listing documents for user: {} - page: {}, size: {}", userId, pageNumber, pageSize);

        // Validate pagination params
        pageNumber = Math.max(0, pageNumber);
        pageSize = Math.min(pageSize, 100); // Max 100 items per page
        pageSize = Math.max(pageSize, 1);

        // Create pageable
        Pageable pageable = PageRequest.of(pageNumber, pageSize, Sort.by("createdAt").descending());

        // Fetch page
        Page<Document> page = documentRepository.findByUserId(userId, pageable);

        // Convert to DTOs
        var content = page.getContent()
                .stream()
                .map(this::mapToListItemDto)
                .collect(Collectors.toList());

        return PaginationResponseDto.<DocumentListItemDto>builder()
                .content(content)
                .pageNumber(page.getNumber())
                .pageSize(page.getSize())
                .totalElements(page.getTotalElements())
                .totalPages(page.getTotalPages())
                .isFirst(page.isFirst())
                .isLast(page.isLast())
                .hasNext(page.hasNext())
                .hasPrevious(page.hasPrevious())
                .build();
    }

    /**
     * Search & filter documents
     */
    public PaginationResponseDto<DocumentListItemDto> searchDocuments(String userId, DocumentSearchFilterDto filter) {
        log.debug("Searching documents for user: {} with filters", userId);

        // Validate pagination
        int pageNumber = Math.max(0, filter.getPageNumber());
        int pageSize = Math.min(filter.getPageSize(), 100);
        pageSize = Math.max(pageSize, 1);

        // Parse sort direction
        Sort.Direction direction = "DESC".equalsIgnoreCase(filter.getSortDirection())
                ? Sort.Direction.DESC
                : Sort.Direction.ASC;

        String sortBy = filter.getSortBy() != null ? filter.getSortBy() : "createdAt";
        Pageable pageable = PageRequest.of(pageNumber, pageSize, Sort.by(direction, sortBy));

        // Execute appropriate query based on filters
        Page<Document> page;

        if (filter.getFileName() != null && !filter.getFileName().isBlank()) {
            page = documentRepository.searchByFileName(userId, filter.getFileName(), pageable);
        } else if (filter.getStatus() != null) {
            DocumentStatus status = DocumentStatus.valueOf(filter.getStatus().toUpperCase());
            page = documentRepository.findByUserIdAndStatus(userId, status, pageable);
        } else if (filter.getSentiment() != null) {
            page = documentRepository.findBySentiment(userId, filter.getSentiment(), pageable);
        } else if (filter.getTopic() != null) {
            page = documentRepository.findByTopic(userId, filter.getTopic(), pageable);
        } else if (filter.getStartDate() != null && filter.getEndDate() != null) {
            page = documentRepository.findByUserIdAndDateRange(userId, filter.getStartDate(), filter.getEndDate(), pageable);
        } else {
            page = documentRepository.findByUserId(userId, pageable);
        }

        // Convert results
        var content = page.getContent()
                .stream()
                .map(this::mapToListItemDto)
                .collect(Collectors.toList());

        return PaginationResponseDto.<DocumentListItemDto>builder()
                .content(content)
                .pageNumber(page.getNumber())
                .pageSize(page.getSize())
                .totalElements(page.getTotalElements())
                .totalPages(page.getTotalPages())
                .isFirst(page.isFirst())
                .isLast(page.isLast())
                .hasNext(page.hasNext())
                .hasPrevious(page.hasPrevious())
                .build();
    }

    /**
     * Retry processing for failed document
     */
    public void retryFailedDocument(String documentId, String userId) {
        Document document = documentRepository.findByIdAndUserId(documentId, userId)
                .orElseThrow(() -> new DocumentNotFoundException("Document not found"));

        if (document.getStatus() != DocumentStatus.FAILED) {
            throw new IllegalArgumentException("Only failed documents can be retried");
        }

        log.info("Retrying processing for document: {}", documentId);
        documentProcessingService.retryDocumentProcessing(documentId);
    }

    /**
     * Delete document
     * Verify ownership, delete from storage and DB
     */
    public void deleteDocument(String documentId, String userId) {
        log.info("Deleting document: {} for user: {}", documentId, userId);

        Document document = documentRepository.findByIdAndUserId(documentId, userId)
                .orElseThrow(() -> new DocumentNotFoundException("Document not found"));

        // Delete from storage
        storageService.deleteFile(document.getFileKey());
        log.debug("File deleted from storage: {}", document.getFileKey());

        // Delete from database
        documentRepository.delete(document);
        log.info("Document deleted: {}", documentId);
    }

    /**
     * Get document statistics
     */
    public DocumentStatsDto getDocumentStats(String userId) {
        log.debug("Fetching document stats for user: {}", userId);

        long total = documentRepository.countByUserId(userId);
        long completed = documentRepository.countByUserIdAndStatus(userId, DocumentStatus.COMPLETED);
        long processing = documentRepository.countByUserIdAndStatus(userId, DocumentStatus.PROCESSING);
        long failed = documentRepository.countByUserIdAndStatus(userId, DocumentStatus.FAILED);

        // Calculate total words
        Object statsObject = documentRepository.getUserDocumentStats(userId);
        long totalWords = 0;
        double avgWords = 0;

        // Extract from native query result
        if (statsObject instanceof Object[]) {
            Object[] stats = (Object[]) statsObject;
            totalWords = ((Number) stats[4]).longValue();
            avgWords = total > 0 ? (double) totalWords / total : 0;
        }

        return DocumentStatsDto.builder()
                .totalDocuments(total)
                .completedDocuments(completed)
                .processingDocuments(processing)
                .failedDocuments(failed)
                .totalWords(totalWords)
                .averageWordCount(avgWords)
                .build();
    }

    /**
     * Update document processing status
     * Called by async processing service
     */
    @Transactional
    public void updateProcessingStatus(String documentId, DocumentStatus status, Float progress, String error) {
        Document document = documentRepository.findById(documentId)
                .orElseThrow(() -> new DocumentNotFoundException("Document not found"));

        document.setStatus(status);
        document.setProcessingProgress(progress);

        if (status == DocumentStatus.PROCESSING && document.getProcessingStartedAt() == null) {
            document.setProcessingStartedAt(LocalDateTime.now());
        }

        if (status == DocumentStatus.COMPLETED) {
            document.setProcessingCompletedAt(LocalDateTime.now());
        }

        if (error != null) {
            document.setProcessingError(error);
        }

        documentRepository.save(document);
        log.debug("Document {} status updated to: {} (progress: {}%)", documentId, status, progress);
    }

    // Helper methods

    private String getFileExtension(String fileName) {
        if (fileName == null || !fileName.contains(".")) {
            return "";
        }
        return fileName.substring(fileName.lastIndexOf(".") + 1).toLowerCase();
    }

    private boolean isAllowedFileType(String extension) {
        String[] allowed = allowedFileTypes.split(",");
        for (String type : allowed) {
            if (type.trim().equals(extension)) {
                return true;
            }
        }
        return false;
    }

    private DocumentResponseDto mapToResponseDto(Document document) {
        return DocumentResponseDto.builder()
                .id(document.getId())
                .fileName(document.getFileName())
                .fileSize(document.getFileSize())
                .mimeType(document.getMimeType())
                .pageCount(document.getPageCount())
                .description(document.getDescription())
                .status(document.getStatus().name())
                .processingProgress(document.getProcessingProgress())
                .processingError(document.getProcessingError())
                .createdAt(document.getCreatedAt())
                .updatedAt(document.getUpdatedAt())
                .processingStartedAt(document.getProcessingStartedAt())
                .processingCompletedAt(document.getProcessingCompletedAt())
                .extractedText(document.getExtractedText())
                .wordCount(document.getWordCount())
                .characterCount(document.getCharacterCount())
                .summary(document.getSummary())
                .keyTopics(document.getKeyTopics())
                .sentiment(document.getSentiment())
                .confidenceScore(document.getConfidenceScore())
                .build();
    }

    private DocumentListItemDto mapToListItemDto(Document document) {
        return DocumentListItemDto.builder()
                .id(document.getId())
                .fileName(document.getFileName())
                .fileSize(document.getFileSize())
                .status(document.getStatus().name())
                .processingProgress(document.getProcessingProgress())
                .sentiment(document.getSentiment())
                .wordCount(document.getWordCount())
                .createdAt(document.getCreatedAt())
                .processingCompletedAt(document.getProcessingCompletedAt())
                .build();
    }

}

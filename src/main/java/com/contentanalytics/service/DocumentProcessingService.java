package com.contentanalytics.service;

import com.contentanalytics.entity.Document;
import com.contentanalytics.entity.DocumentStatus;
import com.contentanalytics.repository.DocumentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.concurrent.CompletableFuture;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class DocumentProcessingService {

    private final WebSocketService webSocketService;
    private final DocumentRepository documentRepository;
    private final FileExtractionService fileExtractionService;
    private final AnthropicService anthropicService;

    @Value("${app.anthropic.max-tokens:2048}")
    private int maxTokens;

    /**
     * Queue document for async processing
     */
    public void queueForProcessing(String documentId) {
        log.info("Queuing document for processing: {}", documentId);
        processDocumentAsync(documentId);
    }

    /**
     * Async document processing
     * Extracts text, analyzes with Claude API
     */
    @Async
    public void processDocumentAsync(String documentId) {
        try {
            processDocument(documentId);
        } catch (Exception e) {
            log.error("Error processing document: {}", documentId, e);
            updateProcessingStatus(documentId, DocumentStatus.FAILED, 0f, e.getMessage());
        }
    }

    /**
     * Main document processing logic
     */
    /**
     * Main document processing logic
     */
    private void processDocument(String documentId) {
        log.info("Starting processing for document: {}", documentId);

        // Get document
        Document document = documentRepository.findById(documentId)
                .orElseThrow(() -> new RuntimeException("Document not found: " + documentId));

        String userId = document.getUser().getId();

        try {
            long startTime = System.currentTimeMillis();

            // Step 1: Update status to PROCESSING
            updateProcessingStatus(documentId, DocumentStatus.PROCESSING, 10f, null);
            webSocketService.sendDocumentProgress(userId, documentId, document.getFileName(), 10f, "PROCESSING", "extracting_text");

            // Step 2: Extract text from file
            log.debug("Extracting text from document: {}", documentId);
            String extractedText = fileExtractionService.extractText(document.getFileKey(), document.getMimeType());

            if (extractedText == null || extractedText.isBlank()) {
                throw new RuntimeException("Failed to extract text from document");
            }

            updateProcessingStatus(documentId, DocumentStatus.PROCESSING, 30f, null);
            webSocketService.sendDocumentProgress(userId, documentId, document.getFileName(), 30f, "PROCESSING", "analyzing");
            log.debug("Text extracted. Length: {} characters", extractedText.length());

            // Step 3: Analyze with Claude API
            log.debug("Analyzing document with Claude API: {}", documentId);
            DocumentAnalysisResult analysis = anthropicService.analyzeDocument(extractedText);

            updateProcessingStatus(documentId, DocumentStatus.PROCESSING, 70f, null);
            webSocketService.sendDocumentProgress(userId, documentId, document.getFileName(), 70f, "PROCESSING", "saving_results");
            log.debug("Analysis complete for document: {}", documentId);

            // Step 4: Update document with results
            document.setStatus(DocumentStatus.COMPLETED);
            document.setProcessingProgress(100f);
            document.setExtractedText(extractedText);
            document.setWordCount(countWords(extractedText));
            document.setCharacterCount(extractedText.length());
            document.setSummary(analysis.getSummary());
            document.setKeyTopics(analysis.getKeyTopics());
            document.setSentiment(analysis.getSentiment());
            document.setConfidenceScore(analysis.getConfidenceScore());
            document.setProcessingCompletedAt(java.time.LocalDateTime.now());

            documentRepository.save(document);

            // Send completion update
            long processingTime = System.currentTimeMillis() - startTime;
            webSocketService.sendDocumentCompleted(
                    userId,
                    documentId,
                    document.getFileName(),
                    analysis.getSummary(),
                    analysis.getKeyTopics(),
                    analysis.getSentiment(),
                    analysis.getConfidenceScore(),
                    document.getWordCount(),
                    processingTime
            );

            log.info("Document processing completed successfully: {}", documentId);

        } catch (Exception e) {
            log.error("Error during document processing: {}", documentId, e);
            updateProcessingStatus(documentId, DocumentStatus.FAILED, 0f, e.getMessage());

            // Send failure update
            webSocketService.sendDocumentFailed(
                    userId,
                    documentId,
                    document.getFileName(),
                    e.getMessage(),
                    "PROCESSING_ERROR"
            );
        }
    }

    /**
     * Update document processing status
     */
    private void updateProcessingStatus(String documentId, DocumentStatus status, Float progress, String error) {
        Document document = documentRepository.findById(documentId)
                .orElse(null);

        if (document == null) {
            log.warn("Document not found for status update: {}", documentId);
            return;
        }

        document.setStatus(status);
        document.setProcessingProgress(progress);

        if (status == DocumentStatus.PROCESSING && document.getProcessingStartedAt() == null) {
            document.setProcessingStartedAt(java.time.LocalDateTime.now());
        }

        if (status == DocumentStatus.COMPLETED) {
            document.setProcessingCompletedAt(java.time.LocalDateTime.now());
        }

        if (error != null) {
            document.setProcessingError(error);
        }

        documentRepository.save(document);
        log.debug("Document {} status updated to: {} (progress: {}%)", documentId, status, progress);
    }

    /**
     * Retry processing for failed document
     */
    @Async
    public void retryDocumentProcessing(String documentId) {
        log.info("Retrying processing for document: {}", documentId);

        Document document = documentRepository.findById(documentId)
                .orElseThrow(() -> new RuntimeException("Document not found: " + documentId));

        // Reset status
        document.setStatus(DocumentStatus.UPLOADED);
        document.setProcessingProgress(0f);
        document.setProcessingError(null);
        documentRepository.save(document);

        // Process again
        processDocumentAsync(documentId);
    }

    /**
     * Count words in text
     */
    private Integer countWords(String text) {
        if (text == null || text.isBlank()) {
            return 0;
        }
        return text.trim().split("\\s+").length;
    }

}

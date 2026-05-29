package com.contentanalytics.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class WebSocketMessage {

    // Message metadata
    private String messageId;
    private String messageType; // PROGRESS, COMPLETED, FAILED, STATUS
    private LocalDateTime timestamp;

    // Document info
    private String documentId;
    private String fileName;
    private String userId;

    // Progress data
    private Float progress; // 0-100%
    private String status; // UPLOADED, PROCESSING, COMPLETED, FAILED

    // Analysis results (on completion)
    private String summary;
    private String keyTopics;
    private String sentiment;
    private Float confidenceScore;
    private Integer wordCount;

    // Error handling
    private String errorMessage;
    private String errorCode;

    // Additional metadata
    private Long processingTimeMs;
    private String processingStage; // "extracting_text", "analyzing", "saving_results"

}

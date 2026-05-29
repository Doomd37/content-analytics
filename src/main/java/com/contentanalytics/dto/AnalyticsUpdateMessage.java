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
public class AnalyticsUpdateMessage {

    private String userId;
    private Long totalDocuments;
    private Long completedDocuments;
    private Long processingDocuments;
    private Long failedDocuments;
    private Long totalWords;
    private Double averageProcessingTimeMs;
    private LocalDateTime lastUpdateAt;

}

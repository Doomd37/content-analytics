package com.contentanalytics.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DocumentStatsDto {

    private Long totalDocuments;
    private Long completedDocuments;
    private Long processingDocuments;
    private Long failedDocuments;
    private Long totalWords;
    private Double averageWordCount;

}
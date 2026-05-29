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
public class DocumentSearchFilterDto {

    private String status; // Filter by status
    private String fileName; // Search by filename
    private String sentiment; // Filter by sentiment
    private String topic; // Filter by topic
    private LocalDateTime startDate; // Created after
    private LocalDateTime endDate; // Created before
    private int pageNumber; // Page number (0-indexed)
    private int pageSize; // Items per page (default 10, max 100)
    private String sortBy; // createdAt, fileName, wordCount
    private String sortDirection; // ASC or DESC

}

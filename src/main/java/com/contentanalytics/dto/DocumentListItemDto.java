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
public class DocumentListItemDto {

    private String id;
    private String fileName;
    private Long fileSize;
    private String status;
    private Float processingProgress;
    private String sentiment;
    private Integer wordCount;
    private LocalDateTime createdAt;
    private LocalDateTime processingCompletedAt;

}

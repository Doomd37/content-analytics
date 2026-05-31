package com.contentanalytics.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ImageProcessingResult {

    private String originalFileName;
    private Long originalFileSize;
    private String mimeType;
    private String thumbnailKey;
    private String mediumKey;
    private String fullKey;
    private Integer width;
    private Integer height;

}
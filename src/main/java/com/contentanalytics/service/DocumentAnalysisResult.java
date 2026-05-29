package com.contentanalytics.service;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DocumentAnalysisResult {

    private String summary;
    private String keyTopics;
    private String sentiment;
    private Float confidenceScore;

}
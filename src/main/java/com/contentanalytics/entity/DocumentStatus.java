package com.contentanalytics.entity;

public enum DocumentStatus {
    UPLOADED,      // File uploaded but not processed yet
    PROCESSING,    // Currently being analyzed by AI
    COMPLETED,     // Analysis complete
    FAILED         // Processing failed
}

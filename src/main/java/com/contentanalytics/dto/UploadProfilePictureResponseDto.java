package com.contentanalytics.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class UploadProfilePictureResponseDto {

    private String messageId;
    private String status; // SUCCESS, ERROR
    private String message;

    // Picture info
    private String thumbnailUrl;
    private String mediumUrl;
    private String fullUrl;
    private Integer width;
    private Integer height;
    private Long fileSizeBytes;

    // File info
    private String fileName;
    private String mimeType;

}

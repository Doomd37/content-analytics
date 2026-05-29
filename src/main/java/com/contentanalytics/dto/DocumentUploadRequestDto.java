package com.contentanalytics.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.web.multipart.MultipartFile;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class DocumentUploadRequestDto {

    @NotNull(message = "File is required")
    private MultipartFile file;

    @Size(max = 1000, message = "Description must not exceed 1000 characters")
    private String description;

}

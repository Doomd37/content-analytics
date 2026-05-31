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
public class PasswordChangeResponseDto {

    private String status; // SUCCESS, ERROR
    private String message;
    private LocalDateTime changedAt;
    private Boolean allSessionsLoggedOut; // Notify user about logout

}

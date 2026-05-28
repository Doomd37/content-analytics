package com.contentanalytics.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class TokenRefreshRequestDto {

    @NotBlank(message = "Refresh token is required")
    private String refreshToken;

}

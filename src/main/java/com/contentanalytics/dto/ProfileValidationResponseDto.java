package com.contentanalytics.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProfileValidationResponseDto {

    private String field; // The field being validated
    private Boolean valid; // Is valid?
    private String message; // Validation message

}

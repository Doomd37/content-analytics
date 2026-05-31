package com.contentanalytics.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProfilePictureDeleteResponseDto {

    private String status; // SUCCESS, ERROR
    private String message;
    private String avatarUrl; // New gravatar URL after deletion

}
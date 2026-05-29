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
public class NotificationMessage {

    private String notificationId;
    private String type; // INFO, WARNING, ERROR, SUCCESS
    private String title;
    private String message;
    private String relatedId; // documentId, userId, etc
    private Boolean isRead;
    private LocalDateTime createdAt;
    private LocalDateTime readAt;

}
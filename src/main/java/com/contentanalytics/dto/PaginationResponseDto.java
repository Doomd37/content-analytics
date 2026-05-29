package com.contentanalytics.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class PaginationResponseDto<T> {

    private List<T> content; // Actual data
    private int pageNumber; // Current page (0-indexed)
    private int pageSize; // Items per page
    private long totalElements; // Total number of items
    private int totalPages; // Total number of pages
    private boolean isFirst; // Is this the first page?
    private boolean isLast; // Is this the last page?
    private boolean hasNext; // Is there a next page?
    private boolean hasPrevious; // Is there a previous page?

}
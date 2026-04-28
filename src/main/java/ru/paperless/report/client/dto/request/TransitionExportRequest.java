package ru.paperless.report.client.dto.request;

import lombok.Data;

@Data
public class TransitionExportRequest {
    // "4374 36474" (опционально)
    private String sprintIds;
}

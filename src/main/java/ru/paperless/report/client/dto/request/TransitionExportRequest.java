package ru.paperless.report.client.dto.request;

import lombok.Data;

import java.util.List;

@Data
public class TransitionExportRequest {
    // status_name из БД
    private List<String> fromStatuses;
    private List<String> toStatuses;

    // "4374 36474" (опционально)
    private String sprintIds;
}
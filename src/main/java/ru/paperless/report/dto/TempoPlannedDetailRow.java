package ru.paperless.report.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class TempoPlannedDetailRow {
    private Long sprintId;
    private String sprintName;
    private String employee;
    private String issueKey;
    private Long plannedSeconds;
    private String statusAtSprintStart;
    private String statusAtSprintEnd;
}
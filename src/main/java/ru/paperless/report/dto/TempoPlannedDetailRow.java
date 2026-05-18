package ru.paperless.report.dto;

import lombok.Data;

@Data
public class TempoPlannedDetailRow {
    private Long sprintId;
    private String sprintName;
    private String employee;
    private String issueKey;
    private String issueSummary;
    private String epicKey;
    private Long plannedSeconds;
    private String statusAtSprintStart;
    private String statusAtSprintEnd;
    private Boolean outOfPlan;

    public TempoPlannedDetailRow(Long sprintId,
                                 String sprintName,
                                 String employee,
                                 String issueKey,
                                 String issueSummary,
                                 String epicKey,
                                 Long plannedSeconds,
                                 String statusAtSprintStart,
                                 String statusAtSprintEnd) {
        this(sprintId, sprintName, employee, issueKey, issueSummary, epicKey, plannedSeconds, statusAtSprintStart, statusAtSprintEnd, false);
    }

    public TempoPlannedDetailRow(Long sprintId,
                                 String sprintName,
                                 String employee,
                                 String issueKey,
                                 String issueSummary,
                                 String epicKey,
                                 Long plannedSeconds,
                                 String statusAtSprintStart,
                                 String statusAtSprintEnd,
                                 Boolean outOfPlan) {
        this.sprintId = sprintId;
        this.sprintName = sprintName;
        this.employee = employee;
        this.issueKey = issueKey;
        this.issueSummary = issueSummary;
        this.epicKey = epicKey;
        this.plannedSeconds = plannedSeconds;
        this.statusAtSprintStart = statusAtSprintStart;
        this.statusAtSprintEnd = statusAtSprintEnd;
        this.outOfPlan = outOfPlan;
    }
}

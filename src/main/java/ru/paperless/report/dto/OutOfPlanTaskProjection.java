package ru.paperless.report.dto;

import java.time.OffsetDateTime;

public interface OutOfPlanTaskProjection {
    Long getSprintId();
    String getSprintName();
    String getEmployee();
    String getIssueKey();
    String getIssueSummary();
    String getEpicKey();
    String getStatusAtSprintStart();
    String getStatusAtSprintEnd();
    OffsetDateTime getTransitionDate();
}

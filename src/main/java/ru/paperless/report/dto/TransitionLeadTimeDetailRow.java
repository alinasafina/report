package ru.paperless.report.dto;

import java.time.Instant;

public interface TransitionLeadTimeDetailRow {
    String getEmployee();

    String getIssueKey();

    String getIssueSummary();

    Long getSprintId();

    String getSprintName();

    String getFromStatusName();

    String getToStatusName();

    Instant getTransitionDate();
}

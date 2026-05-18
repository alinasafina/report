package ru.paperless.report.dto;

import java.time.Instant;

public interface TransitionLeadTimeSummaryRow {
    String getEmployee();

    String getIssueKey();

    String getIssueSummary();

    Long getStartSprintId();

    String getStartSprintName();

    Long getEndSprintId();

    String getEndSprintName();

    Instant getStartDate();

    Instant getEndDate();

    Long getSprintCount();

    Long getReopenedCount();
}

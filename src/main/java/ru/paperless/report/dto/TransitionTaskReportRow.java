package ru.paperless.report.dto;

public interface TransitionTaskReportRow {
    String getEmployee();

    Long getSprintId();

    String getSprintName();

    String getIssueKey();

    String getFromStatusName();

    Long getTransitionsCount();
}

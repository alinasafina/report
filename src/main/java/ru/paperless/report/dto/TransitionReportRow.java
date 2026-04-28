package ru.paperless.report.dto;

public interface TransitionReportRow {
    String getEmployee();
    Long getSprintId();
    String getSprintName();
    Long getTransitionsCount();
}
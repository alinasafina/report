package ru.paperless.report.dto;

import java.time.Instant;
import java.time.OffsetDateTime;

public interface TransitionDetailRow {
    String getIssueKey();

    Long getSprintId();

    String getSprintName();

    String getFromStatusName();

    String getToStatusName();

    String getDeveloper();

    Instant getTransitionDate();
}
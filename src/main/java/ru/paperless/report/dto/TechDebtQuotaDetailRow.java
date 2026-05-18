package ru.paperless.report.dto;

import java.math.BigDecimal;

public interface TechDebtQuotaDetailRow {
    Long getSprintId();
    String getSprintName();
    String getIssueKey();
    String getIssueSummary();
    BigDecimal getEstimateHours();
    String getStatusAtSprintEnd();
}

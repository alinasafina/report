package ru.paperless.report.dto;

import java.math.BigDecimal;

public interface TechDebtQuotaSummaryRow {
    Long getSprintId();
    String getSprintName();
    BigDecimal getTotalEstimateHours();
    Long getTaskCount();
    Long getResolvedTaskCount();
}

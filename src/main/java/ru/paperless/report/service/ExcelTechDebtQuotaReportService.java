package ru.paperless.report.service;

public interface ExcelTechDebtQuotaReportService {
    byte[] buildXlsx(String sprintIdsText);
}

package ru.paperless.report.service;

import java.util.List;

public interface ExcelTransitionLeadTimeReportService {
    byte[] buildXlsx(List<Long> startStatusIds,
                     List<Long> targetStatusIds,
                     String sprintIdsText);
}

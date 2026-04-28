package ru.paperless.report.service;

import java.util.List;

public interface ExcelEffortReportService {
    byte[] buildXlsx(String sprintIds);
}

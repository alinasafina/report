package ru.paperless.report.service;

import java.util.List;

public interface ExcelPlanningExport {
    byte[] buildXlsx(String sprintIds, List<String> doneStatusNames, List<String> notClosedStatusNames);
}

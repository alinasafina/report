package ru.paperless.report.service;

import java.util.List;

public interface ExcelStatusTransitionReportService {
    byte[] buildXlsx(List<Long> fromStatusIds,
                     List<Long> toStatusIds,
                     List<Long> taskFromStatusIds,
                     List<Long> taskToStatusIds,
                     String sprintIdsText);
}

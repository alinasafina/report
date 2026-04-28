package ru.paperless.report.service;

import java.time.LocalDate;

public interface JiraMainFieldsExportService {
    void saveJiraStatuses();

    String saveJiraSprints(LocalDate dateFrom, LocalDate dateTo, String sprintStatesCsv);

    int saveJiraUserKeys();
}

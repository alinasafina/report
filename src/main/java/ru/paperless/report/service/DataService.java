package ru.paperless.report.service;

import ru.paperless.report.entity.ProjectJiraStatus;

import java.util.List;

public interface DataService {
    String getSprintsIdFromDB();

    List<ProjectJiraStatus> getProjectJiraStatus();

    List<String> getSelectableEmployes();

    List<String> getAllEmployes();
}

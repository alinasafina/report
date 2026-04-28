package ru.paperless.report.service;

import ru.paperless.report.entity.ProjectJiraStatus;

import java.util.List;

public interface DataService {
    String getSprintsIdFromDB(String namePrefix);

    List<ProjectJiraStatus> getProjectJiraStatus();

    List<String> getSelectableEmployes();

    List<String> getAllEmployes();
}

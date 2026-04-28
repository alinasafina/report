package ru.paperless.report.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import ru.paperless.report.entity.Employee;
import ru.paperless.report.entity.ProjectJiraSprint;
import ru.paperless.report.entity.ProjectJiraStatus;
import ru.paperless.report.repository.EmployeeRepository;
import ru.paperless.report.repository.ProjectJiraSprintRepository;
import ru.paperless.report.repository.ProjectJiraStatusRepository;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class DataServiceImpl implements DataService {
    private final ProjectJiraSprintRepository projectJiraSprintRepository;
    private final ProjectJiraStatusRepository projectJiraStatusRepository;
    private final EmployeeRepository employeeRepository;

    @Override
    public String getSprintsIdFromDB(String namePrefix) {
        String normalizedPrefix = StringUtils.hasText(namePrefix) ? namePrefix.trim().toLowerCase() : null;

        return projectJiraSprintRepository.findAll().stream()
                .filter(sprint -> {
                    if (!StringUtils.hasText(normalizedPrefix)) {
                        return true;
                    }

                    String sprintName = sprint.getSprintName();
                    return StringUtils.hasText(sprintName)
                            && sprintName.trim().toLowerCase().startsWith(normalizedPrefix);
                })
                .map(ProjectJiraSprint::getSprintId)
                .map(String::valueOf)
                .collect(Collectors.joining(" "));
    }

    @Override
    public List<ProjectJiraStatus> getProjectJiraStatus() {
        return projectJiraStatusRepository.findAll();
    }

    @Override
    public List<String> getSelectableEmployes() {
        return employeeRepository.findBySelectableTrue().stream()
                .map(Employee::getFullName)
                .collect(Collectors.toList());
    }

    @Override
    public List<String> getAllEmployes() {
        return employeeRepository.findAll().stream()
                .map(Employee::getFullName)
                .collect(Collectors.toList());
    }
}

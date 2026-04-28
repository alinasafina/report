package ru.paperless.report.controller;

import io.swagger.v3.oas.annotations.Operation;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import ru.paperless.report.entity.ProjectJiraStatus;
import ru.paperless.report.service.*;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/data")
public class DataController {

    private final DataService service;
    @GetMapping(value = "sprints/id", produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(description = "Выгрузить список айди спринтов из БД")
    public String getSprints(@RequestParam(name = "namePrefix", required = false) String namePrefix) {
        return service.getSprintsIdFromDB(namePrefix);
    }

    @GetMapping(value = "status", produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(description = "Выгрузить список статусов из БД")
    public List<ProjectJiraStatus> getStatuses() {
        return service.getProjectJiraStatus();
    }

    @GetMapping(value = "employee/all", produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(description = "Выгрузить список сотрудников из БД")
    public List<String> getEmployes() {
        return service.getAllEmployes();
    }

    @GetMapping(value = "employee/selectable", produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(description = "Выгрузить список активных сотрудников из БД")
    public List<String> getEmployeeSelectableIsTrue() {
        return service.getSelectableEmployes();
    }
}

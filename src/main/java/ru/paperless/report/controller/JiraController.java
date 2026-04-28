package ru.paperless.report.controller;

import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import io.swagger.v3.oas.annotations.Operation;
import ru.paperless.report.client.dto.request.SprintIdsRequest;
import ru.paperless.report.client.dto.request.TempoPlannedStatusExportRequest;
import ru.paperless.report.client.dto.request.TransitionExportRequest;
import ru.paperless.report.dto.JiraFieldDto;
import ru.paperless.report.service.*;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1")
public class JiraController {

    private final JiraMainFieldsExportService jiraMainFieldsExportService;
    private final JiraStatusTransitionExportService jiraStatusTransitionServiceImpl;
    private final JiraEffortExportService jiraEffortExportService;
    private final TempoSprintPlannedStatusExportService tempoSprintPlannedStatusExportService;
    private final GeneralMethodsService generalMethodsService;

    @GetMapping(value = "jira/task/status", produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(description = "Выгрузить список статусов проекта из Jira в БД")
    public void saveJiraStatuses() {
        jiraMainFieldsExportService.saveJiraStatuses();
    }

    @PostMapping("jira/sprints")
    @Operation(description = "Выгрузить список спринтов по датам из Jira в БД")
    public String saveJiraSprint(
            @RequestParam(name = "dateFrom", defaultValue = "2025-09-01") @NonNull @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateFrom,
            @RequestParam(name = "dateTo") @NonNull @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateTo,
            @RequestParam(name = "states", defaultValue = "active,closed,future") String states) {
        return jiraMainFieldsExportService.saveJiraSprints(dateFrom, dateTo, states);
    }

    @GetMapping(value = "jira/fields", produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(description = "Получить список fields Jira из Jira")
    public List<JiraFieldDto> getJiraFields() {
        return generalMethodsService.getJiraFields();
    }

    @GetMapping(value = "jira/user/key", produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(description = "Выгрузить userKeys")
    public void saveJiraUserKeys() {
        jiraMainFieldsExportService.saveJiraUserKeys();
    }

    @PostMapping("export/statusTransitions")
    @Operation(description = "Выгрузить переходы по статусам")
    public ResponseEntity<?> exportStatusTransitions(@RequestBody TransitionExportRequest req) throws Exception {
        return ResponseEntity.ok(Map.of("saved", jiraStatusTransitionServiceImpl.exportTransitions(req)));
    }

    @PostMapping("export/effort")
    @Operation(description = "Выгрузить списание часов")
    public ResponseEntity<?> exportEffort(@RequestBody SprintIdsRequest req) throws Exception {
        return ResponseEntity.ok(Map.of("saved", jiraEffortExportService.exportEffort(req)));
    }

    @PostMapping("export/sprint/plan")
    @Operation(description = "Выгрузить списание часов")
    public ResponseEntity<?> exportPlannedStatuses(@RequestBody TempoPlannedStatusExportRequest req) throws Exception {
        return ResponseEntity.ok(Map.of("saved", tempoSprintPlannedStatusExportService.exportTempoPlannedWithSprintStatuses(req)));
    }
}

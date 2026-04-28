package ru.paperless.report.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import ru.paperless.report.service.ExcelEffortReportService;
import ru.paperless.report.service.ExcelPlanningExport;
import ru.paperless.report.service.ExcelStatusTransitionReportService;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1")
public class ExcelController {

    private final ExcelStatusTransitionReportService transitionReportService;
    private final ExcelEffortReportService effortReportService;
    private final ExcelPlanningExport planningExport;

    @GetMapping("Возвраты задач")
    public ResponseEntity<byte[]> transitionsReport(
            @RequestParam(name = "sprintIds", required = false) String sprintIds
    ) {
        String baseName = "reopened_" + LocalDate.now();

        //Разработка
        List<String> doneStatus = List.of("Решена", "Ожидает", "Тестирование", "Closed", "In Review", "Tested");
        List<String> notClosed = List.of("В разработке", "Открыта (Open)", "In Progress", "Reopened", "Open");
        //Аналитика
        /*List<String> doneStatus = List.of("Решена", "Тестирование", "Closed", "In Review", "Tested",
                "Закрыта", "Готово к разработке", "Разработка", "Грумминг пройден", "Выпущена");
        List<String> notClosed = List.of("В разработке", "Ожидает", "Открыта (Open)", "In Progress", "Reopened", "Open",
                "In Progress");*/
        /*Тестирование
        List<String> doneStatus = List.of("Решена", "Closed", "In Review", "Tested", "В разработке");
        List<String> notClosed = List.of("Ожидает", "Открыта (Open)", "Reopened", "Open", "Тестирование", "In Progress");*/


        byte[] file = transitionReportService.buildXlsx(fromStatusIds, toStatusIds, sprintIds);

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                        .filename(baseName + ".xlsx")
                        .build().toString())
                .contentType(MediaType.parseMediaType(
                        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .body(file);

    }

    @GetMapping("Оценка задач")
    public ResponseEntity<byte[]> effortReport(@RequestParam(name = "sprintIds", required = false) String sprintIds) {
        String baseName = "effort_" + LocalDate.now();

        byte[] file = effortReportService.buildXlsx(sprintIds);

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                        .filename(baseName + ".xlsx")
                        .build().toString())
                .contentType(MediaType.parseMediaType(
                        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .body(file);

    }

    @GetMapping("Планирование задач")
    public ResponseEntity<byte[]> planningReport(@RequestParam(name = "sprintIds") String sprintIds) {
        String baseName = "planning_" + LocalDate.now();

        //Разработка
        //List<String> doneStatus = List.of("Решена", "Тестирование", "Closed", "In Review", "Tested");
        //List<String> notClosed = List.of("В разработке", "Ожидает", "Открыта (Open)", "In Progress", "Reopened", "Open");
        //Аналитика
        List<String> doneStatus = List.of("Решена", "Тестирование", "Closed", "In Review", "Tested",
                "Закрыта", "Готово к разработке", "Разработка", "Грумминг пройден", "Выпущена");
        List<String> notClosed = List.of("В разработке", "Ожидает", "Открыта (Open)", "In Progress", "Reopened", "Open",
                "In Progress");
        /*Тестирование
        List<String> doneStatus = List.of("Решена", "Closed", "In Review", "Tested", "В разработке");
        List<String> notClosed = List.of("Ожидает", "Открыта (Open)", "Reopened", "Open", "Тестирование", "In Progress");*/

        byte[] file = planningExport.buildXlsx(sprintIds, doneStatus, notClosed);

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                        .filename(baseName + ".xlsx")
                        .build().toString())
                .contentType(MediaType.parseMediaType(
                        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .body(file);

    }
}
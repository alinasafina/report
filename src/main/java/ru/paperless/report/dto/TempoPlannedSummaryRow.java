package ru.paperless.report.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class TempoPlannedSummaryRow {
    private String employee;
    private Long sprintId;
    private String sprintName;
    private Long plannedTasksCount; // всего задач
    private Long doneTasksCount; // задач в "done" статусах (по фильтру)
    private Long notClosedTasksCount;   //задач в "не закрыто" статусах
}
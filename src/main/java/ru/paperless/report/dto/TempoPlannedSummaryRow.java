package ru.paperless.report.dto;

import lombok.Data;

@Data
public class TempoPlannedSummaryRow {
    private String employee;
    private Long sprintId;
    private String sprintName;
    private Long plannedTasksCount; // всего задач
    private Long doneTasksCount; // задач в "done" статусах (по фильтру)
    private Long notClosedTasksCount;   //задач в "не закрыто" статусах
    private Long outOfPlanTasksCount;   //задач вне плана
    private Long outOfPlanDoneTasksCount;   //внеплановых задач в "done" статусах

    public TempoPlannedSummaryRow(String employee,
                                  Long sprintId,
                                  String sprintName,
                                  Long plannedTasksCount,
                                  Long doneTasksCount,
                                  Long notClosedTasksCount) {
        this(employee, sprintId, sprintName, plannedTasksCount, doneTasksCount, notClosedTasksCount, 0L, 0L);
    }

    public TempoPlannedSummaryRow(String employee,
                                  Long sprintId,
                                  String sprintName,
                                  Long plannedTasksCount,
                                  Long doneTasksCount,
                                  Long notClosedTasksCount,
                                  Long outOfPlanTasksCount,
                                  Long outOfPlanDoneTasksCount) {
        this.employee = employee;
        this.sprintId = sprintId;
        this.sprintName = sprintName;
        this.plannedTasksCount = plannedTasksCount;
        this.doneTasksCount = doneTasksCount;
        this.notClosedTasksCount = notClosedTasksCount;
        this.outOfPlanTasksCount = outOfPlanTasksCount;
        this.outOfPlanDoneTasksCount = outOfPlanDoneTasksCount;
    }
}

package ru.paperless.report.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.math.BigDecimal;

@Data
@AllArgsConstructor
public class EffortReportRow {
    private String employee;
    private Long sprintFirstId;
    private String sprintFirstName;
    private Long sprintLastLoggedId;
    private String sprintLastLoggedName;
    private String issueKey;
    private BigDecimal firstEstimateHours;
    private BigDecimal loggedHours;
    private String epicKey;
}

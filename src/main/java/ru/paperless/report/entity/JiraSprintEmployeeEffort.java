package ru.paperless.report.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.OffsetDateTime;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "jira_sprint_employee_effort")
public class JiraSprintEmployeeEffort {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String projectKey;
    private Long sprintFirstId;
    private String sprintFirstName;
    private Long sprintLastLoggedId;
    private String sprintLastLoggedName;

    private String issueKey;
    private String issueSummary;
    private String assignee;
    private String developer;

    private String employee;

    private Double firstEstimateHours;
    private Double loggedHours;
    private String epicKey;
    private String labels;

    /** Дата логирования (worklog.started) — по ней задача попадает в спринт. */
    @Column(name = "collected_at")
    private OffsetDateTime collectedAt;
}

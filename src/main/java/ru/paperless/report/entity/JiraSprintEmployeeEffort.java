package ru.paperless.report.entity;

import jakarta.persistence.*;
import lombok.*;

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
    private String assignee;
    private String developer;

    private String employee;

    private Double firstEstimateHours;
    private Double loggedHours;
    private String epicKey;
}
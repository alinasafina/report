package ru.paperless.report.entity;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "jira_sprint_tempo_planned_status", schema = "public")
@Getter @Setter
@NoArgsConstructor @AllArgsConstructor
@Builder
public class JiraSprintTempoPlannedStatus {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "project_key", nullable = false)
    private String projectKey;

    @Column(name = "sprint_id")
    private Long sprintId;

    @Column(name = "sprint_name")
    private String sprintName;

    @Column(name = "issue_key", nullable = false)
    private String issueKey;

    @Column(name = "issue_summary")
    private String issueSummary;

    @Column(name = "employee", nullable = false)
    private String employee;

    @Column(name = "assignee_key", nullable = false)
    private String assigneeKey;

    @Column(name = "planned_seconds", nullable = false)
    private Long plannedSeconds;

    @Column(name = "status_at_sprint_start")
    private String statusAtSprintStart;

    @Column(name = "status_at_sprint_end")
    private String statusAtSprintEnd;
}

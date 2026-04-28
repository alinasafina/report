package ru.paperless.report.entity;
import jakarta.persistence.*;
import lombok.*;
import java.time.OffsetDateTime;

@Entity
@Table(name = "jira_sprint_status_transition", schema = "public")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class JiraSprintStatusTransition {

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

    @Column(name = "final_assignee")
    private String finalAssignee;

    @Column(name = "developer")
    private String developer;

    @Column(name = "from_status_id")
    private Long fromStatusId;

    @Column(name = "from_status_name")
    private String fromStatusName;

    @Column(name = "to_status_id")
    private Long toStatusId;

    @Column(name = "to_status_name")
    private String toStatusName;

    @Column(name = "transition_date")
    private OffsetDateTime transitionDate;
}
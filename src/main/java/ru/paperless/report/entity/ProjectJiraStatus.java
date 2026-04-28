package ru.paperless.report.entity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "project_jira_status", schema = "public")
@Getter
@Setter
@NoArgsConstructor
public class ProjectJiraStatus {

    @Id
    @Column(name = "status_id", nullable = false)
    private Long statusId;

    @Column(name = "status_name")
    private String statusName;

    @Column(name = "status_category")
    private String statusCategory;
}
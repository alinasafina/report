package ru.paperless.report.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;

@Entity
@Table(name = "project_jira_sprint", schema = "public")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProjectJiraSprint {

    @Id
    @Column(name = "sprint_id", nullable = false)
    private Long sprintId;

    @Column(name = "sprint_name")
    private String sprintName;

    @Column(name = "sprint_state")
    private String sprintState;

    @Column(name = "start_date")
    private LocalDate startDate;

    @Column(name = "end_date")
    private LocalDate endDate;

    @Column(name = "complete_date")
    private LocalDate completeDate;

    @Column(name = "board_id")
    private Long boardId;

    @Column(name = "board_name")
    private String boardName;

    @Column(name = "project_key")
    private String projectKey;
}
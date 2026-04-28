package ru.paperless.report.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

@Entity
@Table(name = "employee", schema = "public")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class Employee {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "full_name", nullable = false)
    private String fullName;

    @Column(name = "selectable", nullable = false)
    private Boolean selectable = true;

    @Column(name = "jira_user_key", nullable = false)
    private String jiraUserKey; // то, что Tempo называет assigneeKey
}
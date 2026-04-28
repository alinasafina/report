package ru.paperless.report.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import ru.paperless.report.dto.EffortReportRow;
import ru.paperless.report.entity.JiraSprintEmployeeEffort;

import java.util.List;

public interface JiraSprintEmployeeEffortRepository extends JpaRepository<JiraSprintEmployeeEffort, Long> {

    @Query(value = """
            SELECT
                e.employee            AS employee,
                e.sprint_first_id           AS sprintFirstId,
                e.sprint_first_name         AS sprintFirstName,
                e.sprint_last_logged_id           AS sprintLastLoggedId,
                e.sprint_last_logged_name         AS sprintLastLoggedName,
                e.issue_key           AS issueKey,
                e.first_estimate_hours AS firstEstimateHours,
                e.logged_hours        AS loggedHours,
                e.epic_key         AS epicKey
            FROM public.jira_sprint_employee_effort e
            WHERE (:sprintsEmpty = true OR e.sprint_first_id = ANY(:sprintIds))
            ORDER BY e.employee, e.sprint_first_id, e.issue_key
            """, nativeQuery = true)
    List<EffortReportRow> getEffortReport(
            @Param("sprintIds") Long[] sprintIds,
            @Param("sprintsEmpty") boolean sprintsEmpty
    );
}
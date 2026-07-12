package ru.paperless.report.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import ru.paperless.report.dto.EffortReportRow;
import ru.paperless.report.dto.TechDebtQuotaDetailRow;
import ru.paperless.report.dto.TechDebtQuotaSummaryRow;
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
            WHERE EXISTS (
                SELECT 1
                FROM public.employee emp
                WHERE btrim(emp.full_name) = btrim(e.employee)
                  AND emp.selectable = true
              )
            ORDER BY e.employee, e.sprint_first_id, e.issue_key
            """, nativeQuery = true)
    List<EffortReportRow> getEffortReport();

    @Query(value = """
            WITH labeled_tasks AS (
                SELECT
                    e.sprint_first_id AS sprintId,
                    max(e.sprint_first_name) AS sprintName,
                    e.issue_key AS issueKey,
                    max(e.issue_summary) AS issueSummary,
                    max(e.first_estimate_hours) AS estimateHours
                FROM public.jira_sprint_employee_effort e
                WHERE (:sprintsEmpty = true OR e.sprint_first_id = ANY(:sprintIds))
                  AND EXISTS (
                    SELECT 1
                    FROM unnest(string_to_array(coalesce(e.labels, ''), ';')) AS lbl
                    WHERE btrim(lbl) = :label
                  )
                GROUP BY e.sprint_first_id, e.issue_key
            ),
            latest_status AS (
                SELECT DISTINCT ON (t.sprint_id, t.issue_key)
                    t.sprint_id AS sprintId,
                    t.issue_key AS issueKey,
                    coalesce(t.to_status_name, ps.status_name) AS statusAtSprintEnd
                FROM public.jira_sprint_status_transition t
                LEFT JOIN public.project_jira_status ps ON ps.status_id = t.to_status_id
                WHERE (:sprintsEmpty = true OR t.sprint_id = ANY(:sprintIds))
                ORDER BY t.sprint_id, t.issue_key, t.transition_date DESC NULLS LAST, t.id DESC
            )
            SELECT
                lt.sprintId AS sprintId,
                lt.sprintName AS sprintName,
                coalesce(sum(lt.estimateHours), 0) AS totalEstimateHours,
                count(*) AS taskCount,
                count(*) FILTER (WHERE ls.statusAtSprintEnd = 'Решена') AS resolvedTaskCount
            FROM labeled_tasks lt
            LEFT JOIN latest_status ls
                ON ls.sprintId = lt.sprintId
               AND ls.issueKey = lt.issueKey
            GROUP BY lt.sprintId, lt.sprintName
            ORDER BY lt.sprintId
            """, nativeQuery = true)
    List<TechDebtQuotaSummaryRow> getTechDebtQuotaSummary(
            @Param("sprintIds") Long[] sprintIds,
            @Param("sprintsEmpty") boolean sprintsEmpty,
            @Param("label") String label
    );

    @Query(value = """
            WITH labeled_tasks AS (
                SELECT
                    e.sprint_first_id AS sprintId,
                    max(e.sprint_first_name) AS sprintName,
                    e.issue_key AS issueKey,
                    max(e.issue_summary) AS issueSummary,
                    max(e.first_estimate_hours) AS estimateHours
                FROM public.jira_sprint_employee_effort e
                WHERE (:sprintsEmpty = true OR e.sprint_first_id = ANY(:sprintIds))
                  AND EXISTS (
                    SELECT 1
                    FROM unnest(string_to_array(coalesce(e.labels, ''), ';')) AS lbl
                    WHERE btrim(lbl) = :label
                  )
                GROUP BY e.sprint_first_id, e.issue_key
            ),
            latest_status AS (
                SELECT DISTINCT ON (t.sprint_id, t.issue_key)
                    t.sprint_id AS sprintId,
                    t.issue_key AS issueKey,
                    coalesce(t.to_status_name, ps.status_name) AS statusAtSprintEnd
                FROM public.jira_sprint_status_transition t
                LEFT JOIN public.project_jira_status ps ON ps.status_id = t.to_status_id
                WHERE (:sprintsEmpty = true OR t.sprint_id = ANY(:sprintIds))
                ORDER BY t.sprint_id, t.issue_key, t.transition_date DESC NULLS LAST, t.id DESC
            )
            SELECT
                lt.sprintId AS sprintId,
                lt.sprintName AS sprintName,
                lt.issueKey AS issueKey,
                lt.issueSummary AS issueSummary,
                lt.estimateHours AS estimateHours,
                ls.statusAtSprintEnd AS statusAtSprintEnd
            FROM labeled_tasks lt
            LEFT JOIN latest_status ls
                ON ls.sprintId = lt.sprintId
               AND ls.issueKey = lt.issueKey
            ORDER BY lt.sprintId, lt.issueKey
            """, nativeQuery = true)
    List<TechDebtQuotaDetailRow> getTechDebtQuotaDetails(
            @Param("sprintIds") Long[] sprintIds,
            @Param("sprintsEmpty") boolean sprintsEmpty,
            @Param("label") String label
    );
}

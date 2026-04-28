package ru.paperless.report.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import ru.paperless.report.dto.TransitionDetailRow;
import ru.paperless.report.dto.TransitionReportRow;
import ru.paperless.report.entity.JiraSprintStatusTransition;

import java.util.List;

public interface JiraSprintStatusTransitionRepository extends JpaRepository<JiraSprintStatusTransition, Long> {
    @Query(value = """
    with matched as (
        select
            t.final_assignee as employee,
            t.sprint_id as sprint_id,
            t.sprint_name as sprint_name,
            count(*) as cnt
        from jira_sprint_status_transition t
        where t.final_assignee is not null
          and t.final_assignee in (:employees)
          and (:useFrom = false or t.from_status_id in (:fromStatusIds))
          and (:useTo   = false or t.to_status_id   in (:toStatusIds))
          and (:useSprints = false or t.sprint_id in (:sprintIds))
        group by t.final_assignee, t.sprint_id, t.sprint_name

        union all

        select
            t.developer as employee,
            t.sprint_id as sprint_id,
            t.sprint_name as sprint_name,
            count(*) as cnt
        from jira_sprint_status_transition t
        where t.developer is not null
          and t.developer in (:employees)
          and (:useFrom = false or t.from_status_id in (:fromStatusIds))
          and (:useTo   = false or t.to_status_id   in (:toStatusIds))
          and (:useSprints = false or t.sprint_id in (:sprintIds))
          and (t.final_assignee is null or t.developer <> t.final_assignee)
        group by t.developer, t.sprint_id, t.sprint_name
    )
    select
        employee as employee,
        sprint_id as sprintId,
        sprint_name as sprintName,
        sum(cnt) as transitionsCount
    from matched
    group by employee, sprint_id, sprint_name
    order by employee, sprint_id nulls last
    """, nativeQuery = true)
    List<TransitionReportRow> getReportByStatusLists(
            @Param("employees") List<String> employees,
            @Param("useFrom") boolean useFrom,
            @Param("fromStatusIds") List<Long> fromStatusIds,
            @Param("useTo") boolean useTo,
            @Param("toStatusIds") List<Long> toStatusIds,
            @Param("useSprints") boolean useSprints,
            @Param("sprintIds") List<Long> sprintIds
    );

    // --- детализация для 2-го листа ---
    @Query(value = """
        select
            t.issue_key as issueKey,
            t.sprint_id as sprintId,
            t.sprint_name as sprintName,
            fs.status_name as fromStatusName,
            ts.status_name as toStatusName,
            t.developer as developer,
            t.transition_date as transitionDate
        from jira_sprint_status_transition t
        left join project_jira_status fs on fs.status_id = t.from_status_id
        left join project_jira_status ts on ts.status_id = t.to_status_id
        where
            ( (t.final_assignee is not null and t.final_assignee in (:employees))
              or
              (t.developer is not null and t.developer in (:employees)) )
          and (:useFrom = false or t.from_status_id in (:fromStatusIds))
          and (:useTo   = false or t.to_status_id   in (:toStatusIds))
          and (:useSprints = false or t.sprint_id in (:sprintIds))
        order by t.transition_date desc nulls last, t.issue_key
        """, nativeQuery = true)
    List<TransitionDetailRow> getDetailsByStatusLists(
            @Param("employees") List<String> employees,
            @Param("useFrom") boolean useFrom,
            @Param("fromStatusIds") List<Long> fromStatusIds,
            @Param("useTo") boolean useTo,
            @Param("toStatusIds") List<Long> toStatusIds,
            @Param("useSprints") boolean useSprints,
            @Param("sprintIds") List<Long> sprintIds
    );
}
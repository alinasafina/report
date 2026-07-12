package ru.paperless.report.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import ru.paperless.report.dto.OutOfPlanTaskProjection;
import ru.paperless.report.dto.TransitionDetailRow;
import ru.paperless.report.dto.TransitionLeadTimeDetailRow;
import ru.paperless.report.dto.TransitionLeadTimeSummaryRow;
import ru.paperless.report.dto.TransitionReportRow;
import ru.paperless.report.dto.TransitionTaskReportRow;
import ru.paperless.report.entity.JiraSprintStatusTransition;

import java.util.List;

public interface JiraSprintStatusTransitionRepository extends JpaRepository<JiraSprintStatusTransition, Long> {
    /**
     * Спринт перехода определяется НЕ по t.sprint_id, а по попаданию transition_date
     * в период спринта (project_jira_sprint.start_date .. end_date включительно).
     * Через lateral + limit 1: если периоды спринтов перекрываются, переход всё равно
     * относится РОВНО к одному спринту (к тому, что начался позже) — иначе строка дублируется.
     */
    @Query(value = """
    with matched as (
        select
            t.final_assignee as employee,
            s.sprint_id as sprint_id,
            s.sprint_name as sprint_name,
            coalesce(t.from_status_name, fs.status_name) as from_status_name,
            count(*) as cnt
        from jira_sprint_status_transition t
        join lateral (
            select ps.sprint_id, ps.sprint_name
            from project_jira_sprint ps
            where (:useSprints = false or ps.sprint_id in (:sprintIds))
              and ps.start_date is not null
              and ps.end_date is not null
              and t.transition_date >= ps.start_date::timestamptz
              and t.transition_date <  (ps.end_date + 1)::timestamptz
            order by ps.start_date desc, ps.sprint_id desc
            limit 1
        ) s on true
        left join project_jira_status fs on fs.status_id = t.from_status_id
        where t.final_assignee is not null
          and t.final_assignee in (:employees)
          and (:useFrom = false or t.from_status_id in (:fromStatusIds))
          and (:useTo   = false or t.to_status_id   in (:toStatusIds))
        group by t.final_assignee, s.sprint_id, s.sprint_name, coalesce(t.from_status_name, fs.status_name)

        union all

        select
            t.developer as employee,
            s.sprint_id as sprint_id,
            s.sprint_name as sprint_name,
            coalesce(t.from_status_name, fs.status_name) as from_status_name,
            count(*) as cnt
        from jira_sprint_status_transition t
        join lateral (
            select ps.sprint_id, ps.sprint_name
            from project_jira_sprint ps
            where (:useSprints = false or ps.sprint_id in (:sprintIds))
              and ps.start_date is not null
              and ps.end_date is not null
              and t.transition_date >= ps.start_date::timestamptz
              and t.transition_date <  (ps.end_date + 1)::timestamptz
            order by ps.start_date desc, ps.sprint_id desc
            limit 1
        ) s on true
        left join project_jira_status fs on fs.status_id = t.from_status_id
        where t.developer is not null
          and t.developer in (:employees)
          and (:useFrom = false or t.from_status_id in (:fromStatusIds))
          and (:useTo   = false or t.to_status_id   in (:toStatusIds))
          and (t.final_assignee is null or t.developer <> t.final_assignee)
        group by t.developer, s.sprint_id, s.sprint_name, coalesce(t.from_status_name, fs.status_name)
    )
    select
        employee as employee,
        sprint_id as sprintId,
        sprint_name as sprintName,
        from_status_name as fromStatusName,
        sum(cnt) as transitionsCount
    from matched
    group by employee, sprint_id, sprint_name, from_status_name
    order by sprint_id nulls last, employee, from_status_name
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
    // Спринт строки определяется по попаданию transition_date в период спринта.
    @Query(value = """
        select
            t.issue_key as issueKey,
            s.sprint_id as sprintId,
            s.sprint_name as sprintName,
            coalesce(t.from_status_name, fs.status_name) as fromStatusName,
            coalesce(t.to_status_name, ts.status_name) as toStatusName,
            t.developer as developer,
            t.transition_date as transitionDate
        from jira_sprint_status_transition t
        join lateral (
            select ps.sprint_id, ps.sprint_name
            from project_jira_sprint ps
            where (:useSprints = false or ps.sprint_id in (:sprintIds))
              and ps.start_date is not null
              and ps.end_date is not null
              and t.transition_date >= ps.start_date::timestamptz
              and t.transition_date <  (ps.end_date + 1)::timestamptz
            order by ps.start_date desc, ps.sprint_id desc
            limit 1
        ) s on true
        left join project_jira_status fs on fs.status_id = t.from_status_id
        left join project_jira_status ts on ts.status_id = t.to_status_id
        where
            ( (t.final_assignee is not null and t.final_assignee in (:employees))
              or
              (t.developer is not null and t.developer in (:employees)) )
          and (:useFrom = false or t.from_status_id in (:fromStatusIds))
          and (:useTo   = false or t.to_status_id   in (:toStatusIds))
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

    @Query(value = """
    with matched as (
        select
            t.final_assignee as employee,
            t.sprint_id as sprint_id,
            t.sprint_name as sprint_name,
            t.issue_key as issue_key,
            coalesce(t.from_status_name, fs.status_name) as from_status_name,
            count(*) as cnt
        from jira_sprint_status_transition t
        left join project_jira_status fs on fs.status_id = t.from_status_id
        where t.final_assignee is not null
          and t.final_assignee in (:employees)
          and t.issue_key is not null
          and (:useFrom = false or t.from_status_id in (:fromStatusIds))
          and (:useTo   = false or t.to_status_id   in (:toStatusIds))
          and (:useSprints = false or t.sprint_id in (:sprintIds))
        group by t.final_assignee, t.sprint_id, t.sprint_name, t.issue_key, coalesce(t.from_status_name, fs.status_name)

        union all

        select
            t.developer as employee,
            t.sprint_id as sprint_id,
            t.sprint_name as sprint_name,
            t.issue_key as issue_key,
            coalesce(t.from_status_name, fs.status_name) as from_status_name,
            count(*) as cnt
        from jira_sprint_status_transition t
        left join project_jira_status fs on fs.status_id = t.from_status_id
        where t.developer is not null
          and t.developer in (:employees)
          and t.issue_key is not null
          and (:useFrom = false or t.from_status_id in (:fromStatusIds))
          and (:useTo   = false or t.to_status_id   in (:toStatusIds))
          and (:useSprints = false or t.sprint_id in (:sprintIds))
          and (t.final_assignee is null or t.developer <> t.final_assignee)
        group by t.developer, t.sprint_id, t.sprint_name, t.issue_key, coalesce(t.from_status_name, fs.status_name)
    )
    select
        employee as employee,
        sprint_id as sprintId,
        sprint_name as sprintName,
        issue_key as issueKey,
        from_status_name as fromStatusName,
        sum(cnt) as transitionsCount
    from matched
    group by employee, sprint_id, sprint_name, issue_key, from_status_name
    order by sprint_id nulls last, employee, issue_key, from_status_name
    """, nativeQuery = true)
    List<TransitionTaskReportRow> getTaskReportByStatusLists(
            @Param("employees") List<String> employees,
            @Param("useFrom") boolean useFrom,
            @Param("fromStatusIds") List<Long> fromStatusIds,
            @Param("useTo") boolean useTo,
            @Param("toStatusIds") List<Long> toStatusIds,
            @Param("useSprints") boolean useSprints,
            @Param("sprintIds") List<Long> sprintIds
    );

    @Query(value = """
    with matched as (
        select
            t.final_assignee as employee,
            t.sprint_id as sprint_id,
            t.sprint_name as sprint_name,
            t.issue_key as issue_key,
            coalesce(t.from_status_name, fs.status_name) as from_status_name,
            count(*) as cnt
        from jira_sprint_status_transition t
        left join project_jira_status fs on fs.status_id = t.from_status_id
        where t.final_assignee is not null
          and t.final_assignee in (:employees)
          and t.issue_key is not null
          and (:useFrom = false or t.from_status_id in (:fromStatusIds))
          and (:useTo   = false or t.to_status_id   in (:toStatusIds))
          and t.transition_date >= :periodStart
          and t.transition_date <= :periodEnd
        group by t.final_assignee, t.sprint_id, t.sprint_name, t.issue_key, coalesce(t.from_status_name, fs.status_name)

        union all

        select
            t.developer as employee,
            t.sprint_id as sprint_id,
            t.sprint_name as sprint_name,
            t.issue_key as issue_key,
            coalesce(t.from_status_name, fs.status_name) as from_status_name,
            count(*) as cnt
        from jira_sprint_status_transition t
        left join project_jira_status fs on fs.status_id = t.from_status_id
        where t.developer is not null
          and t.developer in (:employees)
          and t.issue_key is not null
          and (:useFrom = false or t.from_status_id in (:fromStatusIds))
          and (:useTo   = false or t.to_status_id   in (:toStatusIds))
          and t.transition_date >= :periodStart
          and t.transition_date <= :periodEnd
          and (t.final_assignee is null or t.developer <> t.final_assignee)
        group by t.developer, t.sprint_id, t.sprint_name, t.issue_key, coalesce(t.from_status_name, fs.status_name)
    )
    select
        employee as employee,
        null as sprintId,
        null as sprintName,
        issue_key as issueKey,
        from_status_name as fromStatusName,
        sum(cnt) as transitionsCount
    from matched
    group by employee, issue_key, from_status_name
    order by employee, issue_key, from_status_name
    """, nativeQuery = true)
    List<TransitionTaskReportRow> getTaskReportByPeriod(
            @Param("employees") List<String> employees,
            @Param("useFrom") boolean useFrom,
            @Param("fromStatusIds") List<Long> fromStatusIds,
            @Param("useTo") boolean useTo,
            @Param("toStatusIds") List<Long> toStatusIds,
            @Param("periodStart") java.time.OffsetDateTime periodStart,
            @Param("periodEnd") java.time.OffsetDateTime periodEnd
    );

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
          and t.transition_date >= :periodStart
          and t.transition_date <= :periodEnd
        order by t.transition_date desc nulls last, t.issue_key
        """, nativeQuery = true)
    List<TransitionDetailRow> getDetailsByPeriod(
            @Param("employees") List<String> employees,
            @Param("useFrom") boolean useFrom,
            @Param("fromStatusIds") List<Long> fromStatusIds,
            @Param("useTo") boolean useTo,
            @Param("toStatusIds") List<Long> toStatusIds,
            @Param("periodStart") java.time.OffsetDateTime periodStart,
            @Param("periodEnd") java.time.OffsetDateTime periodEnd
    );

    @Query(value = """
        with matched as (
            select
                t.sprint_id as sprint_id,
                t.sprint_name as sprint_name,
                t.final_assignee as employee,
                t.issue_key as issue_key,
                t.issue_summary as issue_summary,
                t.epic_key as epic_key,
                t.from_status_name as status_at_sprint_start,
                t.to_status_name as status_at_sprint_end,
                t.transition_date as transition_date,
                row_number() over (
                    partition by t.sprint_id, t.final_assignee, t.issue_key
                    order by t.transition_date asc nulls last, t.id asc
                ) as start_rn,
                row_number() over (
                    partition by t.sprint_id, t.final_assignee, t.issue_key
                    order by t.transition_date desc nulls last, t.id desc
                ) as end_rn
            from jira_sprint_status_transition t
            where t.final_assignee is not null
              and t.final_assignee in (:employees)
              and t.issue_key is not null
              and (:useSprints = false or t.sprint_id in (:sprintIds))

            union all

            select
                t.sprint_id as sprint_id,
                t.sprint_name as sprint_name,
                t.developer as employee,
                t.issue_key as issue_key,
                t.issue_summary as issue_summary,
                t.epic_key as epic_key,
                t.from_status_name as status_at_sprint_start,
                t.to_status_name as status_at_sprint_end,
                t.transition_date as transition_date,
                row_number() over (
                    partition by t.sprint_id, t.developer, t.issue_key
                    order by t.transition_date asc nulls last, t.id asc
                ) as start_rn,
                row_number() over (
                    partition by t.sprint_id, t.developer, t.issue_key
                    order by t.transition_date desc nulls last, t.id desc
                ) as end_rn
            from jira_sprint_status_transition t
            where t.developer is not null
              and t.developer in (:employees)
              and t.issue_key is not null
              and (:useSprints = false or t.sprint_id in (:sprintIds))
              and (t.final_assignee is null or t.developer <> t.final_assignee)
        ),
        first_transitions as (
            select
                sprint_id,
                sprint_name,
                employee,
                issue_key,
                issue_summary,
                epic_key,
                status_at_sprint_start
            from matched
            where start_rn = 1
        ),
        last_transitions as (
            select
                sprint_id,
                sprint_name,
                employee,
                issue_key,
                issue_summary,
                epic_key,
                status_at_sprint_end,
                transition_date
            from matched
            where end_rn = 1
        )
        select
            l.sprint_id as sprintId,
            l.sprint_name as sprintName,
            l.employee as employee,
            l.issue_key as issueKey,
            l.issue_summary as issueSummary,
            coalesce(l.epic_key, f.epic_key) as epicKey,
            f.status_at_sprint_start as statusAtSprintStart,
            l.status_at_sprint_end as statusAtSprintEnd,
            l.transition_date as transitionDate
        from last_transitions l
        left join first_transitions f
            on f.sprint_id = l.sprint_id
           and f.employee = l.employee
           and f.issue_key = l.issue_key
        order by l.sprint_id nulls last, l.employee, l.issue_key
        """, nativeQuery = true)
    List<OutOfPlanTaskProjection> getLatestTasksForPlanning(
            @Param("employees") List<String> employees,
            @Param("useSprints") boolean useSprints,
            @Param("sprintIds") List<Long> sprintIds
    );

    /**
     * Сводка «сколько спринтов задача шла от стартового статуса до целевого».
     * Фильтр по спринтам отбирает ЗАДАЧИ (у задачи есть хотя бы один переход в переданных спринтах),
     * а границы и счётчики считаются по ВСЕМ переходам этих задач — иначе задача, у которой
     * Open или Tested случился в спринте вне списка, выпадала из отчёта целиком.
     */
    @Query(value = """
        with scoped_issues as (
            select distinct t.issue_key
            from jira_sprint_status_transition t
            where t.issue_key is not null
              and (:useSprints = false or t.sprint_id in (:sprintIds))
        ),
        matched as (
            select
                t.id as id,
                t.final_assignee as employee,
                t.sprint_id as sprint_id,
                t.sprint_name as sprint_name,
                t.issue_key as issue_key,
                t.issue_summary as issue_summary,
                t.from_status_id as from_status_id,
                coalesce(t.from_status_name, fs.status_name) as from_status_name,
                t.to_status_id as to_status_id,
                coalesce(t.to_status_name, ts.status_name) as to_status_name,
                t.transition_date as transition_date
            from jira_sprint_status_transition t
            join scoped_issues si on si.issue_key = t.issue_key
            left join project_jira_status fs on fs.status_id = t.from_status_id
            left join project_jira_status ts on ts.status_id = t.to_status_id
            where t.final_assignee is not null
              and t.final_assignee in (:employees)
              and t.issue_key is not null

            union all

            select
                t.id as id,
                t.developer as employee,
                t.sprint_id as sprint_id,
                t.sprint_name as sprint_name,
                t.issue_key as issue_key,
                t.issue_summary as issue_summary,
                t.from_status_id as from_status_id,
                coalesce(t.from_status_name, fs.status_name) as from_status_name,
                t.to_status_id as to_status_id,
                coalesce(t.to_status_name, ts.status_name) as to_status_name,
                t.transition_date as transition_date
            from jira_sprint_status_transition t
            join scoped_issues si on si.issue_key = t.issue_key
            left join project_jira_status fs on fs.status_id = t.from_status_id
            left join project_jira_status ts on ts.status_id = t.to_status_id
            where t.developer is not null
              and t.developer in (:employees)
              and t.issue_key is not null
              and (t.final_assignee is null or t.developer <> t.final_assignee)
        ),
        first_open as (
            select
                employee,
                issue_key,
                min(transition_date) as start_date
            from matched
            where (:useStart = false or from_status_id in (:startStatusIds) or to_status_id in (:startStatusIds))
            group by employee, issue_key
        ),
        last_target as (
            select
                m.employee,
                m.issue_key,
                max(m.transition_date) as end_date
            from matched m
            join first_open fo
              on fo.employee = m.employee
             and fo.issue_key = m.issue_key
            where (:useTarget = false or m.to_status_id in (:targetStatusIds))
              and m.transition_date >= fo.start_date
            group by m.employee, m.issue_key
        ),
        boundaries as (
            select
                fo.employee,
                fo.issue_key,
                fo.start_date,
                lt.end_date
            from first_open fo
            join last_target lt
              on lt.employee = fo.employee
             and lt.issue_key = fo.issue_key
            where lt.end_date >= fo.start_date
        ),
        start_rows as (
            select distinct on (b.employee, b.issue_key)
                b.employee as employee,
                b.issue_key as issue_key,
                m.issue_summary as issue_summary,
                m.sprint_id as start_sprint_id,
                m.sprint_name as start_sprint_name,
                b.start_date as start_date
            from boundaries b
            join matched m
              on m.employee = b.employee
             and m.issue_key = b.issue_key
             and m.transition_date = b.start_date
            order by b.employee, b.issue_key, m.id
        ),
        end_rows as (
            select distinct on (b.employee, b.issue_key)
                b.employee as employee,
                b.issue_key as issue_key,
                m.issue_summary as issue_summary,
                m.sprint_id as end_sprint_id,
                m.sprint_name as end_sprint_name,
                b.end_date as end_date
            from boundaries b
            join matched m
              on m.employee = b.employee
             and m.issue_key = b.issue_key
             and m.transition_date = b.end_date
            where (:useTarget = false or m.to_status_id in (:targetStatusIds))
            order by b.employee, b.issue_key, m.id desc
        ),
        sprint_counts as (
            -- сколько спринтов задача шла: считаем спринты, чей период пересекается с [start_date, end_date],
            -- а не спринты, в которых были переходы (иначе спринт «простоя» без переходов не учитывался)
            select
                b.employee,
                b.issue_key,
                (
                    select count(*)
                    from project_jira_sprint ps
                    where ps.start_date is not null
                      and ps.end_date is not null
                      and ps.start_date::timestamptz     <= b.end_date
                      and (ps.end_date + 1)::timestamptz >  b.start_date
                ) as sprint_count
            from boundaries b
        ),
        last_resolved as (
            select
                m.employee,
                m.issue_key,
                max(m.transition_date) as resolved_end_date
            from matched m
            join first_open fo
              on fo.employee = m.employee
             and fo.issue_key = m.issue_key
            where (
                    m.to_status_id = 13936
                    or lower(coalesce(m.to_status_name, '')) = 'решена'
                  )
              and m.transition_date >= fo.start_date
            group by m.employee, m.issue_key
        ),
        resolved_sprint_counts as (
            -- то же самое, но до последнего перехода в «Решена»
            select
                lr.employee,
                lr.issue_key,
                (
                    select count(*)
                    from project_jira_sprint ps
                    where ps.start_date is not null
                      and ps.end_date is not null
                      and ps.start_date::timestamptz     <= lr.resolved_end_date
                      and (ps.end_date + 1)::timestamptz >  fo.start_date
                ) as resolved_sprint_count
            from last_resolved lr
            join first_open fo
              on fo.employee = lr.employee
             and fo.issue_key = lr.issue_key
        ),
        reopened_counts as (
            select
                b.employee,
                b.issue_key,
                count(*) as reopened_count
            from boundaries b
            join matched m
              on m.employee = b.employee
             and m.issue_key = b.issue_key
             and m.transition_date >= b.start_date
             and m.transition_date <= b.end_date
            where m.to_status_id = 4
            group by b.employee, b.issue_key
        ),
        reopened_from_review_counts as (
            select
                b.employee,
                b.issue_key,
                count(*) as reopened_from_review_count
            from boundaries b
            join matched m
              on m.employee = b.employee
             and m.issue_key = b.issue_key
             and m.transition_date >= b.start_date
             and m.transition_date <= b.end_date
            where m.to_status_id = 4
              and lower(coalesce(m.from_status_name, '')) like '%review%'
            group by b.employee, b.issue_key
        )
        select
            b.employee as employee,
            b.issue_key as issueKey,
            coalesce(er.issue_summary, sr.issue_summary) as issueSummary,
            sr.start_sprint_id as startSprintId,
            sr.start_sprint_name as startSprintName,
            er.end_sprint_id as endSprintId,
            er.end_sprint_name as endSprintName,
            b.start_date as startDate,
            b.end_date as endDate,
            sc.sprint_count as sprintCount,
            coalesce(rsc.resolved_sprint_count, 0) as resolvedSprintCount,
            coalesce(rc.reopened_count, 0) as reopenedCount,
            coalesce(rfrc.reopened_from_review_count, 0) as reopenedFromReviewCount
        from boundaries b
        join sprint_counts sc
          on sc.employee = b.employee
         and sc.issue_key = b.issue_key
        left join resolved_sprint_counts rsc
          on rsc.employee = b.employee
         and rsc.issue_key = b.issue_key
        left join reopened_counts rc
          on rc.employee = b.employee
         and rc.issue_key = b.issue_key
        left join reopened_from_review_counts rfrc
          on rfrc.employee = b.employee
         and rfrc.issue_key = b.issue_key
        left join start_rows sr
          on sr.employee = b.employee
         and sr.issue_key = b.issue_key
        left join end_rows er
          on er.employee = b.employee
         and er.issue_key = b.issue_key
        order by b.employee, sc.sprint_count desc, b.issue_key
        """, nativeQuery = true)
    List<TransitionLeadTimeSummaryRow> getLeadTimeSummary(
            @Param("employees") List<String> employees,
            @Param("useStart") boolean useStart,
            @Param("startStatusIds") List<Long> startStatusIds,
            @Param("useTarget") boolean useTarget,
            @Param("targetStatusIds") List<Long> targetStatusIds,
            @Param("useSprints") boolean useSprints,
            @Param("sprintIds") List<Long> sprintIds
    );

    /**
     * Детализация переходов. Фильтр по спринтам отбирает ЗАДАЧИ (у задачи есть хотя бы один
     * переход в переданных спринтах), а сами переходы берутся все — иначе жизненный цикл задачи
     * рвётся и переходы вне переданных спринтов теряются.
     */
    @Query(value = """
        with scoped_issues as (
            select distinct t.issue_key
            from jira_sprint_status_transition t
            where t.issue_key is not null
              and (:useSprints = false or t.sprint_id in (:sprintIds))
        ),
        matched as (
            select
                t.id as id,
                t.final_assignee as employee,
                t.sprint_id as sprint_id,
                t.sprint_name as sprint_name,
                t.issue_key as issue_key,
                t.issue_summary as issue_summary,
                t.from_status_id as from_status_id,
                coalesce(t.from_status_name, fs.status_name) as from_status_name,
                t.to_status_id as to_status_id,
                coalesce(t.to_status_name, ts.status_name) as to_status_name,
                t.transition_date as transition_date
            from jira_sprint_status_transition t
            join scoped_issues si on si.issue_key = t.issue_key
            left join project_jira_status fs on fs.status_id = t.from_status_id
            left join project_jira_status ts on ts.status_id = t.to_status_id
            where t.final_assignee is not null
              and t.final_assignee in (:employees)
              and t.issue_key is not null

            union all

            select
                t.id as id,
                t.developer as employee,
                t.sprint_id as sprint_id,
                t.sprint_name as sprint_name,
                t.issue_key as issue_key,
                t.issue_summary as issue_summary,
                t.from_status_id as from_status_id,
                coalesce(t.from_status_name, fs.status_name) as from_status_name,
                t.to_status_id as to_status_id,
                coalesce(t.to_status_name, ts.status_name) as to_status_name,
                t.transition_date as transition_date
            from jira_sprint_status_transition t
            join scoped_issues si on si.issue_key = t.issue_key
            left join project_jira_status fs on fs.status_id = t.from_status_id
            left join project_jira_status ts on ts.status_id = t.to_status_id
            where t.developer is not null
              and t.developer in (:employees)
              and t.issue_key is not null
              and (t.final_assignee is null or t.developer <> t.final_assignee)
        ),
        first_open as (
            select
                employee,
                issue_key,
                min(transition_date) as start_date
            from matched
            where (:useStart = false or from_status_id in (:startStatusIds) or to_status_id in (:startStatusIds))
            group by employee, issue_key
        ),
        last_target as (
            select
                m.employee,
                m.issue_key,
                max(m.transition_date) as end_date
            from matched m
            join first_open fo
              on fo.employee = m.employee
             and fo.issue_key = m.issue_key
            where (:useTarget = false or m.to_status_id in (:targetStatusIds))
              and m.transition_date >= fo.start_date
            group by m.employee, m.issue_key
        ),
        boundaries as (
            select
                fo.employee,
                fo.issue_key,
                fo.start_date,
                lt.end_date
            from first_open fo
            join last_target lt
              on lt.employee = fo.employee
             and lt.issue_key = fo.issue_key
            where lt.end_date >= fo.start_date
        )
        select
            m.employee as employee,
            m.issue_key as issueKey,
            m.issue_summary as issueSummary,
            m.sprint_id as sprintId,
            m.sprint_name as sprintName,
            m.from_status_name as fromStatusName,
            m.to_status_name as toStatusName,
            m.transition_date as transitionDate
        from matched m
        join boundaries b
          on b.employee = m.employee
         and b.issue_key = m.issue_key
         and m.transition_date >= b.start_date
         and m.transition_date <= b.end_date
        order by m.employee, m.issue_key, m.transition_date, m.id
        """, nativeQuery = true)
    List<TransitionLeadTimeDetailRow> getLeadTimeDetails(
            @Param("employees") List<String> employees,
            @Param("useStart") boolean useStart,
            @Param("startStatusIds") List<Long> startStatusIds,
            @Param("useTarget") boolean useTarget,
            @Param("targetStatusIds") List<Long> targetStatusIds,
            @Param("useSprints") boolean useSprints,
            @Param("sprintIds") List<Long> sprintIds
    );
}

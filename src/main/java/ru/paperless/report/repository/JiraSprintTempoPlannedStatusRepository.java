package ru.paperless.report.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import ru.paperless.report.dto.TempoPlannedDetailRow;
import ru.paperless.report.dto.TempoPlannedSummaryRow;
import ru.paperless.report.entity.JiraSprintTempoPlannedStatus;

import java.util.List;

public interface JiraSprintTempoPlannedStatusRepository extends JpaRepository<JiraSprintTempoPlannedStatus, Long> {

    @Query("""
                select new ru.paperless.report.dto.TempoPlannedDetailRow(
                    t.sprintId,
                    t.sprintName,
                    t.employee,
                    t.issueKey,
                    sum(coalesce(t.plannedSeconds, 0)),
                    min(t.statusAtSprintStart),
                    min(t.statusAtSprintEnd)
                ) 
                from JiraSprintTempoPlannedStatus t
                where (:useEmployees = false or t.employee in :employees)
                  and (:useSprints = false or t.sprintId in :sprintIds)
                group by t.sprintId, t.sprintName, t.employee, t.issueKey
                order by t.sprintId, t.employee, t.issueKey
            """)
    List<TempoPlannedDetailRow> getDetails(
            @Param("useEmployees") boolean useEmployees,
            @Param("employees") List<String> employees,
            @Param("useSprints") boolean useSprints,
            @Param("sprintIds") List<Long> sprintIds
    );

    @Query("""
                select new ru.paperless.report.dto.TempoPlannedSummaryRow(
                    t.employee,
                    t.sprintId,
                    t.sprintName,

                    count(distinct t.issueKey),

                    count(distinct (case
                        when (:useDone = true and t.statusAtSprintEnd in :doneStatusNames)
                        then t.issueKey
                        else null
                    end)),

                    count(distinct (case
                        when (:useNotClosed = true and t.statusAtSprintEnd in :notClosedStatusNames)
                        then t.issueKey
                        else null
                    end))
                )
                from JiraSprintTempoPlannedStatus t
                where (:useEmployees = false or t.employee in :employees)
                  and (:useSprints = false or t.sprintId in :sprintIds)
                  and t.issueKey is not null
                group by t.employee, t.sprintId, t.sprintName
                order by t.employee, t.sprintId
            """)
    List<TempoPlannedSummaryRow> getSummary(
            @Param("useEmployees") boolean useEmployees,
            @Param("employees") List<String> employees,
            @Param("useSprints") boolean useSprints,
            @Param("sprintIds") List<Long> sprintIds,

            @Param("useDone") boolean useDone,
            @Param("doneStatusNames") List<String> doneStatusNames,

            @Param("useNotClosed") boolean useNotClosed,
            @Param("notClosedStatusNames") List<String> notClosedStatusNames
    );
}
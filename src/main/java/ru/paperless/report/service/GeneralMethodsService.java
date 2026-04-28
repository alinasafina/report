package ru.paperless.report.service;

import ru.paperless.report.client.dto.request.SprintIdsRequest;
import ru.paperless.report.client.dto.request.TransitionExportRequest;
import ru.paperless.report.client.dto.response.JiraWorklogResponse;
import ru.paperless.report.dto.JiraFieldDto;
import ru.paperless.report.dto.SprintInfo;
import ru.paperless.report.entity.ProjectJiraSprint;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;

public interface GeneralMethodsService {
    String extractDeveloperValue(Map<String, Object> fields);

    OffsetDateTime parseOffsetDateTimeSafe(String iso);

    List<SprintInfo> extractSprintsDetailed(Map<String, Object> fields);

    OffsetDateTime parseSprintDate(String iso);

    List<Long> resolveSprintIds(SprintIdsRequest req);

    @SuppressWarnings("unchecked")
    String extractAssigneeDisplayName(Map<String, Object> fields);

    String extractDescriptionValue(Map<String, Object> fields);

    Long parseLongSafe(String s);

    Double parseEstimateHours(String raw);

    Set<String> extractIssuePeople(String assignee, String developer);

    SprintInfo pickMainSprint(List<SprintInfo> sprints);


    String extractAuthorDisplayName(JiraWorklogResponse.Author a);

    List<Long> resolveSprintIds(TransitionExportRequest req);

    List<JiraFieldDto> getJiraFields();

    OffsetDateTime toOffsetStartOfDay(LocalDate d);

    LocalDate safeDate(String iso);

    List<ProjectJiraSprint> getSprintsBySprintIds(SprintIdsRequest req);
}

package ru.paperless.report.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import ru.paperless.report.client.JiraFeignClient;
import ru.paperless.report.client.dto.request.SprintIdsRequest;
import ru.paperless.report.client.dto.request.JiraSearchRequest;
import ru.paperless.report.client.dto.response.*;
import ru.paperless.report.entity.Employee;
import ru.paperless.report.entity.JiraSprintEmployeeEffort;
import ru.paperless.report.entity.ProjectJiraSprint;
import ru.paperless.report.repository.EmployeeRepository;
import ru.paperless.report.repository.JiraSprintEmployeeEffortRepository;
import ru.paperless.report.repository.ProjectJiraSprintRepository;

import java.time.OffsetDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class JiraEffortExportServiceImpl implements JiraEffortExportService {

    private final JiraFeignClient jiraClient;

    private final EmployeeRepository employeeRepo;
    private final JiraSprintEmployeeEffortRepository effortRepo;
    private final ProjectJiraSprintRepository sprintRepo;

    private final GeneralMethodsService generalMethodsService;

    @org.springframework.beans.factory.annotation.Value("${jira.sprintFieldId}")
    private String sprintFieldId;
    @org.springframework.beans.factory.annotation.Value("${jira.developerFieldId}")
    private String developerFieldId;
    @org.springframework.beans.factory.annotation.Value("${jira.estimateFieldId}")
    private String estimateFieldId;
    @org.springframework.beans.factory.annotation.Value("${jira.estimateFieldName}")
    private String estimateFieldName;
    @org.springframework.beans.factory.annotation.Value("${jira.epicLinkFieldId}")
    private String epicLinkFieldId;
    @org.springframework.beans.factory.annotation.Value("${jira.project-key}")
    private String projectKey;

    /** Период спринта из project_jira_sprint. Нужен только чтобы проставить спринт списания, фильтра по нему нет. */
    private record SprintPeriod(Long id, String name, OffsetDateTime start, OffsetDateTime endExclusive) {
        boolean contains(OffsetDateTime dt) {
            return dt != null && !dt.isBefore(start) && dt.isBefore(endExclusive);
        }
    }

    @Override
    public int exportEffort(SprintIdsRequest req) throws Exception {

        // 1) мои сотрудники (employee.selectable = true) — единственный фильтр по людям
        Set<String> employeeFilter = employeeRepo.findBySelectableTrue().stream()
                .map(Employee::getFullName)
                .filter(StringUtils::hasText)
                .map(String::trim)
                .collect(Collectors.toSet());
        if (employeeFilter.isEmpty()) {
            log.warn("В employee нет ни одного сотрудника с selectable = true.");
            return 0;
        }

        // 2) спринты
        List<Long> sprintIds = generalMethodsService.resolveSprintIds(req);
        if (sprintIds.isEmpty()) {
            log.info("Нет спринтов для обработки.");
            return 0;
        }

        // 2.1) периоды спринтов — только для проставления sprint_last_logged_*, списания по ним НЕ фильтруются
        List<SprintPeriod> periods = loadSprintPeriods(sprintIds);

        // 3) все задачи переданных спринтов
        String jql = "project = " + projectKey + " AND Sprint in (" +
                sprintIds.stream().map(String::valueOf).collect(Collectors.joining(",")) + ")";
        log.info("JQL: {}", jql);

        int pageSize = 200;
        int startAt = 0;
        int total = Integer.MAX_VALUE;

        List<JiraSprintEmployeeEffort> toSave = new ArrayList<>();

        while (startAt < total) {
            JiraSearchRequest searchReq = JiraSearchRequest.builder()
                    .jql(jql)
                    .fields(List.of("key"))
                    .startAt(startAt)
                    .maxResults(pageSize)
                    .build();

            JiraSearchResponse searchResp = jiraClient.search(searchReq);
            total = Optional.ofNullable(searchResp.getTotal()).orElse(0);

            List<JiraSearchResponse.Issue> issues = Optional.ofNullable(searchResp.getIssues()).orElse(List.of());
            if (issues.isEmpty()) break;

            log.info("Обрабатываются с {} следующие {}, всего {}", startAt, pageSize, total);

            for (JiraSearchResponse.Issue issue : issues) {
                String key = issue.getKey();
                if (!StringUtils.hasText(key)) continue;

                // 4) задача целиком: changelog + поля + worklog
                String fieldsParam = String.join(",", "assignee", sprintFieldId, developerFieldId, estimateFieldId,
                        epicLinkFieldId, "worklog", "summary", "labels");
                JiraIssueResponse full = getIssueWithRetry(key, fieldsParam);

                String summary = extractIssueSummary(full);
                if (summary != null) {
                    String lower = summary.toLowerCase();
                    if (lower.contains("дежурство") || lower.contains("консультация")) continue;
                }

                String assignee = generalMethodsService.extractAssigneeDisplayName(full.getFields());
                String developer = generalMethodsService.extractDeveloperValue(full.getFields());

                // 5) все списания по задаче; фильтруем только по автору — мои сотрудники
                List<JiraWorklogResponse.Worklog> worklogs = loadAllWorklogs(key);
                if (worklogs.isEmpty()) continue;

                JiraIssueSprintResponse.Sprint firstSprint = resolveFirstSprint(key);
                Double firstEstimateHours = extractFirstEstimateHoursFromChangelog(full);
                String epicKey = full.getFields().get(epicLinkFieldId) != null
                        ? full.getFields().get(epicLinkFieldId).toString()
                        : null;
                String labels = extractLabels(full);

                for (JiraWorklogResponse.Worklog w : worklogs) {
                    String author = generalMethodsService.extractAuthorDisplayName(w.getAuthor());
                    if (!StringUtils.hasText(author)) continue;

                    String employee = author.trim();
                    if (!employeeFilter.contains(employee)) continue;

                    // дата, за которую разработчик списал часы
                    OffsetDateTime loggedAt = generalMethodsService.parseOffsetDateTimeSafe(w.getStarted());
                    if (loggedAt == null) {
                        loggedAt = generalMethodsService.parseOffsetDateTimeSafe(w.getUpdated());
                    }

                    int seconds = Optional.ofNullable(w.getTimeSpentSeconds()).orElse(0);
                    if (seconds == 0) continue;

                    // спринт, в чей период попало списание (null, если ни в один из переданных)
                    SprintPeriod loggedSprint = findSprintByDate(periods, loggedAt);

                    JiraSprintEmployeeEffort row = JiraSprintEmployeeEffort.builder()
                            .projectKey(projectKey)
                            .sprintFirstId(firstSprint != null ? firstSprint.getId() : null)
                            .sprintFirstName(firstSprint != null ? firstSprint.getName() : null)
                            .sprintLastLoggedId(loggedSprint != null ? loggedSprint.id() : null)
                            .sprintLastLoggedName(loggedSprint != null ? loggedSprint.name() : null)
                            .issueKey(full.getKey())
                            .issueSummary(summary)
                            .assignee(assignee)
                            .developer(developer)
                            .employee(employee)
                            .firstEstimateHours(firstEstimateHours)
                            .loggedHours(seconds / 3600.0)
                            .collectedAt(loggedAt)
                            .epicKey(epicKey)
                            .labels(labels)
                            .build();

                    toSave.add(row);
                }
            }

            startAt += pageSize;
        }

        if (toSave.isEmpty()) return 0;
        effortRepo.saveAll(toSave);

        log.info("Сохранено {} списаний", toSave.size());
        return toSave.size();
    }

    // -------------------- helpers --------------------

    /** Границы переданных спринтов из project_jira_sprint; end_date включительно. */
    private List<SprintPeriod> loadSprintPeriods(List<Long> sprintIds) {
        List<ProjectJiraSprint> sprints = sprintRepo.findBySprintIds(sprintIds);

        List<SprintPeriod> periods = new ArrayList<>();
        for (ProjectJiraSprint s : sprints) {
            if (s.getStartDate() == null || s.getEndDate() == null) {
                log.warn("Спринт {} ({}): не заполнены start_date/end_date, спринт списания по нему не определить",
                        s.getSprintId(), s.getSprintName());
                continue;
            }
            periods.add(new SprintPeriod(
                    s.getSprintId(),
                    s.getSprintName(),
                    generalMethodsService.toOffsetStartOfDay(s.getStartDate()),
                    generalMethodsService.toOffsetStartOfDay(s.getEndDate().plusDays(1))
            ));
        }
        return periods;
    }

    private SprintPeriod findSprintByDate(List<SprintPeriod> periods, OffsetDateTime loggedAt) {
        if (loggedAt == null) return null;
        return periods.stream()
                .filter(p -> p.contains(loggedAt))
                .findFirst()
                .orElse(null);
    }

    /** Все worklog'и задачи, без фильтров. */
    private List<JiraWorklogResponse.Worklog> loadAllWorklogs(String issueKey) {
        List<JiraWorklogResponse.Worklog> all = new ArrayList<>();

        int startAt = 0;
        int max = 100;
        int total = Integer.MAX_VALUE;

        while (startAt < total) {
            JiraWorklogResponse wl = jiraClient.getWorklog(issueKey, startAt, max);
            total = Optional.ofNullable(wl.getTotal()).orElse(0);

            List<JiraWorklogResponse.Worklog> page = Optional.ofNullable(wl.getWorklogs()).orElse(List.of());
            if (page.isEmpty()) break;

            all.addAll(page);
            startAt += max;
        }
        return all;
    }

    private String extractIssueSummary(JiraIssueResponse full) {
        if (full == null || full.getFields() == null) return null;
        Object summary = full.getFields().get("summary");
        return summary != null ? summary.toString() : null;
    }

    private String extractLabels(JiraIssueResponse full) {
        if (full == null || full.getFields() == null) return null;
        Object labelsValue = full.getFields().get("labels");
        if (labelsValue instanceof List<?> labelsList) {
            return labelsList.stream()
                    .filter(Objects::nonNull)
                    .map(Object::toString)
                    .map(String::trim)
                    .filter(StringUtils::hasText)
                    .collect(Collectors.joining(";"));
        }
        return null;
    }

    /** Первый спринт задачи (для sprint_first_*). */
    private JiraIssueSprintResponse.Sprint resolveFirstSprint(String issueKey) {
        Set<JiraIssueSprintResponse.Sprint> sprints = jiraClient.getIssue(issueKey, sprintFieldId).getFields().values()
                .stream()
                .flatMap(List::stream)
                .collect(Collectors.toSet());

        return sprints.stream()
                .filter(s -> s.getStartODate() != null && s.getEndDate() != null)
                .min(Comparator.comparing(JiraIssueSprintResponse.Sprint::getEndDate))
                .orElse(null);
    }

    private Double extractFirstEstimateHoursFromChangelog(JiraIssueResponse full) {
        List<JiraIssueResponse.History> histories =
                Optional.ofNullable(full.getChangelog()).map(JiraIssueResponse.Changelog::getHistories).orElse(List.of());

        histories = histories.stream()
                .sorted(Comparator.comparing(h -> generalMethodsService.parseOffsetDateTimeSafe(h.getCreated()),
                        Comparator.nullsLast(Comparator.naturalOrder())))
                .toList();

        for (JiraIssueResponse.History h : histories) {
            if (h.getItems() == null) continue;

            for (JiraIssueResponse.Item it : h.getItems()) {
                boolean isTarget = (StringUtils.hasText(it.getField())
                        && estimateFieldName.equalsIgnoreCase(it.getField()));

                if (!isTarget) continue;

                String toVal = it.getToString();
                if (!StringUtils.hasText(toVal)) continue;

                Double hours = generalMethodsService.parseEstimateHours(toVal);
                if (hours != null) return hours;
            }
        }

        Object v = full.getFields() != null ? full.getFields().get(estimateFieldId) : null;
        if (v == null) return 0.0;
        Double parsed = generalMethodsService.parseEstimateHours(v.toString());
        return parsed != null ? parsed : 0.0;
    }

    private JiraIssueResponse getIssueWithRetry(String key, String fieldsParam) throws Exception {
        int pop = 0;
        while (pop < 10) {
            try {
                return jiraClient.getIssue(key, "changelog", fieldsParam);
            } catch (Exception e) {
                log.info("Попытка {} getIssue не удалась: {}", pop + 1, e.getMessage());
                pop++;
            }
        }
        throw new Exception("Кончились попытки на запрос getIssue, запустите задачу заново");
    }
}

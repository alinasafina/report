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

    /** Период спринта, переданного на вход (границы берём из project_jira_sprint). */
    private record SprintPeriod(Long id, String name, OffsetDateTime start, OffsetDateTime endExclusive) {
        boolean contains(OffsetDateTime dt) {
            return dt != null && !dt.isBefore(start) && dt.isBefore(endExclusive);
        }
    }

    /** Ключ агрегации: сотрудник + спринт, в период которого попало логирование. */
    private record EffortKey(String employee, Long sprintId) {
    }

    /** Накопленные часы сотрудника в рамках одного спринта + дата логирования. */
    private static final class EffortAgg {
        double hours;
        OffsetDateTime lastLoggedAt;

        void add(double h, OffsetDateTime loggedAt) {
            hours += h;
            if (loggedAt != null && (lastLoggedAt == null || loggedAt.isAfter(lastLoggedAt))) {
                lastLoggedAt = loggedAt;
            }
        }
    }

    @Override
    public int exportEffort(SprintIdsRequest req) throws Exception {

        // 1) selectable employees
        Set<String> employeeFilter = employeeRepo.findBySelectableTrue().stream()
                .map(Employee::getFullName)
                .filter(StringUtils::hasText)
                .map(String::trim)
                .collect(Collectors.toSet());

        // 2) sprint ids
        List<Long> sprintIds = generalMethodsService.resolveSprintIds(req);
        if (sprintIds.isEmpty()) {
            log.info("Нет спринтов для обработки.");
            return 0;
        }

        // 2.1) периоды переданных спринтов — именно по ним фильтруем логирование
        List<SprintPeriod> periods = loadSprintPeriods(sprintIds);
        if (periods.isEmpty()) {
            log.warn("У переданных спринтов {} нет заполненных start_date/end_date — фильтровать логирование не по чему.", sprintIds);
            return 0;
        }
        Map<Long, SprintPeriod> periodById = periods.stream()
                .collect(Collectors.toMap(SprintPeriod::id, p -> p));

        // 3) JQL
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
                    .fields(List.of("key", "assignee", developerFieldId))
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

                String assigneeFast = generalMethodsService.extractAssigneeDisplayName(issue.getFields());
                String developerFast = generalMethodsService.extractDeveloperValue(issue.getFields());

                if (!allowByPeopleFilter(assigneeFast, developerFast, employeeFilter)) continue;

                // heavy issue: changelog + assignee + dev + estimate + worklog
                String fieldsParam = String.join(",", "assignee", sprintFieldId, developerFieldId, estimateFieldId, epicLinkFieldId, "worklog", "summary", "labels");
                JiraIssueResponse full = getIssueWithRetry(key, fieldsParam);

                if (full.getFields().get("summary").toString().toLowerCase().contains("дежурство") ||
                        full.getFields().get("summary").toString().toLowerCase().contains("консультация")) {
                    continue;
                }

                String assignee = generalMethodsService.extractAssigneeDisplayName(full.getFields());
                String developer = generalMethodsService.extractDeveloperValue(full.getFields());
                if (!allowByPeopleFilter(assignee, developer, employeeFilter)) continue;

                // allowed employees = assignee/dev ∩ selectable employees
                Set<String> issuePeople = generalMethodsService.extractIssuePeople(assignee, developer);
                Set<String> allowedEmployees = issuePeople.stream()
                        .filter(employeeFilter::contains)
                        .collect(Collectors.toSet());
                if (allowedEmployees.isEmpty()) continue;

                // worklog'и, попавшие в периоды переданных спринтов, сгруппированные по (сотрудник, спринт)
                Map<EffortKey, EffortAgg> stats = loadLoggedHoursByEmployee(key, allowedEmployees, periods);
                if (stats.isEmpty()) continue; // в периодах переданных спринтов сотрудники ничего не логировали

                JiraIssueSprintResponse.Sprint firstSprint = resolveFirstSprint(key);
                Double firstEstimateHours = extractFirstEstimateHoursFromChangelog(full);

                for (Map.Entry<EffortKey, EffortAgg> e : stats.entrySet()) {
                    EffortKey k = e.getKey();
                    EffortAgg agg = e.getValue();
                    SprintPeriod sprint = periodById.get(k.sprintId());

                    JiraSprintEmployeeEffort row = JiraSprintEmployeeEffort.builder()
                            .projectKey(projectKey)
                            .sprintFirstId(firstSprint != null ? firstSprint.getId() : null)
                            .sprintFirstName(firstSprint != null ? firstSprint.getName() : null)
                            .sprintLastLoggedId(sprint != null ? sprint.id() : null)
                            .sprintLastLoggedName(sprint != null ? sprint.name() : null)
                            .issueKey(full.getKey())
                            .issueSummary(extractIssueSummary(full))
                            .assignee(assignee)
                            .developer(developer)
                            .employee(k.employee())
                            .firstEstimateHours(firstEstimateHours)
                            .loggedHours(agg.hours)
                            .collectedAt(agg.lastLoggedAt)
                            .epicKey(full.getFields().get(epicLinkFieldId) != null ? full.getFields().get(epicLinkFieldId).toString() : null)
                            .labels(extractLabels(full))
                            .build();

                    toSave.add(row);
                }
            }

            startAt += pageSize;
        }

        if (toSave.isEmpty()) return 0;
        effortRepo.saveAll(toSave);

        log.info("Сохранено {}", toSave.size());
        return toSave.size();
    }

    // -------------------- helpers --------------------

    /**
     * Границы переданных спринтов из project_jira_sprint.
     * end_date включительно: конец периода = end_date + 1 день (00:00), сравнение полуинтервалом [start, end).
     */
    private List<SprintPeriod> loadSprintPeriods(List<Long> sprintIds) {
        List<ProjectJiraSprint> sprints = sprintRepo.findBySprintIds(sprintIds);

        List<SprintPeriod> periods = new ArrayList<>();
        for (ProjectJiraSprint s : sprints) {
            if (s.getStartDate() == null || s.getEndDate() == null) {
                log.warn("Спринт {} ({}) пропущен: не заполнены start_date/end_date", s.getSprintId(), s.getSprintName());
                continue;
            }
            OffsetDateTime start = generalMethodsService.toOffsetStartOfDay(s.getStartDate());
            OffsetDateTime endExclusive = generalMethodsService.toOffsetStartOfDay(s.getEndDate().plusDays(1));
            periods.add(new SprintPeriod(s.getSprintId(), s.getSprintName(), start, endExclusive));
        }
        return periods;
    }

    private boolean allowByPeopleFilter(String assignee, String developer, Set<String> filter) {
        if (filter == null || filter.isEmpty()) return true;

        if (StringUtils.hasText(assignee) && filter.contains(assignee.trim())) return true;

        if (developer != null && !developer.isBlank()) {
            for (String part : developer.split("\\|")) {
                if (filter.contains(part.trim())) return true;
            }
        }
        return false;
    }

    private String extractIssueSummary(JiraIssueResponse full) {
        if (full == null || full.getFields() == null) {
            return null;
        }
        Object summary = full.getFields().get("summary");
        return summary != null ? summary.toString() : null;
    }

    private String extractLabels(JiraIssueResponse full) {
        if (full == null || full.getFields() == null) {
            return null;
        }
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

    /**
     * Суммируем списанные часы по issue только для allowedEmployees и только по тем worklog'ам,
     * дата списания которых (worklog.started — дата, за которую разработчик потратил часы)
     * попадает в период одного из переданных спринтов.
     * Результат сгруппирован по (сотрудник, спринт логирования).
     */
    private Map<EffortKey, EffortAgg> loadLoggedHoursByEmployee(String issueKey,
                                                                Set<String> allowedEmployees,
                                                                List<SprintPeriod> periods) {
        Map<EffortKey, EffortAgg> result = new HashMap<>();

        int startAt = 0;
        int max = 100;
        int total = Integer.MAX_VALUE;

        while (startAt < total) {
            JiraWorklogResponse wl = jiraClient.getWorklog(issueKey, startAt, max);
            total = Optional.ofNullable(wl.getTotal()).orElse(0);

            List<JiraWorklogResponse.Worklog> worklogs = Optional.ofNullable(wl.getWorklogs()).orElse(List.of());
            if (worklogs.isEmpty()) break;

            for (JiraWorklogResponse.Worklog w : worklogs) {
                String author = generalMethodsService.extractAuthorDisplayName(w.getAuthor());
                if (!StringUtils.hasText(author)) continue;

                String emp = author.trim();
                if (!allowedEmployees.contains(emp)) continue;

                // дата, в которую разработчик потратил часы
                OffsetDateTime loggedAt = generalMethodsService.parseOffsetDateTimeSafe(w.getStarted());
                if (loggedAt == null) {
                    loggedAt = generalMethodsService.parseOffsetDateTimeSafe(w.getUpdated());
                }
                if (loggedAt == null) continue;

                // фильтр: worklog должен попасть в период одного из переданных спринтов
                OffsetDateTime finalLoggedAt = loggedAt;
                SprintPeriod sprint = periods.stream()
                        .filter(p -> p.contains(finalLoggedAt))
                        .findFirst()
                        .orElse(null);
                if (sprint == null) continue;

                int seconds = Optional.ofNullable(w.getTimeSpentSeconds()).orElse(0);
                if (seconds == 0) continue;

                result.computeIfAbsent(new EffortKey(emp, sprint.id()), k -> new EffortAgg())
                        .add(seconds / 3600.0, loggedAt);
            }

            startAt += max;
        }

        return result;
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

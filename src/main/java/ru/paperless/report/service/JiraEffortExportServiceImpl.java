package ru.paperless.report.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import ru.paperless.report.client.JiraFeignClient;
import ru.paperless.report.client.dto.request.SprintIdsRequest;
import ru.paperless.report.client.dto.request.JiraSearchRequest;
import ru.paperless.report.client.dto.response.*;
import ru.paperless.report.dto.SprintInfo;
import ru.paperless.report.entity.Employee;
import ru.paperless.report.entity.JiraSprintEmployeeEffort;
import ru.paperless.report.repository.EmployeeRepository;
import ru.paperless.report.repository.JiraSprintEmployeeEffortRepository;

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

    private record LoggedStat(double hours,
                              OffsetDateTime lastActivity,
                              JiraIssueSprintResponse.Sprint actualSprint,
                              JiraIssueSprintResponse.Sprint firstSprint) {
    }

    @Override
    public int exportEffort(SprintIdsRequest req) throws Exception {

        // 1) selectable employees
        Set<String> employeeFilter = employeeRepo.findBySelectableTrue().stream()
                .map(Employee::getFullName)
                .filter(StringUtils::hasText)
                .map(String::trim)
                .collect(Collectors.toSet());

        // 2) actualSprint ids
        List<Long> sprintIds = generalMethodsService.resolveSprintIds(req);
        if (sprintIds.isEmpty()) {
            log.info("Нет спринтов для обработки.");
            return 0;
        }

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

                // heavy issue: changelog + actualSprint + assignee + dev + estimate + worklog
                String fieldsParam = String.join(",", "assignee", sprintFieldId, developerFieldId, estimateFieldId, epicLinkFieldId, "worklog", "summary");
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

                // fallback actualSprint (если нет lastActivity или не нашли спринт по дате)
                List<SprintInfo> sprints = generalMethodsService.extractSprintsDetailed(full.getFields());
                SprintInfo fallbackSprint = generalMethodsService.pickMainSprint(sprints);

                Double firstEstimateHours = extractFirstEstimateHoursFromChangelog(full);

                // worklog stats (hours + lastActivity)
                Map<String, LoggedStat> employeeStats = loadLoggedHoursByEmployee(key, allowedEmployees);

                for (String employee : allowedEmployees) {
                    LoggedStat st = employeeStats.getOrDefault(employee, new LoggedStat(0.0, null, null, null));

                    double logged = st.hours();

                    JiraSprintEmployeeEffort row = JiraSprintEmployeeEffort.builder()
                            .projectKey(projectKey)
                            .sprintFirstId(st.firstSprint != null ? st.firstSprint.getId() : null)
                            .sprintFirstName(st.firstSprint != null ? st.firstSprint.getName() : null)
                            .sprintLastLoggedId(st.actualSprint != null ? st.actualSprint.getId() : null)
                            .sprintLastLoggedName(st.actualSprint != null ? st.actualSprint.getName() : null)
                            .issueKey(full.getKey())
                            .assignee(assignee)
                            .developer(developer)
                            .employee(employee)
                            .firstEstimateHours(firstEstimateHours)
                            .loggedHours(logged)
                            .epicKey(full.getFields().get(epicLinkFieldId) != null ? full.getFields().get(epicLinkFieldId).toString() : null)
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

    /**
     * Суммируем списанные часы по issue только для allowedEmployees
     * + фиксируем дату последней активности (worklog updated/started).
     */
    private Map<String, LoggedStat> loadLoggedHoursByEmployee(String issueKey, Set<String> allowedEmployees) throws Exception {
        Map<String, Double> hoursMap = new HashMap<>();
        Map<String, OffsetDateTime> lastMap = new HashMap<>();
        JiraIssueSprintResponse.Sprint actualSprint = new JiraIssueSprintResponse.Sprint();
        JiraIssueSprintResponse.Sprint firstSprint = new JiraIssueSprintResponse.Sprint();

        int startAt = 0;
        int max = 100;
        int total = Integer.MAX_VALUE;

        Set<JiraIssueSprintResponse.Sprint> sprints = jiraClient.getIssue(issueKey, sprintFieldId).getFields().values()
                .stream()
                .flatMap(List::stream)
                .collect(Collectors.toSet());

        actualSprint = sprints.stream()
                .filter(s -> s.getEndDate() != null)
                .max(Comparator.comparing(JiraIssueSprintResponse.Sprint::getEndDate))
                .orElseThrow(() -> new Exception("Не найден дефолтный спринт"));

        firstSprint = sprints.stream()
                .filter(s -> s.getStartODate() != null)
                .min(Comparator.comparing(JiraIssueSprintResponse.Sprint::getEndDate))
                .orElseThrow(() -> new Exception("Не найден первый спринт"));

        while (startAt < total) {
            JiraWorklogResponse wl = jiraClient.getWorklog(issueKey, startAt, max);
            total = Optional.ofNullable(wl.getTotal()).orElse(0);

            List<JiraWorklogResponse.Worklog> worklogs = Optional.ofNullable(wl.getWorklogs()).orElse(List.of());
            worklogs = worklogs.stream()
                    .sorted(Comparator.comparing(JiraWorklogResponse.Worklog::getUpdated))
                    .toList();
            if (worklogs.isEmpty()) break;

            for (JiraWorklogResponse.Worklog w : worklogs) {
                String author = generalMethodsService.extractAuthorDisplayName(w.getAuthor());
                if (!StringUtils.hasText(author)) continue;

                String emp = author.trim();
                if (!allowedEmployees.contains(emp)) continue;

                int seconds = Optional.ofNullable(w.getTimeSpentSeconds()).orElse(0);
                double hours = seconds / 3600.0;
                hoursMap.merge(emp, hours, Double::sum);

                OffsetDateTime updated = generalMethodsService.parseOffsetDateTimeSafe(w.getUpdated());
                OffsetDateTime started = generalMethodsService.parseOffsetDateTimeSafe(w.getStarted());
                OffsetDateTime activity = updated != null ? updated : started;

                actualSprint = sprints.stream()
                        .filter(s -> s.getStartODate() != null && s.getEndDate() != null)
                        .filter(s -> {
                            assert updated != null;
                            return updated.isAfter(s.getStartODate()) && updated.isBefore(s.getEndDate());
                        })
                        .findFirst()
                        .orElse(actualSprint);

                if (activity != null) {
                    lastMap.merge(emp, activity, (oldVal, newVal) -> newVal.isAfter(oldVal) ? newVal : oldVal);
                }
            }

            startAt += max;
        }

        Map<String, LoggedStat> result = new HashMap<>();
        for (String emp : allowedEmployees) {
            result.put(emp, new LoggedStat(
                    hoursMap.getOrDefault(emp, 0.0),
                    lastMap.get(emp),
                    actualSprint,
                    firstSprint
            ));
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
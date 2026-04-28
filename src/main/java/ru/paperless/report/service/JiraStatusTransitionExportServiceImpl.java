package ru.paperless.report.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import ru.paperless.report.client.JiraFeignClient;
import ru.paperless.report.client.dto.request.JiraSearchRequest;
import ru.paperless.report.client.dto.response.JiraIssueResponse;
import ru.paperless.report.client.dto.response.JiraSearchResponse;
import ru.paperless.report.dto.SprintInfo;
import ru.paperless.report.dto.StatusSets;
import ru.paperless.report.entity.*;
import ru.paperless.report.client.dto.request.TransitionExportRequest;
import ru.paperless.report.repository.*;

import java.time.OffsetDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class JiraStatusTransitionExportServiceImpl implements JiraStatusTransitionExportService {

    private final JiraFeignClient jiraClient;
    private final GeneralMethodsService generalMethodsService;

    private final ProjectJiraStatusRepository statusRepo;
    private final EmployeeRepository employeeRepo;
    private final JiraSprintStatusTransitionRepository transitionRepo;

    @org.springframework.beans.factory.annotation.Value("${jira.sprintFieldId}")
    private String sprintFieldId;
    @org.springframework.beans.factory.annotation.Value("${jira.developerFieldId}")
    private String developerFieldId;
    @org.springframework.beans.factory.annotation.Value("${jira.project-key}")
    private String projectKey;

    @Override
    public int exportTransitions(TransitionExportRequest req) throws Exception {
        // 1) developers
        Set<String> developerFilter = getDevelopers();

        // 2) statuses: name -> id
        StatusSets statuses = resolveStatusIds(req);

        // 3) sprint ids
        List<Long> sprintIds = generalMethodsService.resolveSprintIds(req);

        if (sprintIds.isEmpty()) {
            log.info("Нет спринтов для обработки.");
            return 0;
        }

        // 4) JQL
        String jql = "project = " + projectKey + " AND Sprint in (" +
                sprintIds.stream().map(String::valueOf).collect(Collectors.joining(",")) + ")";
        log.info("JQL: {}", jql);

        // 5) search pagination (лёгкий поиск)
        int pageSize = 200;
        int startAt = 0;
        int total = Integer.MAX_VALUE;

        List<JiraSprintStatusTransition> toSave = new ArrayList<>();

        while (startAt < total) {
            JiraSearchRequest searchReq = JiraSearchRequest.builder()
                    .jql(jql)
                    .fields(List.of("key", "assignee", developerFieldId))
                    .startAt(startAt)
                    .maxResults(pageSize)
                    .build();

            JiraSearchResponse searchResp = jiraClient.search(searchReq);
            total = Optional.ofNullable(searchResp.getTotal()).orElse(0);
            log.info("Обрабатываются с {} следующие {}, всего {}", startAt, pageSize, total);

            List<JiraSearchResponse.Issue> issues = Optional.ofNullable(searchResp.getIssues()).orElse(List.of());
            if (issues.isEmpty()) break;

            for (JiraSearchResponse.Issue issue : issues) {
                String key = issue.getKey();
                if (!StringUtils.hasText(key)) continue;

                // ассайни/девелопер из search (чтобы не тянуть changelog зря)
                String assignee = generalMethodsService.extractAssigneeDisplayName(issue.getFields());
                String developer = generalMethodsService.extractDeveloperValue(issue.getFields());

                if (!allowByPeopleFilter(assignee, developer, developerFilter)) {
                    continue;
                }

                // тяжёлый запрос с changelog
                // fields: assignee,sprintField,developerField — чтобы получить спринты+dev в issue (для записи)
                String fieldsParam = String.join(",", "assignee", sprintFieldId, developerFieldId);
                JiraIssueResponse full = getIssue(jiraClient, key, fieldsParam);

                List<SprintInfo> sprints = generalMethodsService.extractSprintsDetailed(full.getFields());

                String fullAssignee = generalMethodsService.extractAssigneeDisplayName(full.getFields());
                String fullDeveloper = generalMethodsService.extractDeveloperValue(full.getFields());

                if (!allowByPeopleFilter(fullAssignee, fullDeveloper, developerFilter)) {
                    continue;
                }

                // спринт-имена из поля Sprint
                String sprintNames = extractSprintNames(full.getFields());

                // разбор changelog по status id
                List<JiraIssueResponse.History> histories =
                        Optional.ofNullable(full.getChangelog()).map(JiraIssueResponse.Changelog::getHistories).orElse(List.of());

                for (JiraIssueResponse.History h : histories) {
                    OffsetDateTime created = generalMethodsService.parseOffsetDateTimeSafe(h.getCreated());

                    if (h.getItems() == null) continue;

                    SprintInfo sprintAtTransition = pickSprintForTransition(sprints, created);

                    for (JiraIssueResponse.Item it : h.getItems()) {
                        if (!"status".equalsIgnoreCase(it.getField())) continue;

                        Long fromId = generalMethodsService.parseLongSafe(it.getFrom());
                        Long toId = generalMethodsService.parseLongSafe(it.getTo());

                        if (!statuses.fromIds().contains(fromId)) continue;
                        if (!statuses.toIds().contains(toId)) continue;

                        // сохраняем (sprint_id тут у issue может быть несколько — как в bash, пишем строкой имена)
                        // если тебе нужно сохранить именно sprint_id (и по одному на строку) — скажи, сделаю split.
                        JiraSprintStatusTransition row = JiraSprintStatusTransition.builder()
                                .projectKey(projectKey)
                                .sprintId(sprintAtTransition != null ? sprintAtTransition.id() : null)
                                .sprintName(sprintAtTransition != null ? sprintAtTransition.name() : null)
                                .issueKey(full.getKey())
                                .finalAssignee(fullAssignee)
                                .developer(fullDeveloper)
                                .fromStatusId(fromId)
                                .fromStatusName(it.getFromString())
                                .toStatusId(toId)
                                .toStatusName(it.getToString())
                                .transitionDate(created)
                                .build();

                        toSave.add(row);
                    }
                }
            }

            startAt += pageSize;
        }

        if (toSave.isEmpty()) return 0;

        transitionRepo.saveAll(toSave);

        log.info("Сохранено {}", toSave.size());
        return toSave.size();
    }

    private Set<String> getDevelopers() {

        return employeeRepo.findBySelectableTrue()
                .stream()
                .map(Employee::getFullName)
                .filter(StringUtils::hasText)
                .collect(Collectors.toSet());
    }

    private StatusSets resolveStatusIds(TransitionExportRequest req) {
        List<String> fromNames = Optional.ofNullable(req.getFromStatuses()).orElse(List.of());
        List<String> toNames = Optional.ofNullable(req.getToStatuses()).orElse(List.of());

        Map<String, Long> nameToId = statusRepo.findByStatusNameIn(union(fromNames, toNames))
                .stream()
                .collect(Collectors.toMap(ProjectJiraStatus::getStatusName, ProjectJiraStatus::getStatusId, (a, b) -> a));

        var allNames = statusRepo.findAll().stream().map(ProjectJiraStatus::getStatusId).collect(Collectors.toSet());
        Set<Long> fromIds;

        if (fromNames.isEmpty()) {
            fromIds = allNames;
        } else {
            fromIds = fromNames.stream()
                    .map(nameToId::get)
                    .filter(Objects::nonNull)
                    .collect(Collectors.toSet());
        }

        Set<Long> toIds;

        if (toNames.isEmpty()) {
            toIds = allNames;
        } else {
            toIds = toNames.stream()
                    .map(nameToId::get)
                    .filter(Objects::nonNull)
                    .collect(Collectors.toSet());
        }

        return new StatusSets(fromIds, toIds);
    }

    private boolean allowByPeopleFilter(String assignee, String developer, Set<String> filter) {
        if (filter == null || filter.isEmpty()) return true;
        if (assignee != null && filter.contains(assignee)) return true;

        if (developer != null && !developer.isBlank()) {
            // developer может быть "A|B|C"
            for (String part : developer.split("\\|")) {
                if (filter.contains(part.trim())) return true;
            }
        }
        return false;
    }

    private String extractSprintNames(Map<String, Object> fields) {
        if (fields == null) return "";
        Object v = fields.get(sprintFieldId);
        if (v == null) return "";

        // В Jira Sprint часто приходит как строка вида "...name=SPRINT_1,..."
        // или как массив таких строк.
        if (v instanceof String s) {
            return tryExtractSprintNameFromString(s);
        }
        if (v instanceof List<?> list) {
            List<String> names = new ArrayList<>();
            for (Object o : list) {
                if (o == null) continue;
                names.add(tryExtractSprintNameFromString(o.toString()));
            }
            return String.join("|", names);
        }
        return v.toString();
    }

    private String tryExtractSprintNameFromString(String s) {
        // name=... до запятой или ]
        int idx = s.indexOf("name=");
        if (idx < 0) return s;
        String sub = s.substring(idx + 5);
        int end = sub.indexOf(',');
        if (end < 0) end = sub.indexOf(']');
        if (end < 0) return sub;
        return sub.substring(0, end);
    }

    private List<String> union(List<String> a, List<String> b) {
        List<String> r = new ArrayList<>();
        r.addAll(a);
        r.addAll(b);
        return r;
    }

    private JiraIssueResponse getIssue(JiraFeignClient jiraClient, String key, String fieldsParam) throws Exception {
        int pop = 0;
        while (pop < 10) {
            try {
                return jiraClient.getIssue(key, "changelog", fieldsParam);
            } catch (Exception e) {
                log.info("Попытка {} отправить запрос getIssue неудалась {}", pop + 1, e.getMessage());
                pop++;
                log.info("Пробую повторно отправить getIssue");
            }
        }
        throw new Exception("Кончились попытки на запросы getIssue, запустите задачу заново");
    }

    private SprintInfo pickSprintForTransition(List<SprintInfo> sprints, OffsetDateTime transitionDate) {
        if (sprints == null || sprints.isEmpty() || transitionDate == null) return null;

        // 1) Попадание в интервал
        for (SprintInfo sp : sprints) {
            OffsetDateTime start = sp.startDate();
            OffsetDateTime finish = sp.completeDate() != null ? sp.completeDate() : sp.endDate();
            if (start != null && finish != null) {
                if (!transitionDate.isBefore(start) && !transitionDate.isAfter(finish)) {
                    return sp;
                }
            }
        }

        // 2) Последний спринт, который закончился до перехода
        return sprints.stream()
                .map(sp -> {
                    OffsetDateTime finish = sp.completeDate() != null ? sp.completeDate() : sp.endDate();
                    return new AbstractMap.SimpleEntry<>(sp, finish);
                })
                .filter(e -> e.getValue() != null && !e.getValue().isAfter(transitionDate))
                .max(Comparator.comparing(AbstractMap.SimpleEntry::getValue))
                .map(AbstractMap.SimpleEntry::getKey)
                .orElseGet(() ->
                        // 3) fallback: самый “поздний” по startDate, иначе первый
                        sprints.stream()
                                .filter(sp -> sp.startDate() != null)
                                .max(Comparator.comparing(SprintInfo::startDate))
                                .orElse(sprints.get(0))
                );
    }
}

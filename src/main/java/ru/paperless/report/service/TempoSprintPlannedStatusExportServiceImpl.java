package ru.paperless.report.service;


import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import ru.paperless.report.client.JiraFeignClient;
import ru.paperless.report.client.dto.request.TempoPlannedStatusExportRequest;
import ru.paperless.report.client.dto.response.JiraIssueResponse;

import ru.paperless.report.dto.TempoAllocationDto;
import ru.paperless.report.entity.Employee;
import ru.paperless.report.entity.JiraSprintTempoPlannedStatus;
import ru.paperless.report.dto.SprintInfo;
import ru.paperless.report.repository.EmployeeRepository;
import ru.paperless.report.repository.JiraSprintTempoPlannedStatusRepository;
import ru.paperless.report.repository.ProjectJiraSprintRepository;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class TempoSprintPlannedStatusExportServiceImpl implements TempoSprintPlannedStatusExportService {

    private final JiraFeignClient jiraClient;

    private final EmployeeRepository employeeRepo;
    private final ProjectJiraSprintRepository sprintRepo;
    private final JiraSprintTempoPlannedStatusRepository plannedRepo;

    private final GeneralMethodsService generalMethodsService;

    @org.springframework.beans.factory.annotation.Value("${jira.project-key}")
    private String projectKey;
    @org.springframework.beans.factory.annotation.Value("${jira.sprintFieldId}")
    private String sprintFieldId;
    @org.springframework.beans.factory.annotation.Value("#{'${jira.tempo-planned.excluded-summary-phrases:дежурство;передача версии}'.split(';')}")
    private List<String> excludedSummaryPhrasesConfig;

    private static final DateTimeFormatter ISO_DATE = DateTimeFormatter.ISO_LOCAL_DATE;

    /**
     * Рекорд “сырых” данных из Tempo allocation:
     * сотрудник / задача / название задачи / дата планирования / секунды / assigneeKey (jira user key)
     */
    private record PlannedRec(String employee, String assigneeKey, String issueKey, String issueSummary, LocalDate plannedDate,
                              long plannedSeconds) {
    }

    @Override
    @Transactional
    public int exportTempoPlannedWithSprintStatuses(TempoPlannedStatusExportRequest req) throws Exception {

        LocalDate from = req != null ? req.getFrom() : null;
        LocalDate to = req != null ? req.getTo() : null;

        if (from == null || to == null) {
            throw new IllegalArgumentException("from/to обязательны");
        }
        if (to.isBefore(from)) {
            throw new IllegalArgumentException("to < from");
        }

        List<String> excludedSummaryPhrases = normalizeExcludedSummaryPhrases();

        // 1) selectable employees with jiraUserKey
        List<Employee> employees = employeeRepo.findBySelectableTrue();
        Map<String, Employee> byAssigneeKey = employees.stream()
                .filter(e -> StringUtils.hasText(e.getJiraUserKey()))
                .collect(Collectors.toMap(
                        e -> e.getJiraUserKey().trim(),
                        e -> e,
                        (a, b) -> a
                ));

        if (byAssigneeKey.isEmpty()) {
            log.info("Нет selectable сотрудников с заполненным jiraUserKey — нечего выгружать.");
            return 0;
        }

        List<String> assigneeKeys = new ArrayList<>(byAssigneeKey.keySet());

        // 2) load Tempo allocations
        List<TempoAllocationDto> allocations;
        try {
            allocations = Optional.ofNullable(jiraClient.getAllocations(
                    assigneeKeys,
                    "user",                // как в документации
                    from.format(ISO_DATE),
                    to.format(ISO_DATE)
            )).orElse(List.of());
        } catch (Exception e) {
            throw new Exception("Tempo allocation запрос упал: " + e.getMessage(), e);
        }

        if (allocations.isEmpty()) {
            log.info("Tempo allocation вернул пусто в диапазоне {}..{}", from, to);
            return 0;
        }

        // 3) build raw planned records (employee/issue/date/seconds)
        List<PlannedRec> planned = allocations.stream()
                .filter(a -> a.getPlanItem().getKey().toUpperCase().contains(projectKey) )
                .filter(a -> !containsExcludedSummaryPhrase(a, excludedSummaryPhrases))
                .map(a -> toPlannedRec(a, byAssigneeKey))
                .filter(Objects::nonNull)
                .toList();

        if (planned.isEmpty()) {
            log.info("После фильтрации по selectable сотрудникам не осталось записей Tempo.");
            return 0;
        }

        // 4) load sprints from DB (и их даты)
        // В твоих сущностях, судя по прошлому коду, у тебя спринты хранятся в ProjectJiraSprintRepository
        // а парсишь даты через GeneralMethodsService.extractSprintsDetailed(...) из Jira полей.
        // Здесь предполагаю: в БД у тебя есть sprintId + sprintName + start/end (если нет — скажи, сделаем под твою таблицу)
        List<SprintInfo> sprints = sprintRepo.findAllByOrderBySprintIdAsc().stream()
                .map(sp -> new SprintInfo(
                        sp.getSprintId(),
                        sp.getSprintName(),
                        generalMethodsService.toOffsetStartOfDay(sp.getStartDate()),   // OffsetDateTime
                        generalMethodsService.toOffsetStartOfDay(sp.getEndDate()),     // OffsetDateTime
                        null,
                        sp.getSprintState()
                ))
                .filter(sp -> sp.startDate() != null && sp.endDate() != null)
                .filter(sp -> sp.startDate().isAfter(generalMethodsService.toOffsetStartOfDay(req.getFrom())))
                .filter(sp -> sp.endDate().isBefore(generalMethodsService.toOffsetStartOfDay(req.getTo())))
                .toList();

        if (sprints.isEmpty()) {
            log.info("Нет спринтов с датами в БД — нечего маппить Tempo на спринты.");
            return 0;
        }

        // 5) To reduce Jira calls: cache full issue (changelog) by issueKey
        Map<String, JiraIssueResponse> issueCache = new HashMap<>();

        List<JiraSprintTempoPlannedStatus> toSave = new ArrayList<>();
        log.info("Количество спринтов {}", sprints.size());

        for (SprintInfo sp : sprints) {
            log.info("Обрабатывается спринт {}", sp.name());
            LocalDate sprintStart = sp.startDate().toLocalDate();
            LocalDate sprintEnd = sp.endDate().toLocalDate();

            // records within sprint range
            List<PlannedRec> inSprint = planned.stream()
                    .filter(r -> !r.plannedDate().isBefore(sprintStart) && !r.plannedDate().isAfter(sprintEnd))
                    .toList();

            if (inSprint.isEmpty()) continue;

            // Aggregate planned seconds by (assigneeKey + issueKey) within the sprint
            record K(String assigneeKey, String issueKey) {
            }
            Map<K, Long> aggregated = new HashMap<>();
            Map<K, String> employeeName = new HashMap<>();
            Map<K, String> issueSummary = new HashMap<>();

            for (PlannedRec r : inSprint) {
                K k = new K(r.assigneeKey(), r.issueKey());
                aggregated.merge(k, r.plannedSeconds(), Long::sum);
                employeeName.putIfAbsent(k, r.employee());
                issueSummary.putIfAbsent(k, r.issueSummary());
            }

            for (Map.Entry<K, Long> e : aggregated.entrySet()) {
                K k = e.getKey();
                long plannedSeconds = e.getValue();

                String issueKey = k.issueKey();

                JiraIssueResponse full = issueCache.computeIfAbsent(issueKey, ik -> {
                    try {
                        // нужен changelog + status (status обычно в стандартных полях)
                        return jiraClient.getIssue(ik, "changelog", "status,"+sprintFieldId);
                    } catch (Exception ex) {
                        log.warn("Не смогли получить issue {}: {}", ik, ex.getMessage());
                        return null;
                    }
                });

                if (full == null) continue;

                // status at sprint start/end
                String stStart = resolveStatusAtStart(full, sp.startDate());
                String stEnd = resolveStatusAtEnd(full, sp.endDate());

                JiraSprintTempoPlannedStatus row = JiraSprintTempoPlannedStatus.builder()
                        .projectKey(projectKey)
                        .sprintId(sp.id())
                        .sprintName(sp.name())
                        .issueKey(issueKey)
                        .issueSummary(issueSummary.get(k))
                        .employee(employeeName.getOrDefault(k, ""))
                        .assigneeKey(k.assigneeKey())
                        .plannedSeconds(plannedSeconds/ 3600)
                        .statusAtSprintStart(stStart)
                        .statusAtSprintEnd(stEnd)
                        .build();

                toSave.add(row);
            }
        }

        if (toSave.isEmpty()) {
            log.info("Нечего сохранять: после маппинга Tempo -> Sprint -> Jira статусы список пуст.");
            return 0;
        }

        plannedRepo.saveAll(toSave);
        log.info("Сохранено {}", toSave.size());
        return toSave.size();
    }

    private PlannedRec toPlannedRec(TempoAllocationDto a, Map<String, Employee> byAssigneeKey) {
        if (a == null) return null;
        if (a.getAssignee() == null) return null;
        if (a.getPlanItem() == null || !StringUtils.hasText(a.getPlanItem().getKey())) return null;

        String assigneeKey = null;

        // В allocation ответе assignee = объект, где есть userKey
        if (a.getAssignee() != null && StringUtils.hasText(a.getAssignee().getKey())) {
            assigneeKey = a.getAssignee().getKey().trim();
        }

        if (!StringUtils.hasText(assigneeKey)) return null;

        Employee emp = byAssigneeKey.get(assigneeKey);
        if (emp == null) {
            // не selectable / нет в таблице
            return null;
        }

        String issueKey = a.getPlanItem().getKey().trim();

        LocalDate plannedDate;
        try {
            plannedDate = LocalDate.parse(a.getStart(), ISO_DATE);
        } catch (Exception ignored) {
            return null;
        }

        long seconds = 0;
        if (a.getSeconds() != null) seconds = a.getSeconds();
        else if (a.getSecondsPerDay() != null) seconds = a.getSecondsPerDay();

        if (seconds <= 0) return null;

        return new PlannedRec(
                emp.getFullName(),
                assigneeKey,
                issueKey,
                a.getPlanItem().getSummary(),
                plannedDate,
                seconds
        );
    }

    private List<String> normalizeExcludedSummaryPhrases() {
        return Optional.ofNullable(excludedSummaryPhrasesConfig)
                .orElse(List.of())
                .stream()
                .filter(StringUtils::hasText)
                .map(String::trim)
                .map(String::toLowerCase)
                .distinct()
                .toList();
    }

    private boolean containsExcludedSummaryPhrase(TempoAllocationDto allocation, List<String> excludedSummaryPhrases) {
        String summary = Optional.ofNullable(allocation)
                .map(TempoAllocationDto::getPlanItem)
                .map(TempoAllocationDto.PlanItem::getSummary)
                .filter(StringUtils::hasText)
                .map(String::trim)
                .map(String::toLowerCase)
                .orElse("");

        if (!StringUtils.hasText(summary)) {
            return false;
        }

        return excludedSummaryPhrases.stream().anyMatch(summary::contains);
    }

    /**
     * Статус задачи на момент "at".
     * Берём все переходы по полю "status" из changelog и находим последний <= at.
     * Если переходов нет — берём текущий статус из fields.status.name (если есть).
     */
    private String resolveStatusAtStart(JiraIssueResponse full, OffsetDateTime at) {
        if (full == null || at == null) return null;

        List<JiraIssueResponse.History> histories =
                Optional.ofNullable(full.getChangelog())
                        .map(JiraIssueResponse.Changelog::getHistories)
                        .orElse(List.of());

        // сортируем по created
        List<JiraIssueResponse.History> sorted = histories.stream()
                .sorted(Comparator.comparing(h -> generalMethodsService.parseOffsetDateTimeSafe(h.getCreated()),
                        Comparator.nullsLast(Comparator.naturalOrder())))
                .toList();

        String lastStatus = "Открыта (Open)";

        for (JiraIssueResponse.History h : sorted) {
            OffsetDateTime created = generalMethodsService.parseOffsetDateTimeSafe(h.getCreated());
            if (created == null) continue;
            if (created.isAfter(at)) break;

            if (h.getItems() == null) continue;

            for (JiraIssueResponse.Item it : h.getItems()) {
                if (!StringUtils.hasText(it.getField())) continue;
                if (!"status".equalsIgnoreCase(it.getField())) continue;

                // toString обычно содержит название статуса
                if (StringUtils.hasText(it.getToString())) {
                    return it.getToString().trim();
                }
            }
        }
        return lastStatus;
    }


    private String resolveStatusAtEnd(JiraIssueResponse full, OffsetDateTime at) {
        if (full == null || at == null) return null;

        List<JiraIssueResponse.History> histories =
                Optional.ofNullable(full.getChangelog())
                        .map(JiraIssueResponse.Changelog::getHistories)
                        .orElse(List.of());

        // сортируем по created
        List<JiraIssueResponse.History> sorted = histories.stream()
                .sorted(Comparator.comparing(h -> generalMethodsService.parseOffsetDateTimeSafe(h.getCreated()),
                        Comparator.nullsLast(Comparator.naturalOrder())))
                .toList();

        String lastStatus = "Открыта (Open)";

        for (JiraIssueResponse.History h : sorted) {
            OffsetDateTime created = generalMethodsService.parseOffsetDateTimeSafe(h.getCreated());
            if (created == null) continue;
            if (created.isAfter(at)) break;

            if (h.getItems() == null) continue;

            for (JiraIssueResponse.Item it : h.getItems()) {
                if (!StringUtils.hasText(it.getField())) continue;
                if (!"status".equalsIgnoreCase(it.getField())) continue;

                // toString обычно содержит название статуса
                if (StringUtils.hasText(it.getToString())) {
                    lastStatus = it.getToString().trim();
                }
            }
        }

        if (StringUtils.hasText(lastStatus)) return lastStatus;

        // fallback: текущий статус из fields
        try {
            Object statusObj = full.getFields() != null ? full.getFields().get("status") : null;
            if (statusObj instanceof Map<?, ?> m) {
                Object name = m.get("name");
                if (name != null) return name.toString();
            }
        } catch (Exception ignored) {
        }

        return null;
    }
}

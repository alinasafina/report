package ru.paperless.report.service;

import feign.FeignException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import ru.paperless.report.client.JiraFeignClient;
import ru.paperless.report.client.dto.response.JiraBoardsResponse;
import ru.paperless.report.client.dto.response.JiraUserResponse;
import ru.paperless.report.dto.JiraProjectStatusesItem;
import ru.paperless.report.client.dto.response.JiraSprintsResponse;
import ru.paperless.report.config.JiraProperties;
import ru.paperless.report.entity.Employee;
import ru.paperless.report.entity.ProjectJiraSprint;
import ru.paperless.report.entity.ProjectJiraStatus;
import ru.paperless.report.repository.EmployeeRepository;
import ru.paperless.report.repository.ProjectJiraSprintRepository;
import ru.paperless.report.repository.ProjectJiraStatusRepository;

import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class JiraMainFieldsExportServiceImpl implements JiraMainFieldsExportService {
    private final ProjectJiraStatusRepository projectJiraStatusRepository;
    private final ProjectJiraSprintRepository projectJiraSprintRepository;
    private final EmployeeRepository employeeRepository;
    private final JiraFeignClient jiraClient;
    private final JiraProperties props;

    @Override
    @Transactional
    public void saveJiraStatuses() {
        // (аналог проверки auth via /myself)
        jiraClient.myself(); // если токен плохой — тут прилетит 401/403

        List<JiraProjectStatusesItem> items = jiraClient.projectStatuses(props.getProjectKey());
        if (items == null) {
            System.out.println("No jira statuses found");
        }

        // В Jira статусы могут повторяться по issueType — соберём уникальные по statusId
        Map<Long, ProjectJiraStatus> unique = new LinkedHashMap<>();

        for (JiraProjectStatusesItem it : items) {
            if (it.statuses == null) continue;

            for (JiraProjectStatusesItem.JiraStatus st : it.statuses) {
                if (st == null || st.id == null || st.id.isBlank()) continue;

                Long id;
                try {
                    id = Long.parseLong(st.id);
                } catch (NumberFormatException e) {
                    continue; // на всякий случай
                }

                ProjectJiraStatus entity = getProjectJiraStatus(st, id);

                unique.put(id, entity);
            }
        }

        var filteredStatuses = unique.values().stream()
                .filter(s -> !projectJiraStatusRepository.existsByStatusId(s.getStatusId()))
                .collect(Collectors.toSet());

        projectJiraStatusRepository.saveAll(filteredStatuses);

        System.out.println("Сохраненно: " + filteredStatuses.size());
    }

    @Override
    public String saveJiraSprints(LocalDate dateFrom, LocalDate dateTo, String sprintStatesCsv) {
        JiraBoardsResponse boards = jiraClient.boards(props.getProjectKey());
        if (boards.getValues() == null || boards.getValues().isEmpty()) {
            System.out.println("No jira sprints found");
        }

        // dedup по sprintId
        Map<Long, ProjectJiraSprint> acc = new HashMap<>();

        for (JiraBoardsResponse.Board b : boards.getValues()) {
            long boardId = b.getId();
            String boardName = b.getName();

            int startAt = 0;
            while (true) {
                JiraSprintsResponse page = null;
                try {
                    page = jiraClient.sprints(boardId, sprintStatesCsv, startAt, 50);

                    var values = page.getValues();
                    if (values == null || values.isEmpty()) break;

                    for (JiraSprintsResponse.Sprint s : values) {
                        LocalDate start = toYmd(s.getStartDate());
                        LocalDate end = toYmd(s.getEndDate());
                        LocalDate complete = toYmd(s.getCompleteDate());

                        // endDate может быть null => подставим completeDate
                        LocalDate effectiveEnd = (end != null) ? end : complete;

                        if (start == null || effectiveEnd == null) {
                            continue;
                        }

                        // пересечение: start <= dateTo && end >= dateFrom
                        if (start.isAfter(dateTo) || effectiveEnd.isBefore(dateFrom)) {
                            continue;
                        }

                        acc.putIfAbsent(
                                s.getId(),
                                ProjectJiraSprint.builder()
                                        .sprintId(s.getId())
                                        .sprintName(s.getName())
                                        .sprintState(s.getState())
                                        .startDate(start)
                                        .endDate(end)               // оригинальный endDate (может быть null)
                                        .completeDate(complete)
                                        .boardId(boardId)
                                        .boardName(boardName)
                                        .projectKey(props.getProjectKey())
                                        .build()
                        );
                    }

                    startAt += 50;
                } catch (FeignException e) {
                    log.info("Доска {}}: {}", b.getName(), e.getMessage());
                    log.info("Для доски CI это норма");
                    break;
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            }
        }

        var result = acc.values().stream()
                .filter(s -> !projectJiraSprintRepository.existsBySprintId(s.getSprintId()))
                .collect(Collectors.toSet());

        projectJiraSprintRepository.saveAll(result);

        String sprintIds = acc.keySet().stream()
                .map(String::valueOf)
                .collect(Collectors.joining(" "));

        System.out.println("Сохранено: " + result.size());

        return sprintIds;
    }

    @Override
    public int saveJiraUserKeys() {
        List<Employee> employees = employeeRepository.findBySelectableTrue();
        int updated = 0;

        for (Employee e : employees) {
            if (StringUtils.hasText(e.getJiraUserKey())) continue;
            if (!StringUtils.hasText(e.getFullName())) continue;

            String query = e.getFullName().trim();

            List<JiraUserResponse> users;
            try {
                users = jiraClient.searchUsers(query);
            } catch (Exception ex) {
                log.warn("Jira user search failed for '{}': {}", query, ex.getMessage());
                continue;
            }

            Optional<JiraUserResponse> matched = users.stream()
                    .filter(u -> query.equalsIgnoreCase(u.getDisplayName()))
                    .findFirst();

            if (matched.isEmpty()) {
                log.warn("Не найден Jira user для '{}'", query);
                continue;
            }

            JiraUserResponse user = matched.get();
            e.setJiraUserKey(
                    StringUtils.hasText(user.getKey())
                            ? user.getKey().trim()
                            : user.getName()
            );

            employeeRepository.save(e);
            updated++;

            log.info("employee='{}' → jiraUserKey='{}'",
                    e.getFullName(), e.getJiraUserKey());
        }

        log.info("jiraUserKey обновлено у {}", updated);
        return updated;
    }

    private static ProjectJiraStatus getProjectJiraStatus(JiraProjectStatusesItem.JiraStatus st, Long id) {
        ProjectJiraStatus entity = new ProjectJiraStatus();
        entity.setStatusId(id);
        entity.setStatusName(st.name);

        String category = null;
        if (st.statusCategory != null) {
            category = (st.statusCategory.name != null && !st.statusCategory.name.isBlank())
                    ? st.statusCategory.name
                    : st.statusCategory.key;
        }
        entity.setStatusCategory(category);
        return entity;
    }

    private LocalDate toYmd(String jiraIso) {
        if (jiraIso == null || jiraIso.isBlank()) return null;
        // берём до 'T'
        int idx = jiraIso.indexOf('T');
        String ymd = (idx > 0) ? jiraIso.substring(0, idx) : jiraIso;
        return LocalDate.parse(ymd);
    }
}

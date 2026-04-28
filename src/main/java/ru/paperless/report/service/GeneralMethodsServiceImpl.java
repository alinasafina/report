package ru.paperless.report.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import ru.paperless.report.client.JiraFeignClient;
import ru.paperless.report.client.dto.request.SprintIdsRequest;
import ru.paperless.report.client.dto.request.TransitionExportRequest;
import ru.paperless.report.client.dto.response.JiraWorklogResponse;
import ru.paperless.report.dto.JiraFieldDto;
import ru.paperless.report.dto.SprintInfo;
import ru.paperless.report.entity.ProjectJiraSprint;
import ru.paperless.report.repository.ProjectJiraSprintRepository;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class GeneralMethodsServiceImpl implements GeneralMethodsService {

    private final JiraFeignClient jiraClient;
    private final ProjectJiraSprintRepository sprintRepo;

    @org.springframework.beans.factory.annotation.Value("${jira.sprintFieldId}")
    private String sprintFieldId;
    @org.springframework.beans.factory.annotation.Value("${jira.developerFieldId}")
    private String developerFieldId;

    private static final DateTimeFormatter JIRA_WITH_MILLIS_NO_COLON = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSZ");
    private static final DateTimeFormatter ISO_DATE = DateTimeFormatter.ISO_LOCAL_DATE;

    @Override
    public String extractDeveloperValue(Map<String, Object> fields) {
        if (fields == null) return "";
        Object dev = fields.get(developerFieldId);
        if (dev == null) return "";

        try {
            if (dev instanceof Map<?, ?> m) {
                Object dn = m.get("displayName");
                if (dn != null) return dn.toString();
                Object name = m.get("name");
                if (name != null) return name.toString();
                Object email = m.get("emailAddress");
                if (email != null) return email.toString();
                return "";
            }
            if (dev instanceof List<?> list) {
                List<String> names = new ArrayList<>();
                for (Object o : list) {
                    if (o instanceof Map<?, ?> m) {
                        Object dn = m.get("displayName");
                        if (dn != null) names.add(dn.toString());
                    }
                }
                return String.join("|", names);
            }
            return dev.toString();
        } catch (Exception e) {
            return "";
        }
    }


    @Override
    public OffsetDateTime parseOffsetDateTimeSafe(String iso) {
        if (!StringUtils.hasText(iso)) return null;

        // 2) Jira: 2026-01-21T12:36:16.000+0700
        try {
            return OffsetDateTime.parse(iso, JIRA_WITH_MILLIS_NO_COLON);
        } catch (DateTimeParseException ignored) {
            return null;
        }
    }

    @Override
    public List<SprintInfo> extractSprintsDetailed(Map<String, Object> fields) {
        if (fields == null) return List.of();
        Object v = fields.get(sprintFieldId);
        if (v == null) return List.of();

        List<String> raw = new ArrayList<>();
        if (v instanceof String s) raw.add(s);
        else if (v instanceof List<?> list) {
            for (Object o : list) if (o != null) raw.add(o.toString());
        }

        List<SprintInfo> result = new ArrayList<>();
        for (String s : raw) {
            Long id = parseLongSafe(extractToken(s, "id="));
            String name = extractToken(s, "name=");
            String state = extractToken(s, "state=");

            OffsetDateTime start = parseSprintDate(extractToken(s, "startDate="));
            OffsetDateTime end = parseSprintDate(extractToken(s, "endDate="));
            OffsetDateTime complete = parseSprintDate(extractToken(s, "completeDate="));

            if (id != null || (name != null && !name.isBlank())) {
                result.add(new SprintInfo(id, name, start, end, complete, state));
            }
        }
        return result;
    }

    @Override
    public OffsetDateTime parseSprintDate(String iso) {
        if (!StringUtils.hasText(iso)) return null;
        try {
            return OffsetDateTime.parse(iso); // покрывает ...+07:00
        } catch (Exception e) {
            // на всякий случай: если вдруг пришло +0700
            try {
                return OffsetDateTime.parse(iso, JIRA_WITH_MILLIS_NO_COLON);
            } catch (Exception ignored) {
                return null;
            }
        }
    }

    @Override
    @SuppressWarnings("unchecked")
    public String extractAssigneeDisplayName(Map<String, Object> fields) {
        if (fields == null) return "Unassigned";
        Object assignee = fields.get("assignee");
        if (!(assignee instanceof Map<?, ?> m)) return "Unassigned";
        Object displayName = m.get("displayName");
        if (displayName != null) return displayName.toString();
        Object name = m.get("name");
        if (name != null) return name.toString();
        Object email = m.get("emailAddress");
        if (email != null) return email.toString();
        return "Unassigned";
    }

    @Override
    public Long parseLongSafe(String s) {
        try {
            if (!StringUtils.hasText(s)) return null;
            return Long.parseLong(s.trim());
        } catch (Exception e) {
            return null;
        }
    }

    @Override
    public Double parseEstimateHours(String raw) {
        try {
            String s = raw.trim().toLowerCase(Locale.ROOT).replace("h", "");
            if (!StringUtils.hasText(s)) return null;
            return Double.parseDouble(s.replace(",", "."));
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Собираем людей из задачи: assignee + developer(ы).
     * developer может быть строкой "A|B|C".
     */
    @Override
    public Set<String> extractIssuePeople(String assignee, String developer) {
        Set<String> result = new HashSet<>();
        if (StringUtils.hasText(assignee)) {
            result.add(assignee.trim());
        }
        if (StringUtils.hasText(developer)) {
            for (String part : developer.split("\\|")) {
                String p = part.trim();
                if (StringUtils.hasText(p)) result.add(p);
            }
        }
        return result;
    }

    @Override
    public SprintInfo pickMainSprint(List<SprintInfo> sprints) {
        if (sprints == null || sprints.isEmpty()) return null;
        return sprints.stream().filter(sp -> "ACTIVE".equalsIgnoreCase(sp.state())).findFirst()
                .orElseGet(() ->
                        sprints.stream()
                                .filter(sp -> sp.startDate() != null)
                                .max(Comparator.comparing(SprintInfo::startDate))
                                .orElse(sprints.get(0))
                );
    }

    @Override
    public String extractAuthorDisplayName(JiraWorklogResponse.Author a) {
        if (a == null) return null;
        if (StringUtils.hasText(a.getDisplayName())) return a.getDisplayName();
        if (StringUtils.hasText(a.getName())) return a.getName();
        if (StringUtils.hasText(a.getEmailAddress())) return a.getEmailAddress();
        return null;
    }

    @Override
    public List<Long> resolveSprintIds(SprintIdsRequest req) {
        if (req != null && StringUtils.hasText(req.getSprintIds())) {
            return Arrays.stream(req.getSprintIds().trim().split("\\s+"))
                    .filter(StringUtils::hasText)
                    .map(this::parseLongSafe)
                    .filter(Objects::nonNull)
                    .toList();
        }
        return sprintRepo.findAllByOrderBySprintIdAsc().stream()
                .map(ProjectJiraSprint::getSprintId)
                .filter(Objects::nonNull)
                .toList();
    }

    @Override
    public List<Long> resolveSprintIds(TransitionExportRequest req) {
        if (StringUtils.hasText(req.getSprintIds())) {
            return Arrays.stream(req.getSprintIds().trim().split("\\s+"))
                    .filter(StringUtils::hasText)
                    .map(this::parseLongSafe)
                    .filter(Objects::nonNull)
                    .toList();
        }
        // иначе из таблицы
        return sprintRepo.findAllByOrderBySprintIdAsc()
                .stream()
                .map(ProjectJiraSprint::getSprintId)
                .filter(Objects::nonNull)
                .toList();
    }

    @Override
    public List<JiraFieldDto> getJiraFields() {
        synchronized (this) {
            return jiraClient.getFields();
        }
    }

    @Override
    public OffsetDateTime toOffsetStartOfDay(LocalDate d) {
        java.time.ZoneId zone = java.time.ZoneId.systemDefault();
        java.time.ZoneOffset offset = zone.getRules().getOffset(java.time.Instant.now());
        if (d == null) return null;
        // можно заменить на LocalTime.MAX для endDate — зависит от того, как хочешь трактовать LocalDate
        return d.atStartOfDay(zone).toOffsetDateTime();
    }

    @Override
    public LocalDate safeDate(String iso) {
        try {
            if (!StringUtils.hasText(iso)) return null;
            return LocalDate.parse(iso, ISO_DATE);
        } catch (Exception ignored) {
            return null;
        }
    }

    @Override
    public List<ProjectJiraSprint> getSprintsBySprintIds(SprintIdsRequest req) {
        List<Long> ids = Arrays.stream(req.getSprintIds().trim().split("\\s+"))
                    .filter(StringUtils::hasText)
                    .map(this::parseLongSafe)
                    .filter(Objects::nonNull)
                    .toList();

        return sprintRepo.findBySprintIds(ids);
    }
    private String extractToken(String s, String key) {
        int idx = s.indexOf(key);
        if (idx < 0) return null;
        String sub = s.substring(idx + key.length());
        int end = sub.indexOf(',');
        if (end < 0) end = sub.indexOf(']');
        if (end < 0) return sub.trim();
        return sub.substring(0, end).trim();
    }
}

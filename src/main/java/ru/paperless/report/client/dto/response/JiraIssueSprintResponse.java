package ru.paperless.report.client.dto.response;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.util.StringUtils;
import ru.paperless.report.service.GeneralMethodsService;
import ru.paperless.report.service.GeneralMethodsServiceImpl;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class JiraIssueSprintResponse {
    private String key;
    private Map<String, List<Sprint>> fields;

    private static final DateTimeFormatter JIRA_WITH_MILLIS_COLON = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSXXX");

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    @AllArgsConstructor
    @NoArgsConstructor
    public static class Sprint {

        private Long id;
        private String name;
        private OffsetDateTime startODate;
        private OffsetDateTime endDate;

        @JsonCreator(mode = JsonCreator.Mode.DELEGATING)
        public static Sprint fromString(String raw) {
            if (raw == null || raw.isBlank()) {
                return new Sprint(null, null, null, null);
            }

            // нормализуем (иногда ответ приходит с переносами строк)
            String s = raw.replace("\r", "").replace("\n", "");

            return new Sprint(
                    Long.parseLong(extract(s, "id")),
                    extract(s, "name"),
                    parseOffsetDateTimeSafe(extract(s, "startDate")),
                    parseOffsetDateTimeSafe(extract(s, "endDate"))
            );
        }

        private static String extract(String text, String key) {
            Matcher m = Pattern
                    .compile("\\b" + Pattern.quote(key) + "=([^,]+)")
                    .matcher(text);
            return m.find() ? m.group(1).trim() : null;
        }

        private static OffsetDateTime parseOffsetDateTimeSafe(String iso) {
            try {
                return OffsetDateTime.parse(iso, JIRA_WITH_MILLIS_COLON);
            } catch (DateTimeParseException ignored) {
                return null;
            }
        }
    }
}
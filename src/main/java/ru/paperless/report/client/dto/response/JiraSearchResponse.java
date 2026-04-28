package ru.paperless.report.client.dto.response;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

import java.util.List;
import java.util.Map;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class JiraSearchResponse {
    private Integer startAt;
    private Integer maxResults;
    private Integer total;
    private List<Issue> issues;

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Issue {
        private String key;
        private Map<String, Object> fields;
    }
}
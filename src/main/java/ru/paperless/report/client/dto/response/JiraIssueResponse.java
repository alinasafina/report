package ru.paperless.report.client.dto.response;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

import java.util.List;
import java.util.Map;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class JiraIssueResponse {
    private String key;
    private Map<String, Object> fields;
    private Changelog changelog;

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Changelog {
        private List<History> histories;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class History {
        private String created; // ISO string
        private List<Item> items;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Item {
        private String field;
        private String from;       // status id as string
        private String fromString; // status name
        private String to;         // status id as string
        private String toString;   // status name
    }
}
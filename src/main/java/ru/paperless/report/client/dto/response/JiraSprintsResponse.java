package ru.paperless.report.client.dto.response;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

import java.util.List;

@Data
public class JiraSprintsResponse {
    private List<Sprint> values;

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Sprint {
        private Long id;
        private String name;
        private String state;

        // ISO string like 2025-10-15T12:00:00.000+0300
        private String startDate;
        private String endDate;
        private String completeDate;
    }
}
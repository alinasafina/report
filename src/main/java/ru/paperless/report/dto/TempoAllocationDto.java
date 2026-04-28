package ru.paperless.report.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class TempoAllocationDto {

    private Long id;

    private Assignee assignee;
    private PlanItem planItem;

    private Double commitment;
    private Long secondsPerDay;
    private Boolean includeNonWorkingDays;

    private String start;     // yyyy-MM-dd
    private String startTime; // "14:00"
    private String end;       // yyyy-MM-dd

    private String description;

    private Long seconds;     // суммарно по записи (в твоих примерах = secondsPerDay)
    private String created;
    private String createdBy;
    private String createdByKey;
    private String updated;
    private String updatedBy;

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Assignee {
        private String key;     // "belkin"
        private String type;    // "user"
        private String userKey; // "belkin" (в вашем ответе так)
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class PlanItem {
        private String key;  // "FDTECH-116"
        private Long id;     // issue id
        private String type; // "ISSUE"
        private String name;
        private String summary;

        private IssueStatus issueStatus;

        @Data
        @JsonIgnoreProperties(ignoreUnknown = true)
        public static class IssueStatus {
            private String key;  // "Backlog" / "Закрыта (Closed)"
            private String type; // "default" / "success"
        }
    }
}
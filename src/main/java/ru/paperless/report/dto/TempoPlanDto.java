package ru.paperless.report.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;


@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class TempoPlanDto {

    // tempo id у вас называется allocationId
    @JsonProperty("allocationId")
    private Long allocationId;

    // user key / username (то, что тебе нужно писать в employee.jira_user_key)
    @JsonProperty("assignee")
    private String assignee;

    @JsonProperty("assigneeType")
    private String assigneeType; // USER

    @JsonProperty("planItemId")
    private Long planItemId;

    @JsonProperty("planItemType")
    private String planItemType; // ISSUE

    // основной день планирования (часто Tempo отдаёт по дням)
    @JsonProperty("day")
    private String day; // yyyy-MM-dd

    // границы плана (могут быть равны day)
    @JsonProperty("planStart")
    private String planStart; // yyyy-MM-dd

    @JsonProperty("planEnd")
    private String planEnd; // yyyy-MM-dd

    // planned seconds иногда double (как у тебя 7800.0)
    @JsonProperty("timePlannedSeconds")
    private Double timePlannedSeconds;

    @JsonProperty("secondsPerDay")
    private Long secondsPerDay;

    @JsonProperty("planItemInfo")
    private PlanItemInfo planItemInfo;

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class PlanItemInfo {
        @JsonProperty("key")
        private String key;

        @JsonProperty("projectKey")
        private String projectKey;

        @JsonProperty("summary")
        private String summary;

        @JsonProperty("isResolved")
        private Boolean isResolved;

        @JsonProperty("issueStatus")
        private IssueStatus issueStatus;

        @Data
        @JsonIgnoreProperties(ignoreUnknown = true)
        public static class IssueStatus {
            @JsonProperty("key")
            private String key;   // "Закрыта (Closed)"
            @JsonProperty("type")
            private String type;  // "success"
        }
    }
}
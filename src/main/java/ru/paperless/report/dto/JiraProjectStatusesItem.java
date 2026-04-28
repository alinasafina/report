package ru.paperless.report.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public class JiraProjectStatusesItem {
    public IssueTypeInfo issueTypeInfo;
    public IssueType issueType;
    public List<JiraStatus> statuses;

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class IssueTypeInfo { public String name; }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class IssueType { public String name; }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class JiraStatus {
        public String id;
        public String name;
        public StatusCategory statusCategory;

        @JsonIgnoreProperties(ignoreUnknown = true)
        public static class StatusCategory {
            public String name;
            public String key;
        }
        @JsonIgnoreProperties(ignoreUnknown = true)
        public static class Scope {
            public String type;
        }
    }
}
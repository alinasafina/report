package ru.paperless.report.client.dto.response;

import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter @Setter
public class JiraWorklogResponse {
    private Integer startAt;
    private Integer maxResults;
    private Integer total;
    private List<Worklog> worklogs;

    @Getter
    @Setter
    public static class Worklog {
        private Author author;
        private Integer timeSpentSeconds; // Jira отдаёт seconds
        private String updated;
        private String started; // ISO
    }

    @Getter @Setter
    public static class Author {
        private String displayName;
        private String name;
        private String emailAddress;
    }
}
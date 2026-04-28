package ru.paperless.report.client.dto.response;

import lombok.Data;

@Data
public class JiraUserResponse {
    private String key;          // <<< это jiraUserKey
    private String name;
    private String displayName;
    private String emailAddress;
}
package ru.paperless.report.client.dto.request;
import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class JiraSearchRequest {
    private String jql;
    private List<String> fields;
    private Integer startAt;
    private Integer maxResults;
}
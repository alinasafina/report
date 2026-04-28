package ru.paperless.report.service;

import ru.paperless.report.client.dto.request.SprintIdsRequest;

public interface JiraEffortExportService {
    int exportEffort(SprintIdsRequest req) throws Exception;
}

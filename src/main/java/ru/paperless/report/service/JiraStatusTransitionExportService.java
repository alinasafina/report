package ru.paperless.report.service;

import ru.paperless.report.client.dto.request.TransitionExportRequest;

public interface JiraStatusTransitionExportService {
    int exportTransitions(TransitionExportRequest req) throws Exception;
}

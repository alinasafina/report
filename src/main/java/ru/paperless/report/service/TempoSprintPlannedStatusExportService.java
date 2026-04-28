package ru.paperless.report.service;

import org.springframework.transaction.annotation.Transactional;
import ru.paperless.report.client.dto.request.SprintIdsRequest;
import ru.paperless.report.client.dto.request.TempoPlannedStatusExportRequest;

public interface TempoSprintPlannedStatusExportService {

    @Transactional
    int exportTempoPlannedWithSprintStatuses(TempoPlannedStatusExportRequest req) throws Exception;
}

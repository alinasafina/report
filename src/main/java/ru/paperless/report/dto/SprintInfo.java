package ru.paperless.report.dto;

import java.time.OffsetDateTime;

public record SprintInfo(
        Long id,
        String name,
        OffsetDateTime startDate,
        OffsetDateTime endDate,
        OffsetDateTime completeDate,
        String state
) {
}
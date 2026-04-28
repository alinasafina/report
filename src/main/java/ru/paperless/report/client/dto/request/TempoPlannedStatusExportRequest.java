package ru.paperless.report.client.dto.request;

import lombok.Data;

import java.time.LocalDate;

@Data
public class TempoPlannedStatusExportRequest {
    private LocalDate from;
    private LocalDate to;
}
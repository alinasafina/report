package ru.paperless.report.service;

import lombok.RequiredArgsConstructor;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import ru.paperless.report.dto.TransitionLeadTimeDetailRow;
import ru.paperless.report.dto.TransitionLeadTimeSummaryRow;
import ru.paperless.report.entity.Employee;
import ru.paperless.report.entity.ProjectJiraStatus;
import ru.paperless.report.repository.EmployeeRepository;
import ru.paperless.report.repository.JiraSprintStatusTransitionRepository;
import ru.paperless.report.repository.ProjectJiraStatusRepository;

import java.io.ByteArrayOutputStream;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ExcelTransitionLeadTimeReportServiceImpl implements ExcelTransitionLeadTimeReportService {

    private static final DateTimeFormatter DT_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
            .withZone(ZoneId.systemDefault());

    private final JiraSprintStatusTransitionRepository transitionRepository;
    private final EmployeeRepository employeeRepository;
    private final ProjectJiraStatusRepository statusRepository;

    @Override
    public byte[] buildXlsx(List<Long> startStatusIds,
                            List<Long> targetStatusIds,
                            String sprintIdsText) {
        Filter filter = prepareFilter(startStatusIds, targetStatusIds, sprintIdsText);

        List<TransitionLeadTimeSummaryRow> summaryRows = transitionRepository.getLeadTimeSummary(
                filter.employees(), filter.useStart(), filter.safeStartStatusIds(),
                filter.useTarget(), filter.safeTargetStatusIds(), filter.useSprints(), filter.safeSprintIds()
        );

        List<TransitionLeadTimeDetailRow> detailRows = transitionRepository.getLeadTimeDetails(
                filter.employees(), filter.useStart(), filter.safeStartStatusIds(),
                filter.useTarget(), filter.safeTargetStatusIds(), filter.useSprints(), filter.safeSprintIds()
        );

        return toXlsxBytes(summaryRows, detailRows, filter);
    }

    private byte[] toXlsxBytes(List<TransitionLeadTimeSummaryRow> summaryRows,
                               List<TransitionLeadTimeDetailRow> detailRows,
                               Filter filter) {
        try (Workbook wb = new XSSFWorkbook();
             ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            Map<Long, String> statusNames = getStatusNamesById(filter.startStatusIds(), filter.targetStatusIds());

            Font boldFont = wb.createFont();
            boldFont.setBold(true);

            CellStyle headerStyle = wb.createCellStyle();
            headerStyle.setFont(boldFont);

            Sheet summarySheet = wb.createSheet("3.1 Спринты до Протестировано");
            int summaryRowNum = writeMeta(summarySheet, filter, statusNames);

            Row summaryHeader = summarySheet.createRow(summaryRowNum++);
            summaryHeader.createCell(0).setCellValue("Сотрудник");
            summaryHeader.createCell(1).setCellValue("Номер задачи");
            summaryHeader.createCell(2).setCellValue("Название задачи");
            summaryHeader.createCell(3).setCellValue("Первый спринт");
            summaryHeader.createCell(4).setCellValue("Последний спринт");
            summaryHeader.createCell(5).setCellValue("Дата первого Open");
            summaryHeader.createCell(6).setCellValue("Дата последнего перехода в Протестировано");
            summaryHeader.createCell(7).setCellValue("Количество переоткрытий");
            summaryHeader.createCell(8).setCellValue("Переоткрыто из Review");
            summaryHeader.createCell(9).setCellValue("Количество спринтов");
            applyHeaderStyle(summaryHeader, headerStyle, 10);

            for (TransitionLeadTimeSummaryRow row : summaryRows) {
                Row x = summarySheet.createRow(summaryRowNum++);
                x.createCell(0).setCellValue(nullSafe(row.getEmployee()));
                x.createCell(1).setCellValue(nullSafe(row.getIssueKey()));
                x.createCell(2).setCellValue(nullSafe(row.getIssueSummary()));
                x.createCell(3).setCellValue(nullSafe(row.getStartSprintName()));
                x.createCell(4).setCellValue(nullSafe(row.getEndSprintName()));
                x.createCell(5).setCellValue(formatDate(row.getStartDate()));
                x.createCell(6).setCellValue(formatDate(row.getEndDate()));
                x.createCell(7).setCellValue(row.getReopenedCount() == null ? 0 : row.getReopenedCount());
                x.createCell(8).setCellValue(row.getReopenedFromReviewCount() == null ? 0 : row.getReopenedFromReviewCount());
                x.createCell(9).setCellValue(row.getSprintCount() == null ? 0 : row.getSprintCount());
            }

            summarySheet.setAutoFilter(new CellRangeAddress(summaryHeader.getRowNum(), summaryHeader.getRowNum(), 0, 9));
            autoSizeColumns(summarySheet, 10);
            summarySheet.setColumnWidth(0, 35 * 256);
            summarySheet.setColumnWidth(2, 60 * 256);

            Sheet detailSheet = wb.createSheet("3.2 Переходы задач");
            int detailRowNum = writeMeta(detailSheet, filter, statusNames);

            Row detailHeader = detailSheet.createRow(detailRowNum++);
            detailHeader.createCell(0).setCellValue("Сотрудник");
            detailHeader.createCell(1).setCellValue("Номер задачи");
            detailHeader.createCell(2).setCellValue("Название задачи");
            detailHeader.createCell(3).setCellValue("Спринт");
            detailHeader.createCell(4).setCellValue("Из статуса");
            detailHeader.createCell(5).setCellValue("В статус");
            detailHeader.createCell(6).setCellValue("Дата перехода");
            applyHeaderStyle(detailHeader, headerStyle, 7);

            for (TransitionLeadTimeDetailRow row : detailRows) {
                Row x = detailSheet.createRow(detailRowNum++);
                x.createCell(0).setCellValue(nullSafe(row.getEmployee()));
                x.createCell(1).setCellValue(nullSafe(row.getIssueKey()));
                x.createCell(2).setCellValue(nullSafe(row.getIssueSummary()));
                x.createCell(3).setCellValue(nullSafe(row.getSprintName()));
                x.createCell(4).setCellValue(nullSafe(row.getFromStatusName()));
                x.createCell(5).setCellValue(nullSafe(row.getToStatusName()));
                x.createCell(6).setCellValue(formatDate(row.getTransitionDate()));
            }

            detailSheet.setAutoFilter(new CellRangeAddress(detailHeader.getRowNum(), detailHeader.getRowNum(), 0, 6));
            autoSizeColumns(detailSheet, 7);
            detailSheet.setColumnWidth(0, 35 * 256);
            detailSheet.setColumnWidth(2, 60 * 256);

            wb.write(baos);
            return baos.toByteArray();
        } catch (Exception e) {
            throw new RuntimeException("Не удалось сформировать XLSX отчет по сроку перехода задач", e);
        }
    }

    private int writeMeta(Sheet sheet, Filter filter, Map<Long, String> statusNames) {
        int rowNum = 0;

        Row m1 = sheet.createRow(rowNum++);
        m1.createCell(0).setCellValue("Стартовый статус");
        m1.createCell(1).setCellValue(formatStatusList(filter.startStatusIds(), statusNames));

        Row m2 = sheet.createRow(rowNum++);
        m2.createCell(0).setCellValue("Конечный статус");
        m2.createCell(1).setCellValue(formatStatusList(filter.targetStatusIds(), statusNames));

        Row m3 = sheet.createRow(rowNum++);
        m3.createCell(0).setCellValue("Сотрудник");
        m3.createCell(1).setCellValue(String.join(", ", filter.employees()));

        return rowNum + 1;
    }

    private Filter prepareFilter(List<Long> startStatusIds,
                                 List<Long> targetStatusIds,
                                 String sprintIdsText) {
        List<String> employees = employeeRepository.findBySelectableTrue().stream()
                .map(Employee::getFullName)
                .filter(StringUtils::hasText)
                .distinct()
                .toList();

        if (employees.isEmpty()) {
            throw new IllegalArgumentException("Список сотрудников для отчета пуст");
        }

        List<Long> startIds = normalizeLongList(startStatusIds);
        List<Long> targetIds = normalizeLongList(targetStatusIds);
        List<Long> sprintIds = parseSprintIds(sprintIdsText);

        boolean useStart = !startIds.isEmpty();
        boolean useTarget = !targetIds.isEmpty();
        boolean useSprints = !sprintIds.isEmpty();

        return new Filter(
                employees,
                useStart, useStart ? startIds : List.of(-1L),
                useTarget, useTarget ? targetIds : List.of(-1L),
                useSprints, useSprints ? sprintIds : List.of(-1L),
                startIds, targetIds
        );
    }

    private List<Long> parseSprintIds(String sprintIdsText) {
        if (!StringUtils.hasText(sprintIdsText)) {
            return List.of();
        }

        return Arrays.stream(sprintIdsText.trim().split("\\s+"))
                .filter(StringUtils::hasText)
                .map(Long::valueOf)
                .distinct()
                .collect(Collectors.toList());
    }

    private List<Long> normalizeLongList(List<Long> input) {
        if (input == null) {
            return List.of();
        }
        return input.stream()
                .filter(Objects::nonNull)
                .distinct()
                .toList();
    }

    private Map<Long, String> getStatusNamesById(List<Long> startStatusIds, List<Long> targetStatusIds) {
        List<Long> statusIds = java.util.stream.Stream.concat(startStatusIds.stream(), targetStatusIds.stream())
                .filter(Objects::nonNull)
                .distinct()
                .toList();

        Map<Long, String> result = new HashMap<>();
        for (ProjectJiraStatus status : statusRepository.findAllById(statusIds)) {
            if (status.getStatusId() != null && StringUtils.hasText(status.getStatusName())) {
                result.put(status.getStatusId(), status.getStatusName());
            }
        }
        return result;
    }

    private String formatStatusList(List<Long> statusIds, Map<Long, String> statusNames) {
        if (statusIds == null || statusIds.isEmpty()) {
            return "ALL";
        }
        return statusIds.stream()
                .map(id -> statusNames.getOrDefault(id, String.valueOf(id)))
                .collect(Collectors.joining(", "));
    }

    private void applyHeaderStyle(Row row, CellStyle headerStyle, int cellsCount) {
        for (int i = 0; i < cellsCount; i++) {
            row.getCell(i).setCellStyle(headerStyle);
        }
    }

    private void autoSizeColumns(Sheet sheet, int columnsCount) {
        for (int i = 0; i < columnsCount; i++) {
            sheet.autoSizeColumn(i);
        }
    }

    private String formatDate(Instant instant) {
        return instant == null ? "" : DT_FMT.format(instant);
    }

    private String nullSafe(String value) {
        return value == null ? "" : value;
    }

    private record Filter(
            List<String> employees,
            boolean useStart, List<Long> safeStartStatusIds,
            boolean useTarget, List<Long> safeTargetStatusIds,
            boolean useSprints, List<Long> safeSprintIds,
            List<Long> startStatusIds,
            List<Long> targetStatusIds
    ) {
    }
}

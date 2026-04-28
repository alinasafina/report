package ru.paperless.report.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.HorizontalAlignment;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import ru.paperless.report.dto.OutOfPlanTaskProjection;
import ru.paperless.report.dto.TempoPlannedDetailRow;
import ru.paperless.report.dto.TempoPlannedSummaryRow;
import ru.paperless.report.entity.Employee;
import ru.paperless.report.repository.EmployeeRepository;
import ru.paperless.report.repository.JiraSprintStatusTransitionRepository;
import ru.paperless.report.repository.JiraSprintTempoPlannedStatusRepository;

import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class ExcelPlanningExportImpl implements ExcelPlanningExport {
    private final JiraSprintTempoPlannedStatusRepository tempoRepo;
    private final JiraSprintStatusTransitionRepository transitionRepo;
    private final EmployeeRepository employeeRepo;

    @Override
    public byte[] buildXlsx(String sprintIds,
                            List<String> doneStatusNames,
                            List<String> notClosedStatusNames) {
        log.info("Статусы done задач: {}", doneStatusNames);
        log.info("Статусы не закрытых задач: {}", notClosedStatusNames);

        Filter f = prepareFilter(getDevelopers(), sprintIds, doneStatusNames, notClosedStatusNames);

        List<TempoPlannedDetailRow> plannedDetails = tempoRepo.getDetails(
                true, f.employees(),
                f.useSprints(), f.safeSprintIds()
        );

        List<TempoPlannedDetailRow> details = mergeWithOutOfPlanTasks(plannedDetails, f);
        List<TempoPlannedSummaryRow> summary = aggregateSummary(details, f);

        return toXlsxBytes(details, summary, f);
    }

    private byte[] toXlsxBytes(List<TempoPlannedDetailRow> details,
                               List<TempoPlannedSummaryRow> summary,
                               Filter f) {

        try (Workbook wb = new XSSFWorkbook();
             ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            Set<String> doneStatuses = new HashSet<>(f.doneStatusNamesOriginal());

            CellStyle outOfPlanStyle = wb.createCellStyle();
            outOfPlanStyle.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
            outOfPlanStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);

            Font notDoneFont = wb.createFont();
            notDoneFont.setColor(IndexedColors.BROWN.getIndex());

            CellStyle notDoneStyle = wb.createCellStyle();
            notDoneStyle.setFont(notDoneFont);

            CellStyle outOfPlanNotDoneStyle = wb.createCellStyle();
            outOfPlanNotDoneStyle.cloneStyleFrom(outOfPlanStyle);
            outOfPlanNotDoneStyle.setFont(notDoneFont);

            CellStyle legendHeaderStyle = wb.createCellStyle();
            legendHeaderStyle.setAlignment(HorizontalAlignment.CENTER);

            Font boldFont = wb.createFont();
            boldFont.setBold(true);

            CellStyle headerStyle = wb.createCellStyle();
            headerStyle.setFont(boldFont);

            short percentFormat = wb.createDataFormat().getFormat("0%");

            Font orangeFont = wb.createFont();
            orangeFont.setColor(IndexedColors.ORANGE.getIndex());

            Font redFont = wb.createFont();
            redFont.setColor(IndexedColors.RED.getIndex());

            CellStyle percentStyle = wb.createCellStyle();
            percentStyle.setDataFormat(percentFormat);

            CellStyle orangePercentStyle = wb.createCellStyle();
            orangePercentStyle.setDataFormat(percentFormat);
            orangePercentStyle.setFont(orangeFont);

            CellStyle redPercentStyle = wb.createCellStyle();
            redPercentStyle.setDataFormat(percentFormat);
            redPercentStyle.setFont(redFont);

            CellStyle legendGreyStyle = wb.createCellStyle();
            legendGreyStyle.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
            legendGreyStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            legendGreyStyle.setAlignment(HorizontalAlignment.CENTER);

            CellStyle legendBrickStyle = wb.createCellStyle();
            legendBrickStyle.setFont(notDoneFont);
            legendBrickStyle.setAlignment(HorizontalAlignment.CENTER);

            Sheet s1 = wb.createSheet("1.2 План-факт задачи");
            int r = 0;

            Row m1 = s1.createRow(r++);
            m1.createCell(0).setCellValue("Разработка завершена");
            m1.createCell(1).setCellValue(
                    f.doneStatusNamesOriginal().isEmpty()
                            ? "EMPTY"
                            : String.join(", ", f.doneStatusNamesOriginal())
            );

            Row m2 = s1.createRow(r++);
            m2.createCell(0).setCellValue("Разработка не завершена");
            m2.createCell(1).setCellValue(
                    f.notClosedStatusNamesOriginal().isEmpty()
                            ? "EMPTY"
                            : String.join(", ", f.notClosedStatusNamesOriginal())
            );

            Row m3 = s1.createRow(r++);
            m3.createCell(0).setCellValue("Сотрудник");
            m3.createCell(1).setCellValue(String.join(", ", f.employees()));

            Row legendHeader = getOrCreateRow(s1, 0);
            legendHeader.createCell(5).setCellValue("Легенда:");
            legendHeader.getCell(5).setCellStyle(legendHeaderStyle);
            legendHeader.createCell(6).setCellValue("");
            legendHeader.getCell(6).setCellStyle(legendHeaderStyle);
            s1.addMergedRegion(new CellRangeAddress(0, 0, 5, 6));

            Row legendRow1 = getOrCreateRow(s1, 1);
            legendRow1.createCell(5).setCellValue("Внеплан Sprint'a");
            legendRow1.createCell(6).setCellValue("Серый");
            legendRow1.getCell(6).setCellStyle(legendGreyStyle);

            Row legendRow2 = getOrCreateRow(s1, 2);
            legendRow2.createCell(5).setCellValue("Разработка не завершена");
            legendRow2.createCell(6).setCellValue("Кирпичный");
            legendRow2.getCell(6).setCellStyle(legendBrickStyle);

            r++;

            Row h = s1.createRow(r++);
            h.createCell(0).setCellValue("Спринт");
            h.createCell(1).setCellValue("Сотрудник");
            h.createCell(2).setCellValue("Номер задачи");
            h.createCell(3).setCellValue("Название задачи");
            h.createCell(4).setCellValue("Запланировано, ч");
            h.createCell(5).setCellValue("Статус на начало");
            h.createCell(6).setCellValue("Статус на конец");
            for (int i = 0; i < 7; i++) {
                h.getCell(i).setCellStyle(headerStyle);
            }

            for (TempoPlannedDetailRow row : details) {
                Row x = s1.createRow(r++);
                x.createCell(0).setCellValue(nullSafe(row.getSprintName()));
                x.createCell(1).setCellValue(nullSafe(row.getEmployee()));
                x.createCell(2).setCellValue(nullSafe(row.getIssueKey()));
                x.createCell(3).setCellValue(nullSafe(row.getIssueSummary()));
                if (row.getPlannedSeconds() != null) {
                    x.createCell(4).setCellValue(row.getPlannedSeconds());
                } else {
                    x.createCell(4).setCellValue("");
                }
                x.createCell(5).setCellValue(nullSafe(row.getStatusAtSprintStart()));
                x.createCell(6).setCellValue(nullSafe(row.getStatusAtSprintEnd()));

                boolean isDone = StringUtils.hasText(row.getStatusAtSprintEnd())
                        && doneStatuses.contains(row.getStatusAtSprintEnd());

                if (Boolean.TRUE.equals(row.getOutOfPlan()) && !isDone) {
                    for (int i = 0; i < 7; i++) {
                        x.getCell(i).setCellStyle(outOfPlanNotDoneStyle);
                    }
                } else if (Boolean.TRUE.equals(row.getOutOfPlan())) {
                    for (int i = 0; i < 7; i++) {
                        x.getCell(i).setCellStyle(outOfPlanStyle);
                    }
                } else if (!isDone) {
                    for (int i = 0; i < 7; i++) {
                        x.getCell(i).setCellStyle(notDoneStyle);
                    }
                }
            }

            s1.setAutoFilter(new CellRangeAddress(h.getRowNum(), h.getRowNum(), 0, 6));
            for (int i = 0; i < 7; i++) s1.autoSizeColumn(i);
            s1.setColumnWidth(1, 35 * 256);
            s1.setColumnWidth(3, 65 * 256);

            Sheet s2 = wb.createSheet("1.1 План-факт кол-во задач");
            int d = 0;

            Row sh = s2.createRow(d++);
            sh.createCell(0).setCellValue("Сотрудник");
            sh.createCell(1).setCellValue("Спринт");
            sh.createCell(2).setCellValue("Количество задач запланировано");
            sh.createCell(3).setCellValue("Разработка завершена (из плана)");
            sh.createCell(4).setCellValue("Разработка не завершена (из плана)");
            sh.createCell(5).setCellValue("Вне плана");
            sh.createCell(6).setCellValue("% завершения плана");
            for (int i = 0; i < 7; i++) {
                sh.getCell(i).setCellStyle(headerStyle);
            }

            for (TempoPlannedSummaryRow row : summary) {
                Row x = s2.createRow(d++);
                x.createCell(0).setCellValue(nullSafe(row.getEmployee()));
                x.createCell(1).setCellValue(nullSafe(row.getSprintName()));
                x.createCell(2).setCellValue(row.getPlannedTasksCount() == null ? 0 : row.getPlannedTasksCount());
                x.createCell(3).setCellValue(row.getDoneTasksCount() == null ? 0 : row.getDoneTasksCount());
                x.createCell(4).setCellValue(row.getNotClosedTasksCount() == null ? 0 : row.getNotClosedTasksCount());
                x.createCell(5).setCellValue(row.getOutOfPlanTasksCount() == null ? 0 : row.getOutOfPlanTasksCount());

                long plannedCount = row.getPlannedTasksCount() == null ? 0 : row.getPlannedTasksCount();
                long doneCount = row.getDoneTasksCount() == null ? 0 : row.getDoneTasksCount();
                double doneRatio = plannedCount > 0 ? (double) doneCount / plannedCount : 0d;
                var percentCell = x.createCell(6);
                percentCell.setCellValue(doneRatio);
                if (doneRatio < 0.5d) {
                    percentCell.setCellStyle(redPercentStyle);
                } else if (doneRatio < 0.75d) {
                    percentCell.setCellStyle(orangePercentStyle);
                } else {
                    percentCell.setCellStyle(percentStyle);
                }
            }

            s2.setAutoFilter(new CellRangeAddress(sh.getRowNum(), sh.getRowNum(), 0, 6));
            for (int i = 0; i < 7; i++) s2.autoSizeColumn(i);

            wb.setSheetOrder("1.1 План-факт кол-во задач", 0);
            wb.setSheetOrder("1.2 План-факт задачи", 1);

            wb.write(baos);
            return baos.toByteArray();

        } catch (Exception e) {
            throw new RuntimeException("Не удалось сформировать XLSX", e);
        }
    }

    private List<TempoPlannedDetailRow> mergeWithOutOfPlanTasks(List<TempoPlannedDetailRow> plannedDetails, Filter f) {
        List<TempoPlannedDetailRow> result = new ArrayList<>(plannedDetails);
        Set<String> doneStatuses = new HashSet<>(f.doneStatusNamesOriginal());
        Set<String> plannedIssueKeys = plannedDetails.stream()
                .map(this::toSprintIssueKey)
                .collect(Collectors.toCollection(HashSet::new));
        Set<String> addedOutOfPlanKeys = new HashSet<>();

        List<OutOfPlanTaskProjection> transitionTasks = transitionRepo.getLatestTasksForPlanning(
                f.employees(),
                f.useSprints(),
                f.safeSprintIds()
        );

        for (OutOfPlanTaskProjection task : transitionTasks) {
            if (StringUtils.hasText(task.getStatusAtSprintStart())
                    && doneStatuses.contains(task.getStatusAtSprintStart())) {
                continue;
            }

            TempoPlannedDetailRow row = new TempoPlannedDetailRow(
                    task.getSprintId(),
                    task.getSprintName(),
                    task.getEmployee(),
                    task.getIssueKey(),
                    task.getIssueSummary(),
                    null,
                    task.getStatusAtSprintStart(),
                    task.getStatusAtSprintEnd(),
                    true
            );

            String sprintIssueKey = toSprintIssueKey(row);
            if (!plannedIssueKeys.contains(sprintIssueKey) && addedOutOfPlanKeys.add(toDetailKey(row))) {
                result.add(row);
            }
        }

        result.sort(Comparator
                .comparing(TempoPlannedDetailRow::getSprintId, Comparator.nullsLast(Comparator.naturalOrder()))
                .thenComparing(TempoPlannedDetailRow::getEmployee, Comparator.nullsLast(String::compareTo))
                .thenComparing(TempoPlannedDetailRow::getIssueKey, Comparator.nullsLast(String::compareTo)));

        return result;
    }

    private List<TempoPlannedSummaryRow> aggregateSummary(List<TempoPlannedDetailRow> details, Filter f) {
        record SummaryKey(String employee, Long sprintId, String sprintName) {}
        class SummaryAcc {
            long plannedTasksCount;
            long doneTasksCount;
            long notClosedTasksCount;
            long outOfPlanTasksCount;
        }

        Set<String> doneStatuses = new HashSet<>(f.doneStatusNamesOriginal());
        Set<String> notClosedStatuses = new HashSet<>(f.notClosedStatusNamesOriginal());
        Map<SummaryKey, SummaryAcc> aggregated = new HashMap<>();

        for (TempoPlannedDetailRow row : details) {
            SummaryKey key = new SummaryKey(row.getEmployee(), row.getSprintId(), row.getSprintName());
            SummaryAcc acc = aggregated.computeIfAbsent(key, k -> new SummaryAcc());

            if (Boolean.TRUE.equals(row.getOutOfPlan())) {
                acc.outOfPlanTasksCount++;
                continue;
            }

            acc.plannedTasksCount++;

            if (StringUtils.hasText(row.getStatusAtSprintEnd()) && doneStatuses.contains(row.getStatusAtSprintEnd())) {
                acc.doneTasksCount++;
            }
            if (StringUtils.hasText(row.getStatusAtSprintEnd()) && notClosedStatuses.contains(row.getStatusAtSprintEnd())) {
                acc.notClosedTasksCount++;
            }
        }

        return aggregated.entrySet().stream()
                .sorted(Map.Entry.comparingByKey(
                        Comparator.comparing(SummaryKey::employee, Comparator.nullsLast(String::compareTo))
                                .thenComparing(SummaryKey::sprintId, Comparator.nullsLast(Comparator.naturalOrder()))
                                .thenComparing(SummaryKey::sprintName, Comparator.nullsLast(String::compareTo))
                ))
                .map(e -> new TempoPlannedSummaryRow(
                        e.getKey().employee(),
                        e.getKey().sprintId(),
                        e.getKey().sprintName(),
                        e.getValue().plannedTasksCount,
                        e.getValue().doneTasksCount,
                        e.getValue().notClosedTasksCount,
                        e.getValue().outOfPlanTasksCount
                ))
                .toList();
    }

    private Filter prepareFilter(List<String> employees,
                                 String sprintIdsText,
                                 List<String> doneStatusNames,
                                 List<String> notClosedStatusNames) {

        List<String> normalizedEmployees = normalizeStrings(employees);
        if (normalizedEmployees.isEmpty()) {
            throw new IllegalArgumentException("Сотрудник обязателен (список сотрудников не должен быть пустым)");
        }

        List<Long> sprintIds = parseLongIds(sprintIdsText);
        boolean useSprints = !sprintIds.isEmpty();
        List<Long> safeSprints = useSprints ? sprintIds : List.of(-1L);

        List<String> normalizedDone = normalizeStrings(doneStatusNames);
        boolean useDone = !normalizedDone.isEmpty();
        List<String> safeDone = useDone ? normalizedDone : List.of("__NO_STATUS__");

        List<String> normalizedNotClosed = normalizeStrings(notClosedStatusNames);
        boolean useNotClosed = !normalizedNotClosed.isEmpty();
        List<String> safeNotClosed = useNotClosed ? normalizedNotClosed : List.of("__NO_STATUS__");

        return new Filter(
                normalizedEmployees,
                useSprints, safeSprints,
                useDone, safeDone,
                useNotClosed, safeNotClosed,
                sprintIdsText,
                normalizedDone,
                normalizedNotClosed
        );
    }

    private List<Long> parseLongIds(String text) {
        if (!StringUtils.hasText(text)) return List.of();
        return Arrays.stream(text.trim().split("\\s+"))
                .filter(StringUtils::hasText)
                .map(Long::valueOf)
                .distinct()
                .collect(Collectors.toList());
    }

    private List<String> normalizeStrings(List<String> input) {
        if (input == null) return List.of();
        return input.stream()
                .filter(Objects::nonNull)
                .map(String::trim)
                .filter(StringUtils::hasText)
                .distinct()
                .toList();
    }

    private String toDetailKey(TempoPlannedDetailRow row) {
        return String.join("|",
                nullSafe(row.getEmployee()),
                row.getSprintId() == null ? "" : String.valueOf(row.getSprintId()),
                nullSafe(row.getIssueKey()));
    }

    private String toSprintIssueKey(TempoPlannedDetailRow row) {
        return String.join("|",
                row.getSprintId() == null ? "" : String.valueOf(row.getSprintId()),
                nullSafe(row.getIssueKey()));
    }

    private String nullSafe(String s) {
        return s == null ? "" : s;
    }

    private Row getOrCreateRow(Sheet sheet, int rowIndex) {
        Row row = sheet.getRow(rowIndex);
        return row != null ? row : sheet.createRow(rowIndex);
    }

    private record Filter(
            List<String> employees,
            boolean useSprints, List<Long> safeSprintIds,
            boolean useDone, List<String> safeDoneStatusNames,
            boolean useNotClosed, List<String> safeNotClosedStatusNames,
            String sprintIdsTextOriginal,
            List<String> doneStatusNamesOriginal,
            List<String> notClosedStatusNamesOriginal
    ) {}

    private List<String> getDevelopers() {
        return employeeRepo.findBySelectableTrue()
                .stream()
                .map(Employee::getFullName)
                .filter(StringUtils::hasText)
                .distinct()
                .collect(Collectors.toList());
    }
}

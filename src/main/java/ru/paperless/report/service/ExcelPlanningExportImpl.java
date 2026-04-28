package ru.paperless.report.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.Font;
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

            Sheet s1 = wb.createSheet("1.2 План-факт задачи");
            int r = 0;

            Row m1 = s1.createRow(r++);
            m1.createCell(0).setCellValue("ID Спринта");
            m1.createCell(1).setCellValue(
                    StringUtils.hasText(f.sprintIdsTextOriginal())
                            ? f.sprintIdsTextOriginal().trim()
                            : "ALL"
            );

            Row m2 = s1.createRow(r++);
            m2.createCell(0).setCellValue("Done статусы");
            m2.createCell(1).setCellValue(
                    f.doneStatusNamesOriginal().isEmpty()
                            ? "EMPTY"
                            : String.join(", ", f.doneStatusNamesOriginal())
            );

            Row m3 = s1.createRow(r++);
            m3.createCell(0).setCellValue("Не готово");
            m3.createCell(1).setCellValue(
                    f.notClosedStatusNamesOriginal().isEmpty()
                            ? "EMPTY"
                            : String.join(", ", f.notClosedStatusNamesOriginal())
            );

            Row m4 = s1.createRow(r++);
            m4.createCell(0).setCellValue("Сотрудник");
            m4.createCell(1).setCellValue(String.join(", ", f.employees()));

            r++;

            Row h = s1.createRow(r++);
            h.createCell(0).setCellValue("ID спринта");
            h.createCell(1).setCellValue("Спринт");
            h.createCell(2).setCellValue("Сотрудник");
            h.createCell(3).setCellValue("Номер задачи");
            h.createCell(4).setCellValue("Название задачи");
            h.createCell(5).setCellValue("Запланировано, ч");
            h.createCell(6).setCellValue("Статус на начало");
            h.createCell(7).setCellValue("Статус на конец");

            for (TempoPlannedDetailRow row : details) {
                Row x = s1.createRow(r++);
                x.createCell(0).setCellValue(row.getSprintId() == null ? "" : String.valueOf(row.getSprintId()));
                x.createCell(1).setCellValue(nullSafe(row.getSprintName()));
                x.createCell(2).setCellValue(nullSafe(row.getEmployee()));
                x.createCell(3).setCellValue(nullSafe(row.getIssueKey()));
                x.createCell(4).setCellValue(nullSafe(row.getIssueSummary()));
                if (row.getPlannedSeconds() != null) {
                    x.createCell(5).setCellValue(row.getPlannedSeconds());
                } else {
                    x.createCell(5).setCellValue("");
                }
                x.createCell(6).setCellValue(nullSafe(row.getStatusAtSprintStart()));
                x.createCell(7).setCellValue(nullSafe(row.getStatusAtSprintEnd()));

                boolean isDone = StringUtils.hasText(row.getStatusAtSprintEnd())
                        && doneStatuses.contains(row.getStatusAtSprintEnd());

                if (Boolean.TRUE.equals(row.getOutOfPlan()) && !isDone) {
                    for (int i = 0; i < 8; i++) {
                        x.getCell(i).setCellStyle(outOfPlanNotDoneStyle);
                    }
                } else if (Boolean.TRUE.equals(row.getOutOfPlan())) {
                    for (int i = 0; i < 8; i++) {
                        x.getCell(i).setCellStyle(outOfPlanStyle);
                    }
                } else if (!isDone) {
                    for (int i = 0; i < 8; i++) {
                        x.getCell(i).setCellStyle(notDoneStyle);
                    }
                }
            }

            s1.setAutoFilter(new CellRangeAddress(h.getRowNum(), h.getRowNum(), 0, 7));
            for (int i = 0; i < 8; i++) s1.autoSizeColumn(i);
            s1.setColumnWidth(1, 25 * 256);
            s1.setColumnWidth(2, 30 * 256);
            s1.setColumnWidth(4, 60 * 256);

            Sheet s2 = wb.createSheet("1.1 План-факт кол-во задач");
            int d = 0;

            Row sh = s2.createRow(d++);
            sh.createCell(0).setCellValue("Сотрудник");
            sh.createCell(1).setCellValue("ID спринта");
            sh.createCell(2).setCellValue("Спринт");
            sh.createCell(3).setCellValue("Количество задач запланировано");
            sh.createCell(4).setCellValue("Разработка завершена (из плана)");
            sh.createCell(5).setCellValue("Разработка не завершена (из плана)");
            sh.createCell(6).setCellValue("Вне плана");

            for (TempoPlannedSummaryRow row : summary) {
                Row x = s2.createRow(d++);
                x.createCell(0).setCellValue(nullSafe(row.getEmployee()));
                x.createCell(1).setCellValue(row.getSprintId() == null ? "" : String.valueOf(row.getSprintId()));
                x.createCell(2).setCellValue(nullSafe(row.getSprintName()));
                x.createCell(3).setCellValue(row.getPlannedTasksCount() == null ? 0 : row.getPlannedTasksCount());
                x.createCell(4).setCellValue(row.getDoneTasksCount() == null ? 0 : row.getDoneTasksCount());
                x.createCell(5).setCellValue(row.getNotClosedTasksCount() == null ? 0 : row.getNotClosedTasksCount());
                x.createCell(6).setCellValue(row.getOutOfPlanTasksCount() == null ? 0 : row.getOutOfPlanTasksCount());
            }

            s2.setAutoFilter(new CellRangeAddress(sh.getRowNum(), sh.getRowNum(), 0, 6));
            for (int i = 0; i < 7; i++) s2.autoSizeColumn(i);

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

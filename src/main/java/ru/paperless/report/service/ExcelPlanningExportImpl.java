package ru.paperless.report.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.util.StringUtils;
import ru.paperless.report.dto.TempoPlannedDetailRow;
import ru.paperless.report.dto.TempoPlannedSummaryRow;
import ru.paperless.report.entity.Employee;
import ru.paperless.report.repository.EmployeeRepository;
import ru.paperless.report.repository.JiraSprintTempoPlannedStatusRepository;

import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class ExcelPlanningExportImpl implements ExcelPlanningExport {
    private final JiraSprintTempoPlannedStatusRepository tempoRepo;
    private final EmployeeRepository employeeRepo;

    /**
     * @param sprintIds       "101 102" через пробел; пусто/null -> ALL
     * @param doneStatusNames имена статусов через пробел; пусто/null -> doneTasksCount=0
     *                        <p>
     *                        Важно: если в статусах бывают пробелы ("In Progress"), лучше передавать через кавычки,
     *                        или поменять разделитель на ';'. См. parseStatusNames().
     */
    @Override
    public byte[] buildXlsx(String sprintIds,
                            List<String> doneStatusNames,
                            List<String> notClosedStatusNames) {
        log.info("Статусы done задач: {}", doneStatusNames);
        log.info("Статусы не закрытых задач: {}", notClosedStatusNames);

        Filter f = prepareFilter(getDevelopers(), sprintIds, doneStatusNames, notClosedStatusNames);

        List<TempoPlannedDetailRow> details = tempoRepo.getDetails(
                true, f.employees(),
                f.useSprints(), f.safeSprintIds()
        );

        List<TempoPlannedSummaryRow> summary = tempoRepo.getSummary(
                true, f.employees(),
                f.useSprints(), f.safeSprintIds(),
                f.useDone(), f.safeDoneStatusNames(),
                f.useNotClosed(), f.safeNotClosedStatusNames()
        );

        return toXlsxBytes(details, summary, f);
    }
    private byte[] toXlsxBytes(List<TempoPlannedDetailRow> details,
                               List<TempoPlannedSummaryRow> summary,
                               Filter f) {

        try (Workbook wb = new XSSFWorkbook();
             ByteArrayOutputStream baos = new ByteArrayOutputStream()) {

            // ---------- Sheet 1: Details ----------
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

            r++; // пустая строка

            Row h = s1.createRow(r++);
            h.createCell(0).setCellValue("ID спринта");
            h.createCell(1).setCellValue("Спринт");
            h.createCell(2).setCellValue("Сотрудник");
            h.createCell(3).setCellValue("Номер задачи");
            h.createCell(4).setCellValue("Запланировано, ч");
            h.createCell(5).setCellValue("Статус на начало");
            h.createCell(6).setCellValue("Статус на конец");

            for (TempoPlannedDetailRow row : details) {
                Row x = s1.createRow(r++);
                x.createCell(0).setCellValue(row.getSprintId() == null ? "" : String.valueOf(row.getSprintId()));
                x.createCell(1).setCellValue(nullSafe(row.getSprintName()));
                x.createCell(2).setCellValue(nullSafe(row.getEmployee()));
                x.createCell(3).setCellValue(nullSafe(row.getIssueKey()));
                x.createCell(4).setCellValue(row.getPlannedSeconds() == null ? 0 : row.getPlannedSeconds());
                x.createCell(5).setCellValue(nullSafe(row.getStatusAtSprintStart()));
                x.createCell(6).s
            }

            for (int i = 0; i < 7; i++) s1.autoSizeColumn(i);

            // ---------- Sheet 2: Summary ----------
            Sheet s2 = wb.createSheet("1.1 План-факт кол-во задач");
            int d = 0;

            Row sh = s2.createRow(d++);
            sh.createCell(0).setCellValue("Сотрудник");
            sh.createCell(1).setCellValue("ID спринта");
            sh.createCell(2).setCellValue("Спринт");
            sh.createCell(3).setCellValue("Количество задач запланировано");
            sh.createCell(4).setCellValue("Количество done-задач");
            sh.createCell(5).setCellValue("Количество не закрытых задач");

            for (TempoPlannedSummaryRow row : summary) {
                Row x = s2.createRow(d++);
                x.createCell(0).setCellValue(nullSafe(row.getEmployee()));
                x.createCell(1).setCellValue(row.getSprintId() == null ? "" : String.valueOf(row.getSprintId()));
                x.createCell(2).setCellValue(nullSafe(row.getSprintName()));
                x.createCell(3).setCellValue(row.getPlannedTasksCount() == null ? 0 : row.getPlannedTasksCount());
                x.createCell(4).setCellValue(row.getDoneTasksCount() == null ? 0 : row.getDoneTasksCount());
                x.createCell(5).setCellValue(row.getNotClosedTasksCount() == null ? 0 : row.getNotClosedTasksCount());
            }

            for (int i = 0; i < 6; i++) s2.autoSizeColumn(i);

            wb.write(baos);
            return baos.toByteArray();

        } catch (Exception e) {
            throw new RuntimeException("Не удалось сформировать XLSX", e);
        }
    }

    // ---------- filter prep ----------
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

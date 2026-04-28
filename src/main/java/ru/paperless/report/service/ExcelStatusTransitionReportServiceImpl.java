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
import ru.paperless.report.dto.TransitionDetailRow;
import ru.paperless.report.dto.TransitionReportRow;
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
public class ExcelStatusTransitionReportServiceImpl implements ExcelStatusTransitionReportService {

    private final JiraSprintStatusTransitionRepository reportRepository;
    private final EmployeeRepository employeeRepo;
    private final ProjectJiraStatusRepository projectJiraStatusRepository;

    private static final DateTimeFormatter DT_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
                    .withZone(ZoneId.systemDefault());

    /**
     * Генерирует XLSX:
     * 1-й лист: агрегат employee + sprint
     * 2-й лист: детализация переходов (issue_key, sprint, from->to, developer, date)
     * <p>
     * Фильтры:
     * - fromStatusIds: пусто/null -> все from_status
     * - toStatusIds:   пусто/null -> все to_status
     * - employees: обязателен. матчим по final_assignee ИЛИ developer
     * - sprintIdsText: "101 102" (через пробел). пусто/null -> все спринты
     */
    public byte[] buildXlsx(List<Long> fromStatusIds,
                            List<Long> toStatusIds,
                            String sprintIdsText) {

        Filter f = prepareFilter(fromStatusIds, toStatusIds, getDevelopers(), sprintIdsText);

        List<TransitionReportRow> summaryRows = reportRepository.getReportByStatusLists(
                f.employees(), f.useFrom(), f.safeFromIds(), f.useTo(), f.safeToIds(), f.useSprints(), f.safeSprintIds()
        );

        List<TransitionDetailRow> detailRows = reportRepository.getDetailsByStatusLists(
                f.employees(), f.useFrom(), f.safeFromIds(), f.useTo(), f.safeToIds(), f.useSprints(), f.safeSprintIds()
        );

        return toXlsxBytes(summaryRows, detailRows, f);
    }

    private byte[] toXlsxBytes(List<TransitionReportRow> summaryRows,
                               List<TransitionDetailRow> detailRows,
                               Filter f) {

        try (Workbook wb = new XSSFWorkbook();
             ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            Map<Long, String> statusNamesById = getStatusNamesById(f);
            Font boldFont = wb.createFont();
            boldFont.setBold(true);

            CellStyle headerStyle = wb.createCellStyle();
            headerStyle.setFont(boldFont);

            // ---------- Sheet 1: Summary ----------
            Sheet s1 = wb.createSheet("2.1 Количество возвратов по спринтам");
            int r = 0;

            Row m1 = s1.createRow(r++);
            m1.createCell(0).setCellValue("Из статуса");
            m1.createCell(1).setCellValue(formatStatusListOrAll(f.fromIdsOriginal(), statusNamesById));

            Row m2 = s1.createRow(r++);
            m2.createCell(0).setCellValue("В статус");
            m2.createCell(1).setCellValue(formatStatusListOrAll(f.toIdsOriginal(), statusNamesById));

            Row m3 = s1.createRow(r++);
            m3.createCell(0).setCellValue("Сотрудник");
            m3.createCell(1).setCellValue(String.join(", ", f.employees()));

            r++; // empty row

            Row header = s1.createRow(r++);
            header.createCell(0).setCellValue("Спринт");
            header.createCell(1).setCellValue("Сотрудник");
            header.createCell(2).setCellValue("Из статуса");
            header.createCell(3).setCellValue("Количество возвратов");
            for (int i = 0; i < 4; i++) {
                header.getCell(i).setCellStyle(headerStyle);
            }

            for (TransitionReportRow row : summaryRows) {
                Row x = s1.createRow(r++);
                x.createCell(0).setCellValue(nullSafe(row.getSprintName()));
                x.createCell(1).setCellValue(nullSafe(row.getEmployee()));
                x.createCell(2).setCellValue(nullSafe(row.getFromStatusName()));
                x.createCell(3).setCellValue(row.getTransitionsCount() == null ? 0 : row.getTransitionsCount());
            }

            s1.setAutoFilter(new CellRangeAddress(header.getRowNum(), header.getRowNum(), 0, 3));
            for (int i = 0; i < 4; i++) s1.autoSizeColumn(i);
            s1.setColumnWidth(1, 35 * 256);

            // ---------- Sheet 2: Details ----------
            Sheet s2 = wb.createSheet("2.2 Возвраты по задачам");
            int d = 0;

            Row dh = s2.createRow(d++);
            dh.createCell(0).setCellValue("Спринт");
            dh.createCell(1).setCellValue("Сотрудник");
            dh.createCell(2).setCellValue("Номер задачи");          // "название задачи" => issue_key
            dh.createCell(3).setCellValue("Из статуса");
            dh.createCell(4).setCellValue("В статус");
            dh.createCell(5).setCellValue("Дата перехода");
            for (int i = 0; i < 6; i++) {
                dh.getCell(i).setCellStyle(headerStyle);
            }

            for (TransitionDetailRow row : detailRows) {
                Row x = s2.createRow(d++);
                x.createCell(0).setCellValue(nullSafe(row.getSprintName()));
                x.createCell(1).setCellValue(nullSafe(row.getDeveloper()));
                x.createCell(2).setCellValue(nullSafe(row.getIssueKey()));
                x.createCell(3).setCellValue(nullSafe(row.getFromStatusName()));
                x.createCell(4).setCellValue(nullSafe(row.getToStatusName()));
                x.createCell(5).setCellValue(formatDate(row.getTransitionDate()));
            }

            s2.setAutoFilter(new CellRangeAddress(dh.getRowNum(), dh.getRowNum(), 0, 5));
            for (int i = 0; i < 6; i++) s2.autoSizeColumn(i);

            wb.write(baos);
            return baos.toByteArray();
        } catch (Exception e) {
            throw new RuntimeException("Не удалось сформировать XLSX", e);
        }
    }

    // ---------- filter prep ----------
    private Filter prepareFilter(List<Long> fromStatusIds,
                                 List<Long> toStatusIds,
                                 List<String> employees,
                                 String sprintIdsText) {

        List<String> normalizedEmployees = normalizeEmployees(employees);
        if (normalizedEmployees.isEmpty()) {
            throw new IllegalArgumentException("Сотрудник обязателен (список сотрудников не должен быть пустым)");
        }

        List<Long> fromIds = normalizeLongList(fromStatusIds);
        List<Long> toIds = normalizeLongList(toStatusIds);

        boolean useFrom = !fromIds.isEmpty();
        boolean useTo = !toIds.isEmpty();

        List<Long> sprintIds = parseSprintIds(sprintIdsText);
        boolean useSprints = !sprintIds.isEmpty();

        // безопасные списки для IN (...)
        List<Long> safeFrom = useFrom ? fromIds : List.of(-1L);
        List<Long> safeTo = useTo ? toIds : List.of(-1L);
        List<Long> safeSprints = useSprints ? sprintIds : List.of(-1L);

        return new Filter(
                normalizedEmployees,
                useFrom, safeFrom,
                useTo, safeTo,
                useSprints, safeSprints,
                fromIds, toIds,
                sprintIdsText
        );
    }

    private List<Long> parseSprintIds(String sprintIdsText) {
        if (!StringUtils.hasText(sprintIdsText)) return List.of();

        return Arrays.stream(sprintIdsText.trim().split("\\s+"))
                .filter(StringUtils::hasText)
                .map(Long::valueOf)
                .distinct()
                .collect(Collectors.toList());
    }

    private List<Long> normalizeLongList(List<Long> input) {
        if (input == null) return List.of();
        return input.stream()
                .filter(Objects::nonNull)
                .distinct()
                .toList();
    }

    private List<String> normalizeEmployees(List<String> employees) {
        if (employees == null) return List.of();
        return employees.stream()
                .filter(Objects::nonNull)
                .map(String::trim)
                .filter(StringUtils::hasText)
                .distinct()
                .toList();
    }

    private String formatStatusListOrAll(List<Long> ids, Map<Long, String> statusNamesById) {
        if (ids == null || ids.isEmpty()) return "ALL";
        return ids.stream()
                .map(id -> statusNamesById.getOrDefault(id, String.valueOf(id)))
                .collect(Collectors.joining(", "));
    }

    private Map<Long, String> getStatusNamesById(Filter f) {
        List<Long> statusIds = java.util.stream.Stream.concat(
                        f.fromIdsOriginal().stream(),
                        f.toIdsOriginal().stream()
                )
                .filter(Objects::nonNull)
                .distinct()
                .toList();

        if (statusIds.isEmpty()) {
            return Map.of();
        }

        Map<Long, String> statusNamesById = new HashMap<>();
        for (ProjectJiraStatus status : projectJiraStatusRepository.findAllById(statusIds)) {
            if (status.getStatusId() != null && StringUtils.hasText(status.getStatusName())) {
                statusNamesById.put(status.getStatusId(), status.getStatusName());
            }
        }
        return statusNamesById;
    }

    private String nullSafe(String s) {
        return s == null ? "" : s;
    }

    private String formatDate(Instant instant) {
        return instant == null ? "" : DT_FMT.format(instant);
    }

    private record Filter(
            List<String> employees,
            boolean useFrom, List<Long> safeFromIds,
            boolean useTo, List<Long> safeToIds,
            boolean useSprints, List<Long> safeSprintIds,
            List<Long> fromIdsOriginal,
            List<Long> toIdsOriginal,
            String sprintIdsTextOriginal
    ) {
    }

    private List<String> getDevelopers() {

        return employeeRepo.findBySelectableTrue()
                .stream()
                .map(Employee::getFullName)
                .filter(StringUtils::hasText)
                .distinct()
                .collect(Collectors.toList());
    }
}

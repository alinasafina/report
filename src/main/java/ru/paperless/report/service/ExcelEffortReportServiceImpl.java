package ru.paperless.report.service;

import lombok.RequiredArgsConstructor;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import ru.paperless.report.dto.EffortReportRow;
import ru.paperless.report.repository.JiraSprintEmployeeEffortRepository;

import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.util.*;

@Service
@RequiredArgsConstructor
public class ExcelEffortReportServiceImpl implements ExcelEffortReportService {

    private final JiraSprintEmployeeEffortRepository repo;

    @Override
    public byte[] buildXlsx(String sprintIdsText) {
        List<Long> sprintIds = parseSprintIds(sprintIdsText);
        Long[] sprintArr = sprintIds.toArray(Long[]::new);
        boolean sprintsEmpty = sprintArr.length == 0;

        List<EffortReportRow> detailRows = repo.getEffortReport(sprintArr, sprintsEmpty);

        List<SummaryRow> summaryByEmployeeSprint = aggregateByEmployeeSprint(detailRows);
        List<EmployeeTotalRow> totalsByEmployee = aggregateTotalsByEmployee(summaryByEmployeeSprint);

        // список спринтов считаем по sprint_first
        List<SprintRow> usedSprints = extractUsedSprints(detailRows);

        return toXlsxBytes(
                summaryByEmployeeSprint,
                detailRows,
                usedSprints,
                totalsByEmployee,
                sprintIdsText
        );
    }

    private byte[] toXlsxBytes(List<SummaryRow> summaryRows,
                               List<EffortReportRow> detailRows,
                               List<SprintRow> usedSprints,
                               List<EmployeeTotalRow> totalsByEmployee,
                               String sprintIdsTextOriginal) {

        try (Workbook wb = new XSSFWorkbook();
             ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            Font boldFont = wb.createFont();
            boldFont.setBold(true);

            CellStyle headerStyle = wb.createCellStyle();
            headerStyle.setFont(boldFont);

            // ===================== Sheet 1: Summary =====================
            Sheet s1 = wb.createSheet("3.2 Соотвествия оценке по спринтам");
            int r1 = 0;

            Row h1 = s1.createRow(r1++);
            h1.createCell(0).setCellValue("Сотрудник");
            h1.createCell(1).setCellValue("Спринт (first)");
            h1.createCell(2).setCellValue("Спринт (last logged)");
            h1.createCell(3).setCellValue("Соответсвует оценке");
            h1.createCell(4).setCellValue("Несоответсвует оценке");
            h1.createCell(5).setCellValue("Без оценки разработки");

            for (SummaryRow sr : summaryRows) {
                Row x = s1.createRow(r1++);
                x.createCell(0).setCellValue(nullSafe(sr.employee));
                x.createCell(1).setCellValue(nullSafe(sr.sprintFirstName));
                x.createCell(2).setCellValue(nullSafe(sr.sprintLastLoggedName));
                x.createCell(3).setCellValue(sr.loggedLeFirstCount);
                x.createCell(4).setCellValue(sr.loggedGtFirstCount);
                x.createCell(5).setCellValue(sr.firstEqZeroCount);
            }

            for (int i = 0; i <= 5; i++) s1.autoSizeColumn(i);

            // ===================== Sheet 2: Details =====================
            Sheet s2 = wb.createSheet("3.3 Соответсвия оценке по задачам");
            int r2 = 0;

            Row h2 = s2.createRow(r2++);
            h2.createCell(0).setCellValue("Сотрудник");
            h2.createCell(1).setCellValue("Спринт (first)");
            h2.createCell(2).setCellValue("Спринт (last logged)");
            h2.createCell(3).setCellValue("Задача");
            h2.createCell(4).setCellValue("Эпик");
            h2.createCell(5).setCellValue("Оценка разработки");
            h2.createCell(6).setCellValue("Списано часов");

            int dataStartRow = r2;
            int estCellIndex = 5;
            int logCellIndex = 6;

            for (EffortReportRow row : detailRows) {
                Row x = s2.createRow(r2++);

                x.createCell(0).setCellValue(nullSafe(row.getEmployee()));
                x.createCell(1).setCellValue(nullSafe(row.getSprintFirstName()));
                x.createCell(2).setCellValue(nullSafe(row.getSprintLastLoggedName()));
                x.createCell(3).setCellValue(nullSafe(row.getIssueKey()));
                x.createCell(4).setCellValue(nullSafe(row.getEpicKey()));

                Cell estCell = x.createCell(estCellIndex);
                setNumeric(estCell, row.getFirstEstimateHours());

                Cell logCell = x.createCell(logCellIndex);
                setNumeric(logCell, row.getLoggedHours());
            }

            int dataEndRow = r2 - 1;
            if (!detailRows.isEmpty()) {
                applyConditionalFormattingForDetails(s2, dataStartRow, dataEndRow, estCellIndex, logCellIndex);
            }
            for (int i = 0; i <= 6; i++) s2.autoSizeColumn(i);

            // ===================== Sheet 3: Info =====================
            Sheet s3 = wb.createSheet("3.1 Соответсвие оценке по сотрудникам");
            int r3 = 0;

            Row t1 = s3.createRow(r3++);
            t1.createCell(0).setCellValue("Список спринтов (first)");
            t1.createCell(4).setCellValue("Общее по спринтам");

            Row hs = s3.createRow(r3++);
            hs.createCell(0).setCellValue("Спринт (first)");

            hs.createCell(4).setCellValue("Сотрудник");
            hs.createCell(5).setCellValue("Соответсвует оценке");
            hs.createCell(6).setCellValue("Несоответсвует оценке");
            hs.createCell(7).setCellValue("Без оценки разработки");
            for (int i = 0; i <= 7; i++) {
                if (hs.getCell(i) != null) {
                    hs.getCell(i).setCellStyle(headerStyle);
                }
            }

            int startDataRow = r3;

            int rr = startDataRow;
            for (SprintRow s : usedSprints) {
                Row row = getOrCreateRow(s3, rr++);
                row.createCell(0).setCellValue(nullSafe(s.sprintName));
            }

            rr = startDataRow;
            for (EmployeeTotalRow t : totalsByEmployee) {
                Row row = getOrCreateRow(s3, rr++);
                row.createCell(4).setCellValue(nullSafe(t.employee));
                row.createCell(5).setCellValue(t.loggedLeFirstCount);
                row.createCell(6).setCellValue(t.loggedGtFirstCount);
                row.createCell(7).setCellValue(t.firstEqZeroCount);
            }

            for (int i = 0; i <= 7; i++) s3.autoSizeColumn(i);
            s3.setAutoFilter(new CellRangeAddress(hs.getRowNum(), hs.getRowNum(), 4, 7));

            wb.setSheetOrder("3.1 Соответсвие оценке по сотрудникам", 0);
            wb.setSheetOrder("3.2 Соотвествия оценке по спринтам", 1);
            wb.setSheetOrder("3.3 Соответсвия оценке по задачам", 2);

            wb.write(baos);
            return baos.toByteArray();

        } catch (Exception e) {
            throw new RuntimeException("Не удалось сформировать XLSX (effort report)", e);
        }
    }

    /**
     * Условное форматирование:
     * - grey  : first = 0
     * - green : first<>0 AND logged<=first
     * - red   : first<>0 AND logged>first
     *
     * estCol / logCol — индексы колонок (0-based).
     */
    private void applyConditionalFormattingForDetails(Sheet sh,
                                                      int dataStartRow,
                                                      int dataEndRow,
                                                      int estCol,
                                                      int logCol) {

        SheetConditionalFormatting scf = sh.getSheetConditionalFormatting();

        CellRangeAddress[] bothRange = {
                new CellRangeAddress(dataStartRow, dataEndRow, estCol, logCol)
        };

        // Excel row numbering is 1-based
        int excelRow = dataStartRow + 1;
        String firstRef = "$" + colLetter(estCol) + excelRow;
        String loggedRef = "$" + colLetter(logCol) + excelRow;

        String greenFormula = "AND(" + firstRef + "<>0," + loggedRef + "<=" + firstRef + ")";
        ConditionalFormattingRule greenRule = scf.createConditionalFormattingRule(greenFormula);
        PatternFormatting greenFill = greenRule.createPatternFormatting();
        greenFill.setFillForegroundColor(IndexedColors.LIGHT_GREEN.getIndex());
        greenFill.setFillBackgroundColor(IndexedColors.LIGHT_GREEN.getIndex());
        greenFill.setFillPattern(PatternFormatting.SOLID_FOREGROUND);

        String redFormula = "AND(" + firstRef + "<>0," + loggedRef + ">" + firstRef + ")";
        ConditionalFormattingRule redRule = scf.createConditionalFormattingRule(redFormula);
        PatternFormatting redFill = redRule.createPatternFormatting();
        redFill.setFillForegroundColor(IndexedColors.ROSE.getIndex());
        redFill.setFillBackgroundColor(IndexedColors.ROSE.getIndex());
        redFill.setFillPattern(PatternFormatting.SOLID_FOREGROUND);

        String greyFormula = firstRef + "=0";
        ConditionalFormattingRule greyRule = scf.createConditionalFormattingRule(greyFormula);
        PatternFormatting greyFill = greyRule.createPatternFormatting();
        greyFill.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
        greyFill.setFillBackgroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
        greyFill.setFillPattern(PatternFormatting.SOLID_FOREGROUND);

        scf.addConditionalFormatting(bothRange, new ConditionalFormattingRule[]{greenRule, redRule, greyRule});
    }

    private String colLetter(int colIdx) {
        // 0->A, 1->B, ... 25->Z, 26->AA ...
        int x = colIdx;
        StringBuilder sb = new StringBuilder();
        while (x >= 0) {
            sb.insert(0, (char) ('A' + (x % 26)));
            x = x / 26 - 1;
        }
        return sb.toString();
    }

    // ===================== Aggregations =====================

    /**
     * Summary: employee + sprint_first + sprint_last_logged
     * (иначе на уровне одного sprint_first может быть много разных sprint_last_logged, и будет “каша”)
     */
    private List<SummaryRow> aggregateByEmployeeSprint(List<EffortReportRow> rows) {
        Map<SummaryKey, SummaryAcc> map = new LinkedHashMap<>();

        for (EffortReportRow r : rows) {
            SummaryKey key = new SummaryKey(
                    nullSafe(r.getEmployee()),
                    r.getSprintFirstId(),
                    nullSafe(r.getSprintFirstName()),
                    r.getSprintLastLoggedId(),
                    nullSafe(r.getSprintLastLoggedName())
            );
            SummaryAcc acc = map.computeIfAbsent(key, k -> new SummaryAcc());

            BigDecimal first = nvl(r.getFirstEstimateHours());
            BigDecimal logged = nvl(r.getLoggedHours());

            if (first.compareTo(BigDecimal.ZERO) == 0) {
                acc.firstEqZero++;
            } else {
                if (logged.compareTo(first) <= 0) acc.loggedLeFirst++;
                else acc.loggedGtFirst++;
            }
        }

        List<SummaryRow> out = new ArrayList<>(map.size());
        for (Map.Entry<SummaryKey, SummaryAcc> e : map.entrySet()) {
            SummaryKey k = e.getKey();
            SummaryAcc a = e.getValue();
            out.add(new SummaryRow(
                    k.employee, k.sprintFirstId, k.sprintFirstName,
                    k.sprintLastLoggedId, k.sprintLastLoggedName,
                    a.loggedLeFirst, a.loggedGtFirst, a.firstEqZero
            ));
        }

        out.sort(Comparator
                .comparing((SummaryRow x) -> x.employee, Comparator.nullsFirst(String::compareTo))
                .thenComparing(x -> x.sprintFirstId, Comparator.nullsFirst(Long::compareTo))
                .thenComparing(x -> x.sprintLastLoggedId, Comparator.nullsFirst(Long::compareTo)));

        return out;
    }

    // Totals: employee (sum across all summary rows)
    private List<EmployeeTotalRow> aggregateTotalsByEmployee(List<SummaryRow> summaryRows) {
        Map<String, SummaryAcc> map = new LinkedHashMap<>();

        for (SummaryRow sr : summaryRows) {
            String emp = nullSafe(sr.employee);
            SummaryAcc acc = map.computeIfAbsent(emp, k -> new SummaryAcc());
            acc.loggedLeFirst += sr.loggedLeFirstCount;
            acc.loggedGtFirst += sr.loggedGtFirstCount;
            acc.firstEqZero += sr.firstEqZeroCount;
        }

        List<EmployeeTotalRow> out = new ArrayList<>(map.size());
        for (Map.Entry<String, SummaryAcc> e : map.entrySet()) {
            SummaryAcc a = e.getValue();
            out.add(new EmployeeTotalRow(e.getKey(), a.loggedLeFirst, a.loggedGtFirst, a.firstEqZero));
        }

        out.sort(Comparator.comparing(x -> x.employee, Comparator.nullsFirst(String::compareTo)));
        return out;
    }

    // Used sprints: distinct from details по sprint_first
    private List<SprintRow> extractUsedSprints(List<EffortReportRow> detailRows) {
        Map<Long, String> map = new LinkedHashMap<>();
        for (EffortReportRow r : detailRows) {
            Long id = r.getSprintFirstId();
            if (id == null) continue;
            map.putIfAbsent(id, nullSafe(r.getSprintFirstName()));
        }

        List<SprintRow> out = new ArrayList<>();
        for (Map.Entry<Long, String> e : map.entrySet()) {
            out.add(new SprintRow(e.getKey(), e.getValue()));
        }

        out.sort(Comparator.comparing(x -> x.sprintId));
        return out;
    }

    private record SummaryKey(String employee,
                              Long sprintFirstId, String sprintFirstName,
                              Long sprintLastLoggedId, String sprintLastLoggedName) {
    }

    private static class SummaryAcc {
        int loggedLeFirst = 0;
        int loggedGtFirst = 0;
        int firstEqZero = 0;
    }

    private static class SummaryRow {
        final String employee;

        final Long sprintFirstId;
        final String sprintFirstName;

        final Long sprintLastLoggedId;
        final String sprintLastLoggedName;

        final int loggedLeFirstCount;
        final int loggedGtFirstCount;
        final int firstEqZeroCount;

        SummaryRow(String employee,
                   Long sprintFirstId, String sprintFirstName,
                   Long sprintLastLoggedId, String sprintLastLoggedName,
                   int loggedLeFirstCount, int loggedGtFirstCount, int firstEqZeroCount) {
            this.employee = employee;
            this.sprintFirstId = sprintFirstId;
            this.sprintFirstName = sprintFirstName;
            this.sprintLastLoggedId = sprintLastLoggedId;
            this.sprintLastLoggedName = sprintLastLoggedName;
            this.loggedLeFirstCount = loggedLeFirstCount;
            this.loggedGtFirstCount = loggedGtFirstCount;
            this.firstEqZeroCount = firstEqZeroCount;
        }
    }

    private static class EmployeeTotalRow {
        final String employee;
        final int loggedLeFirstCount;
        final int loggedGtFirstCount;
        final int firstEqZeroCount;

        EmployeeTotalRow(String employee, int a, int b, int c) {
            this.employee = employee;
            this.loggedLeFirstCount = a;
            this.loggedGtFirstCount = b;
            this.firstEqZeroCount = c;
        }
    }

    private static class SprintRow {
        final Long sprintId;
        final String sprintName;

        SprintRow(Long sprintId, String sprintName) {
            this.sprintId = sprintId;
            this.sprintName = sprintName;
        }
    }

    // ===================== Helpers =====================

    private Row getOrCreateRow(Sheet sh, int rowIndex) {
        Row r = sh.getRow(rowIndex);
        return (r != null) ? r : sh.createRow(rowIndex);
    }

    private void setNumeric(Cell cell, BigDecimal v) {
        if (v == null) {
            cell.setBlank();
            return;
        }
        cell.setCellValue(v.doubleValue());
    }

    private BigDecimal nvl(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v;
    }

    private String nullSafe(String s) {
        return s == null ? "" : s;
    }

    private List<Long> parseSprintIds(String sprintIdsText) {
        if (!StringUtils.hasText(sprintIdsText)) return List.of();

        return Arrays.stream(sprintIdsText.trim().split("\\s+"))
                .filter(StringUtils::hasText)
                .map(Long::valueOf)
                .distinct()
                .toList();
    }
}

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
import java.math.RoundingMode;
import java.util.*;

@Service
@RequiredArgsConstructor
public class ExcelEffortReportServiceImpl implements ExcelEffortReportService {

    private final JiraSprintEmployeeEffortRepository repo;

    @Override
    public byte[] buildXlsx(String sprintIdsText) {
        // из БД приходит по одной строке на каждое списание (worklog).
        // Фильтр только один — сотрудник должен быть в employee с selectable = true.
        // По спринтам не фильтруем: таблица и так наполняется задачами переданных спринтов.
        List<EffortReportRow> rawRows = repo.getEffortReport();

        // 4.3: одна строка на (сотрудник + задача), часы просуммированы по issue_key
        List<IssueRow> issueRows = aggregateByEmployeeIssue(rawRows);

        // 4.2: строится на основе 4.3 — одна строка на (сотрудник + спринт)
        List<SummaryRow> summaryByEmployeeSprint = aggregateByEmployeeSprint(issueRows);

        // 4.1: строится на основе 4.2 — одна строка на сотрудника
        List<EmployeeTotalRow> totalsByEmployee = aggregateTotalsByEmployee(summaryByEmployeeSprint);

        // список спринтов считаем по sprint_first
        List<SprintRow> usedSprints = extractUsedSprints(issueRows);

        return toXlsxBytes(
                summaryByEmployeeSprint,
                issueRows,
                usedSprints,
                totalsByEmployee,
                sprintIdsText
        );
    }

    private byte[] toXlsxBytes(List<SummaryRow> summaryRows,
                               List<IssueRow> issueRows,
                               List<SprintRow> usedSprints,
                               List<EmployeeTotalRow> totalsByEmployee,
                               String sprintIdsTextOriginal) {

        try (Workbook wb = new XSSFWorkbook();
             ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            Font boldFont = wb.createFont();
            boldFont.setBold(true);

            Font orangeFont = wb.createFont();
            orangeFont.setColor(IndexedColors.ORANGE.getIndex());

            CellStyle headerStyle = wb.createCellStyle();
            headerStyle.setFont(boldFont);

            CellStyle orangeTextStyle = wb.createCellStyle();
            orangeTextStyle.setFont(orangeFont);

            // ===================== Sheet 1: Summary =====================
            Sheet s1 = wb.createSheet("4.2 Соотвествия оценке по спринтам");
            int r1 = 0;

            Row h1 = s1.createRow(r1++);
            h1.createCell(0).setCellValue("Сотрудник");
            h1.createCell(1).setCellValue("Спринт (first)");
            h1.createCell(2).setCellValue("Соответсвует оценке");
            h1.createCell(3).setCellValue("Несоответсвует оценке");
            h1.createCell(4).setCellValue("Без оценки разработки");
            h1.createCell(5).setCellValue("Незатрекано время");
            for (int i = 0; i <= 5; i++) {
                h1.getCell(i).setCellStyle(headerStyle);
            }

            for (SummaryRow sr : summaryRows) {
                Row x = s1.createRow(r1++);
                x.createCell(0).setCellValue(nullSafe(sr.employee));
                x.createCell(1).setCellValue(nullSafe(sr.sprintFirstName));
                x.createCell(2).setCellValue(sr.loggedLeFirstCount);
                x.createCell(3).setCellValue(sr.loggedGtFirstCount);
                x.createCell(4).setCellValue(sr.firstEqZeroCount);
                x.createCell(5).setCellValue(sr.zeroLoggedWithEstimateCount);
            }

            s1.setAutoFilter(new CellRangeAddress(h1.getRowNum(), h1.getRowNum(), 0, 5));
            for (int i = 0; i <= 5; i++) s1.autoSizeColumn(i);

            // ===================== Sheet 2: Details =====================
            Sheet s2 = wb.createSheet("4.3 Соответсвия оценке по задачам");
            int r2 = 0;

            Row h2 = s2.createRow(r2++);
            h2.createCell(0).setCellValue("Сотрудник");
            h2.createCell(1).setCellValue("Спринт (first)");
            h2.createCell(2).setCellValue("Спринт (last logged)");
            h2.createCell(3).setCellValue("Задача");
            h2.createCell(4).setCellValue("Эпик");
            h2.createCell(5).setCellValue("Оценка разработки");
            h2.createCell(6).setCellValue("Затрекано времени");
            h2.createCell(7).setCellValue("% от оценки");
            for (int i = 0; i <= 7; i++) {
                h2.getCell(i).setCellStyle(headerStyle);
            }

            int dataStartRow = r2;
            int estCellIndex = 5;
            int logCellIndex = 6;

            for (IssueRow row : issueRows) {
                Row x = s2.createRow(r2++);

                x.createCell(0).setCellValue(nullSafe(row.employee));
                x.createCell(1).setCellValue(nullSafe(row.sprintFirstName));
                x.createCell(2).setCellValue(nullSafe(row.sprintLastLoggedName));
                x.createCell(3).setCellValue(nullSafe(row.issueKey));
                x.createCell(4).setCellValue(nullSafe(row.epicKey));

                Cell estCell = x.createCell(estCellIndex);
                setNumeric(estCell, row.firstEstimateHours);

                Cell logCell = x.createCell(logCellIndex);
                setNumeric(logCell, row.loggedHours);

                Cell percentCell = x.createCell(7);
                setPercentFromEstimate(percentCell, row.firstEstimateHours, row.loggedHours);
                if (percentCell.getCellType() == CellType.NUMERIC && percentCell.getNumericCellValue() > 130d) {
                    percentCell.setCellStyle(orangeTextStyle);
                }
            }

            int dataEndRow = r2 - 1;
            if (!issueRows.isEmpty()) {
                applyConditionalFormattingForDetails(s2, dataStartRow, dataEndRow, estCellIndex, logCellIndex);
            }
            s2.setAutoFilter(new CellRangeAddress(h2.getRowNum(), h2.getRowNum(), 0, 7));
            for (int i = 0; i <= 7; i++) s2.autoSizeColumn(i);

            // ===================== Sheet 3: Info =====================
            Sheet s3 = wb.createSheet("4.1 Соответсвие оценке по сотрудникам");
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
            hs.createCell(8).setCellValue("Незатрекано время");
            for (int i = 0; i <= 8; i++) {
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
                row.createCell(8).setCellValue(t.zeroLoggedWithEstimateCount);
            }

            for (int i = 0; i <= 8; i++) s3.autoSizeColumn(i);
            s3.setAutoFilter(new CellRangeAddress(hs.getRowNum(), hs.getRowNum(), 4, 8));

            wb.setSheetOrder(s3.getSheetName(), 0);
            wb.setSheetOrder(s1.getSheetName(), 1);
            wb.setSheetOrder(s2.getSheetName(), 2);

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

        String orangeFormula = "AND(" + firstRef + ">0," + loggedRef + "=0)";
        ConditionalFormattingRule orangeRule = scf.createConditionalFormattingRule(orangeFormula);
        PatternFormatting orangeFill = orangeRule.createPatternFormatting();
        orangeFill.setFillForegroundColor(IndexedColors.LIGHT_ORANGE.getIndex());
        orangeFill.setFillBackgroundColor(IndexedColors.LIGHT_ORANGE.getIndex());
        orangeFill.setFillPattern(PatternFormatting.SOLID_FOREGROUND);

        String greenFormula = "AND(" + firstRef + "<>0," + loggedRef + "<=" + firstRef + "," + loggedRef + "<>0)";
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

        scf.addConditionalFormatting(bothRange, new ConditionalFormattingRule[]{orangeRule, greenRule, redRule, greyRule});
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
     * Лист 4.3: одна строка на (сотрудник + задача).
     * Все списания сотрудника по задаче суммируются в logged_hours, оценка берётся одна на задачу.
     */
    private List<IssueRow> aggregateByEmployeeIssue(List<EffortReportRow> rows) {
        Map<IssueKey, IssueAcc> map = new LinkedHashMap<>();

        for (EffortReportRow r : rows) {
            IssueKey key = new IssueKey(nullSafe(r.getEmployee()), nullSafe(r.getIssueKey()));
            IssueAcc acc = map.computeIfAbsent(key, k -> new IssueAcc());

            acc.logged = acc.logged.add(nvl(r.getLoggedHours()));

            // оценка одна на задачу — берём максимальную из строк списаний
            BigDecimal est = nvl(r.getFirstEstimateHours());
            if (est.compareTo(acc.firstEstimate) > 0) acc.firstEstimate = est;

            if (acc.sprintFirstId == null && r.getSprintFirstId() != null) {
                acc.sprintFirstId = r.getSprintFirstId();
                acc.sprintFirstName = nullSafe(r.getSprintFirstName());
            }
            // спринт последнего списания — с максимальным id
            if (r.getSprintLastLoggedId() != null
                    && (acc.sprintLastLoggedId == null || r.getSprintLastLoggedId() > acc.sprintLastLoggedId)) {
                acc.sprintLastLoggedId = r.getSprintLastLoggedId();
                acc.sprintLastLoggedName = nullSafe(r.getSprintLastLoggedName());
            }
            if (acc.epicKey == null && StringUtils.hasText(r.getEpicKey())) {
                acc.epicKey = r.getEpicKey();
            }
        }

        List<IssueRow> out = new ArrayList<>(map.size());
        for (Map.Entry<IssueKey, IssueAcc> e : map.entrySet()) {
            IssueKey k = e.getKey();
            IssueAcc a = e.getValue();
            out.add(new IssueRow(
                    k.employee, a.sprintFirstId, a.sprintFirstName,
                    a.sprintLastLoggedId, a.sprintLastLoggedName,
                    k.issueKey, a.epicKey, a.firstEstimate, a.logged
            ));
        }

        out.sort(Comparator
                .comparing((IssueRow x) -> x.employee, Comparator.nullsFirst(String::compareTo))
                .thenComparing(x -> x.sprintFirstId, Comparator.nullsFirst(Long::compareTo))
                .thenComparing(x -> x.issueKey, Comparator.nullsFirst(String::compareTo)));

        return out;
    }

    /**
     * Лист 4.2 — на основе 4.3: одна строка на (сотрудник + спринт), считаем задачи из 4.3.
     */
    private List<SummaryRow> aggregateByEmployeeSprint(List<IssueRow> issueRows) {
        Map<SummaryKey, SummaryAcc> map = new LinkedHashMap<>();

        for (IssueRow r : issueRows) {
            SummaryKey key = new SummaryKey(
                    nullSafe(r.employee),
                    r.sprintFirstId,
                    nullSafe(r.sprintFirstName)
            );
            SummaryAcc acc = map.computeIfAbsent(key, k -> new SummaryAcc());

            BigDecimal first = nvl(r.firstEstimateHours);
            BigDecimal logged = nvl(r.loggedHours);

            if (first.compareTo(BigDecimal.ZERO) == 0) {
                acc.firstEqZero++;
            } else {
                if (logged.compareTo(BigDecimal.ZERO) == 0) {
                    acc.zeroLoggedWithEstimate++;
                }
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
                    a.loggedLeFirst, a.loggedGtFirst, a.firstEqZero, a.zeroLoggedWithEstimate
            ));
        }

        out.sort(Comparator
                .comparing((SummaryRow x) -> x.employee, Comparator.nullsFirst(String::compareTo))
                .thenComparing(x -> x.sprintFirstId, Comparator.nullsFirst(Long::compareTo)));

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
            acc.zeroLoggedWithEstimate += sr.zeroLoggedWithEstimateCount;
        }

        List<EmployeeTotalRow> out = new ArrayList<>(map.size());
        for (Map.Entry<String, SummaryAcc> e : map.entrySet()) {
            SummaryAcc a = e.getValue();
            out.add(new EmployeeTotalRow(
                    e.getKey(),
                    a.loggedLeFirst,
                    a.loggedGtFirst,
                    a.firstEqZero,
                    a.zeroLoggedWithEstimate
            ));
        }

        out.sort(Comparator.comparing(x -> x.employee, Comparator.nullsFirst(String::compareTo)));
        return out;
    }

    // Used sprints: distinct from details по sprint_first
    private List<SprintRow> extractUsedSprints(List<IssueRow> issueRows) {
        Map<Long, String> map = new LinkedHashMap<>();
        for (IssueRow r : issueRows) {
            Long id = r.sprintFirstId;
            if (id == null) continue;
            map.putIfAbsent(id, nullSafe(r.sprintFirstName));
        }

        List<SprintRow> out = new ArrayList<>();
        for (Map.Entry<Long, String> e : map.entrySet()) {
            out.add(new SprintRow(e.getKey(), e.getValue()));
        }

        out.sort(Comparator.comparing(x -> x.sprintId));
        return out;
    }

    /** Ключ строки листа 4.3: сотрудник + задача. */
    private record IssueKey(String employee, String issueKey) {
    }

    private static class IssueAcc {
        BigDecimal logged = BigDecimal.ZERO;
        BigDecimal firstEstimate = BigDecimal.ZERO;
        Long sprintFirstId;
        String sprintFirstName;
        Long sprintLastLoggedId;
        String sprintLastLoggedName;
        String epicKey;
    }

    /** Строка листа 4.3: сотрудник + задача, часы просуммированы по issue_key. */
    private static class IssueRow {
        final String employee;
        final Long sprintFirstId;
        final String sprintFirstName;
        final Long sprintLastLoggedId;
        final String sprintLastLoggedName;
        final String issueKey;
        final String epicKey;
        final BigDecimal firstEstimateHours;
        final BigDecimal loggedHours;

        IssueRow(String employee,
                 Long sprintFirstId, String sprintFirstName,
                 Long sprintLastLoggedId, String sprintLastLoggedName,
                 String issueKey, String epicKey,
                 BigDecimal firstEstimateHours, BigDecimal loggedHours) {
            this.employee = employee;
            this.sprintFirstId = sprintFirstId;
            this.sprintFirstName = sprintFirstName;
            this.sprintLastLoggedId = sprintLastLoggedId;
            this.sprintLastLoggedName = sprintLastLoggedName;
            this.issueKey = issueKey;
            this.epicKey = epicKey;
            this.firstEstimateHours = firstEstimateHours;
            this.loggedHours = loggedHours;
        }
    }

    private record SummaryKey(String employee,
                              Long sprintFirstId, String sprintFirstName) {
    }

    private static class SummaryAcc {
        int loggedLeFirst = 0;
        int loggedGtFirst = 0;
        int firstEqZero = 0;
        int zeroLoggedWithEstimate = 0;
    }

    private static class SummaryRow {
        final String employee;

        final Long sprintFirstId;
        final String sprintFirstName;

        final int loggedLeFirstCount;
        final int loggedGtFirstCount;
        final int firstEqZeroCount;
        final int zeroLoggedWithEstimateCount;

        SummaryRow(String employee,
                   Long sprintFirstId, String sprintFirstName,
                   int loggedLeFirstCount, int loggedGtFirstCount, int firstEqZeroCount,
                   int zeroLoggedWithEstimateCount) {
            this.employee = employee;
            this.sprintFirstId = sprintFirstId;
            this.sprintFirstName = sprintFirstName;
            this.loggedLeFirstCount = loggedLeFirstCount;
            this.loggedGtFirstCount = loggedGtFirstCount;
            this.firstEqZeroCount = firstEqZeroCount;
            this.zeroLoggedWithEstimateCount = zeroLoggedWithEstimateCount;
        }
    }

    private static class EmployeeTotalRow {
        final String employee;
        final int loggedLeFirstCount;
        final int loggedGtFirstCount;
        final int firstEqZeroCount;
        final int zeroLoggedWithEstimateCount;

        EmployeeTotalRow(String employee, int a, int b, int c, int d) {
            this.employee = employee;
            this.loggedLeFirstCount = a;
            this.loggedGtFirstCount = b;
            this.firstEqZeroCount = c;
            this.zeroLoggedWithEstimateCount = d;
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

    private void setPercentFromEstimate(Cell cell, BigDecimal estimate, BigDecimal logged) {
        BigDecimal safeEstimate = nvl(estimate);
        BigDecimal safeLogged = nvl(logged);
        if (safeEstimate.compareTo(BigDecimal.ZERO) == 0 && safeLogged.compareTo(BigDecimal.ZERO) == 0) {
            cell.setCellValue(0);
            return;
        }

        if (safeEstimate.compareTo(BigDecimal.ZERO) <= 0) {
            cell.setBlank();
            return;
        }

        BigDecimal percent = safeLogged
                .multiply(BigDecimal.valueOf(100))
                .divide(safeEstimate, 2, RoundingMode.HALF_UP);
        cell.setCellValue(percent.doubleValue());
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

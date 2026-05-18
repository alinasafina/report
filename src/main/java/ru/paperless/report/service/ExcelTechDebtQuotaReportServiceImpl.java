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
import ru.paperless.report.dto.TechDebtQuotaDetailRow;
import ru.paperless.report.dto.TechDebtQuotaSummaryRow;
import ru.paperless.report.repository.JiraSprintEmployeeEffortRepository;

import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ExcelTechDebtQuotaReportServiceImpl implements ExcelTechDebtQuotaReportService {

    private static final String LABEL = "backend_techdebt_quota";

    private final JiraSprintEmployeeEffortRepository effortRepository;

    @Override
    public byte[] buildXlsx(String sprintIdsText) {
        List<Long> sprintIds = parseSprintIds(sprintIdsText);
        Long[] sprintArr = sprintIds.toArray(Long[]::new);
        boolean sprintsEmpty = sprintArr.length == 0;

        List<TechDebtQuotaSummaryRow> summaryRows = effortRepository.getTechDebtQuotaSummary(sprintArr, sprintsEmpty, LABEL);
        List<TechDebtQuotaDetailRow> detailRows = effortRepository.getTechDebtQuotaDetails(sprintArr, sprintsEmpty, LABEL);

        return toXlsxBytes(summaryRows, detailRows, sprintIdsText);
    }

    private byte[] toXlsxBytes(List<TechDebtQuotaSummaryRow> summaryRows,
                               List<TechDebtQuotaDetailRow> detailRows,
                               String sprintIdsText) {
        try (Workbook wb = new XSSFWorkbook();
             ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            Font boldFont = wb.createFont();
            boldFont.setBold(true);

            CellStyle headerStyle = wb.createCellStyle();
            headerStyle.setFont(boldFont);

            Sheet s1 = wb.createSheet("5.1 TechDebt quota по спринтам");
            int r1 = 0;

            Row m1 = s1.createRow(r1++);
            m1.createCell(0).setCellValue("Метка");
            m1.createCell(1).setCellValue(LABEL);

            Row m2 = s1.createRow(r1++);
            m2.createCell(0).setCellValue("Спринты");
            m2.createCell(1).setCellValue(StringUtils.hasText(sprintIdsText) ? sprintIdsText : "ALL");

            r1++;

            Row h1 = s1.createRow(r1++);
            h1.createCell(0).setCellValue("Спринт");
            h1.createCell(1).setCellValue("Сумма оценки, ч");
            h1.createCell(2).setCellValue("Количество задач");
            h1.createCell(3).setCellValue("Количество задач перешедших в Решена");
            applyHeaderStyle(h1, headerStyle, 4);

            for (TechDebtQuotaSummaryRow row : summaryRows) {
                Row x = s1.createRow(r1++);
                x.createCell(0).setCellValue(nullSafe(row.getSprintName()));
                x.createCell(1).setCellValue(toDouble(row.getTotalEstimateHours()));
                x.createCell(2).setCellValue(row.getTaskCount() == null ? 0 : row.getTaskCount());
                x.createCell(3).setCellValue(row.getResolvedTaskCount() == null ? 0 : row.getResolvedTaskCount());
            }

            s1.setAutoFilter(new CellRangeAddress(h1.getRowNum(), h1.getRowNum(), 0, 3));
            autoSizeColumns(s1, 4);

            Sheet s2 = wb.createSheet("5.2 TechDebt quota по задачам");
            int r2 = 0;

            Row d1 = s2.createRow(r2++);
            d1.createCell(0).setCellValue("Метка");
            d1.createCell(1).setCellValue(LABEL);

            Row d2 = s2.createRow(r2++);
            d2.createCell(0).setCellValue("Спринты");
            d2.createCell(1).setCellValue(StringUtils.hasText(sprintIdsText) ? sprintIdsText : "ALL");

            r2++;

            Row h2 = s2.createRow(r2++);
            h2.createCell(0).setCellValue("Спринт");
            h2.createCell(1).setCellValue("Номер задачи");
            h2.createCell(2).setCellValue("Название задачи");
            h2.createCell(3).setCellValue("Оценка разработки, ч");
            h2.createCell(4).setCellValue("Статус на конец спринта");
            applyHeaderStyle(h2, headerStyle, 5);

            for (TechDebtQuotaDetailRow row : detailRows) {
                Row x = s2.createRow(r2++);
                x.createCell(0).setCellValue(nullSafe(row.getSprintName()));
                x.createCell(1).setCellValue(nullSafe(row.getIssueKey()));
                x.createCell(2).setCellValue(nullSafe(row.getIssueSummary()));
                x.createCell(3).setCellValue(toDouble(row.getEstimateHours()));
                x.createCell(4).setCellValue(nullSafe(row.getStatusAtSprintEnd()));
            }

            s2.setAutoFilter(new CellRangeAddress(h2.getRowNum(), h2.getRowNum(), 0, 4));
            autoSizeColumns(s2, 5);
            s2.setColumnWidth(2, 60 * 256);

            wb.write(baos);
            return baos.toByteArray();
        } catch (Exception e) {
            throw new RuntimeException("Не удалось сформировать XLSX отчет по backend techdebt quota", e);
        }
    }

    private List<Long> parseSprintIds(String text) {
        if (!StringUtils.hasText(text)) {
            return List.of();
        }
        return Arrays.stream(text.trim().split("\\s+"))
                .filter(StringUtils::hasText)
                .map(Long::valueOf)
                .distinct()
                .collect(Collectors.toList());
    }

    private void applyHeaderStyle(Row row, CellStyle style, int count) {
        for (int i = 0; i < count; i++) {
            row.getCell(i).setCellStyle(style);
        }
    }

    private void autoSizeColumns(Sheet sheet, int count) {
        for (int i = 0; i < count; i++) {
            sheet.autoSizeColumn(i);
        }
    }

    private double toDouble(BigDecimal value) {
        return value == null ? 0d : value.doubleValue();
    }

    private String nullSafe(String value) {
        return value == null ? "" : value;
    }
}

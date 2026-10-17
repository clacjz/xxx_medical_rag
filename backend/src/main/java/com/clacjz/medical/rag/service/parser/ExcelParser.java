package com.clacjz.medical.rag.service.parser;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.hssf.usermodel.HSSFWorkbook;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class ExcelParser implements DocumentParser {

    private final TextCleaner textCleaner;

    @Override
    public String parse(InputStream inputStream) {
        StringBuilder allText = new StringBuilder();

        try {
            // 根据文件格式选择合适的 Workbook
            Workbook workbook;
            String fileFormat = detectFormat(inputStream);
            inputStream.reset(); // 重置流以供后续读取

            if ("xlsx".equalsIgnoreCase(fileFormat)) {
                workbook = new XSSFWorkbook(inputStream);
            } else {
                workbook = new HSSFWorkbook(inputStream);
            }

            int sheetCount = workbook.getNumberOfSheets();
            log.debug("Excel workbook has {} sheets", sheetCount);

            for (int sheetIdx = 0; sheetIdx < sheetCount; sheetIdx++) {
                try {
                    Sheet sheet = workbook.getSheetAt(sheetIdx);
                    String sheetName = workbook.getSheetName(sheetIdx);
                    if (sheetName != null && !sheetName.isBlank()) {
                        allText.append("## Sheet: ").append(sheetName).append("\n");
                    }

                    int lastRowNum = sheet.getLastRowNum();
                    for (int rowIdx = 0; rowIdx <= lastRowNum; rowIdx++) {
                        Row row = sheet.getRow(rowIdx);
                        if (row == null) continue;

                        List<String> rowCells = new ArrayList<>();
                        short lastCellNum = row.getLastCellNum();

                        for (int cellIdx = 0; cellIdx < lastCellNum; cellIdx++) {
                            Cell cell = row.getCell(cellIdx);
                            String cellValue = getCellValueAsString(cell);
                            if (cellValue != null && !cellValue.isBlank()) {
                                rowCells.add(cellValue);
                            }
                        }

                        if (!rowCells.isEmpty()) {
                            allText.append(String.join(" | ", rowCells)).append("\n");
                        }
                    }
                    allText.append("\n");
                } catch (Exception e) {
                    log.warn("Failed to extract sheet {}: {}", sheetIdx, e);
                }
            }

            workbook.close();
            String result = allText.toString().trim();
            log.info("Excel document parsed successfully: {} chars from {} sheets",
                    result.length(), sheetCount);

            return result;

        } catch (Exception e) {
            log.error("Error parsing Excel file", e);
            throw new RuntimeException("Failed to parse Excel file: " + e.getMessage(), e);
        }
    }

    private String detectFormat(InputStream inputStream) {
        try {
            byte[] header = new byte[4];
            inputStream.mark(4);
            inputStream.read(header);
            inputStream.reset();

            // XLSX 以 PK 开头（ZIP 格式），XLS 以 D0 CF 开头
            if (header[0] == 0x50 && header[1] == 0x4B) {
                return "xlsx";
            } else {
                return "xls";
            }
        } catch (Exception e) {
            return "xls"; // 默认按旧格式处理
        }
    }

    private String getCellValueAsString(Cell cell) {
        if (cell == null) return null;

        return switch (cell.getCellType()) {
            case STRING -> cell.getStringCellValue();
            case NUMERIC -> {
                if (DateUtil.isCellDateFormatted(cell)) {
                    yield cell.getLocalDateTimeCellValue().toString();
                } else {
                    double numValue = cell.getNumericCellValue();
                    if (numValue == Math.floor(numValue)) {
                        yield String.valueOf((long) numValue);
                    }
                    yield String.valueOf(numValue);
                }
            }
            case BOOLEAN -> String.valueOf(cell.getBooleanCellValue());
            case FORMULA -> {
                try {
                    yield cell.getStringCellValue();
                } catch (Exception e) {
                    yield String.valueOf(cell.getNumericCellValue());
                }
            }
            case BLANK -> null;
            default -> null;
        };
    }

    @Override
    public String[] getSupportedExtensions() {
        return new String[]{"xlsx", "xls"};
    }
}

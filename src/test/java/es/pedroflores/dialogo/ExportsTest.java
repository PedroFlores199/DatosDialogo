package es.pedroflores.dialogo;

import static org.junit.jupiter.api.Assertions.*;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;

class ExportsTest {
  private ReadonlyQueries.QueryResult sample() {
    var rows = new ArrayList<Map<String, Object>>();
    for (int i = 0; i < 200; i++)
      rows.add(
          Map.of(
              "provincia",
              "Almería",
              "noches",
              5303084 + i,
              "año",
              2025,
              "texto",
              "=HYPERLINK(\"https://example.invalid\")",
              "nota",
              "Ocupación · años · información pública",
              "dato",
              i));
    return new ReadonlyQueries.QueryResult(
        "sample",
        "SELECT * FROM fact_hotel",
        List.of("provincia", "noches", "año", "texto", "nota", "dato"),
        rows,
        true,
        3.);
  }

  @Test
  void excel_keeps_values_numeric_and_untrusted_strings_are_never_formulas() throws Exception {
    byte[] bytes = Exports.excel("Pernoctaciones de Almería", sample());
    try (var book = new XSSFWorkbook(new ByteArrayInputStream(bytes))) {
      var result = book.getSheet("Resultado");
      assertEquals(201, result.getPhysicalNumberOfRows());
      assertEquals(CellType.NUMERIC, result.getRow(1).getCell(1).getCellType());
      assertEquals(5303084., result.getRow(1).getCell(1).getNumericCellValue());
      assertEquals(CellType.STRING, result.getRow(1).getCell(3).getCellType());
      assertTrue(result.getRow(1).getCell(3).getStringCellValue().startsWith("=HYPERLINK"));
      assertEquals(
          "Sí: primeras 200 filas", book.getSheetAt(1).getRow(5).getCell(1).getStringCellValue());
    }
  }

  @Test
  void pdf_paginates_wide_tables_and_preserves_spanish_text_and_every_row() throws Exception {
    byte[] bytes = Exports.pdf("Pernoctaciones de Almería en 2025", sample());
    try (var document = Loader.loadPDF(bytes)) {
      assertTrue(document.getNumberOfPages() > 3);
      String text = new PDFTextStripper().getText(document);
      assertTrue(text.contains("Almería"));
      assertTrue(text.replace(".", "").contains("5303283"));
      assertTrue(text.contains("Dato") || text.contains("dato"));
      assertTrue(text.contains("límite de 200 alcanzado"));
    }
    String report = System.getenv("DIALOGO_EXPORT_REPORT");
    if (report != null) Files.write(Path.of(report), bytes);
  }
}

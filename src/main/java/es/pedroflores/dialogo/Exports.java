package es.pedroflores.dialogo;

import java.awt.Color;
import java.io.*;
import java.time.LocalDate;
import java.util.*;
import org.apache.pdfbox.pdmodel.*;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType0Font;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

public final class Exports {
  private Exports() {}

  public static byte[] excel(String question, ReadonlyQueries.QueryResult result)
      throws IOException {
    try (var book = new XSSFWorkbook();
        var output = new ByteArrayOutputStream()) {
      var sheet = book.createSheet("Resultado");
      var header = book.createCellStyle();
      header.setFillForegroundColor(IndexedColors.DARK_GREEN.getIndex());
      header.setFillPattern(FillPatternType.SOLID_FOREGROUND);
      var font = book.createFont();
      font.setColor(IndexedColors.WHITE.getIndex());
      font.setBold(true);
      header.setFont(font);
      var numeric = book.createCellStyle();
      numeric.setDataFormat(book.createDataFormat().getFormat("#,##0.########"));
      var row = sheet.createRow(0);
      for (int c = 0; c < result.columns().size(); c++) {
        var cell = row.createCell(c);
        cell.setCellValue(result.columns().get(c));
        cell.setCellStyle(header);
      }
      for (int r = 0; r < result.rows().size(); r++) {
        row = sheet.createRow(r + 1);
        for (int c = 0; c < result.columns().size(); c++) {
          Object value = result.rows().get(r).get(result.columns().get(c));
          var cell = row.createCell(c);
          if (value instanceof Number number) {
            cell.setCellValue(number.doubleValue());
            if (Math.abs(number.doubleValue()) >= 10000
                || number.doubleValue() != Math.rint(number.doubleValue()))
              cell.setCellStyle(numeric);
          } else if (value instanceof Boolean bool) cell.setCellValue(bool);
          else if (value != null) cell.setCellValue(value.toString());
        }
      }
      sheet.createFreezePane(0, 1);
      sheet.setAutoFilter(
          new CellRangeAddress(0, result.rows().size(), 0, result.columns().size() - 1));
      for (int c = 0; c < result.columns().size(); c++) {
        sheet.autoSizeColumn(c);
        sheet.setColumnWidth(
            c, Math.max(18 * 256, Math.min(45 * 256, sheet.getColumnWidth(c) + 512)));
      }
      var metadata = book.createSheet("Consulta y fuente");
      String[][] info = {
        {"Pregunta", question},
        {"SQL ejecutado", result.sql()},
        {"Fuente", "INE / TurismoLakehouse. Captura y procedencia en data/provenance.json."},
        {"Exportado", LocalDate.now().toString()},
        {"Filas", String.valueOf(result.rows().size())},
        {"Límite alcanzado", result.truncated() ? "Sí: primeras 200 filas" : "No"},
        {
          "Aviso",
          "2026 es parcial. Gasto y países solo existen para Andalucía. Las previsiones no son"
              + " observaciones."
        }
      };
      for (int r = 0; r < info.length; r++) {
        var line = metadata.createRow(r);
        line.createCell(0).setCellValue(info[r][0]);
        line.createCell(1).setCellValue(info[r][1]);
      }
      metadata.setColumnWidth(0, 24 * 256);
      metadata.setColumnWidth(1, 100 * 256);
      book.write(output);
      return output.toByteArray();
    }
  }

  public static byte[] pdf(String question, ReadonlyQueries.QueryResult result) throws IOException {
    try (var document = new PDDocument();
        var output = new ByteArrayOutputStream();
        var input = Exports.class.getResourceAsStream("/fonts/DejaVuSans.ttf")) {
      var font = PDType0Font.load(document, Objects.requireNonNull(input));
      var writer = new Pages(document, font);
      writer.start("Consulta y procedencia");
      writer.paragraph(question, 12, 742);
      writer.paragraph(
          "INE / TurismoLakehouse · "
              + LocalDate.now()
              + " · "
              + result.rows().size()
              + " filas"
              + (result.truncated() ? " (límite de 200 alcanzado)" : ""),
          10,
          742);
      writer.paragraph(
          "2026 es parcial. Gasto y países solo existen para Andalucía. Las previsiones son"
              + " orientativas.",
          10,
          742);
      writer.paragraph("SQL ejecutado", 12, 742);
      writer.paragraph(result.sql(), 9, 742);
      if (result.columns().size() > 5)
        writer.paragraph(
            "Las tablas se dividen en grupos de hasta cinco columnas para facilitar la lectura. Los"
                + " textos de más de 400 caracteres se abrevian aquí; Excel conserva el contenido"
                + " completo.",
            9,
            742);
      int bands = (result.columns().size() + 4) / 5;
      for (int band = 0; band < bands; band++) {
        var columns =
            result.columns().subList(band * 5, Math.min(result.columns().size(), band * 5 + 5));
        String section =
            "Resultado · columnas " + (band * 5 + 1) + " a " + (band * 5 + columns.size());
        if (band == 0) writer.section(section);
        else writer.start(section);
        writer.tableHeader(columns);
        for (var row : result.rows())
          writer.row(columns.stream().map(c -> display(row.get(c))).toList(), columns);
        if (result.rows().isEmpty())
          writer.paragraph("No hay observaciones para esta selección.", 11, 742);
      }
      writer.close();
      document.save(output);
      return output.toByteArray();
    }
  }

  private static String display(Object value) {
    if (value instanceof Number number) {
      var format = java.text.NumberFormat.getNumberInstance(Locale.forLanguageTag("es-ES"));
      format.setMaximumFractionDigits(8);
      format.setGroupingUsed(Math.abs(number.doubleValue()) >= 10000);
      return format.format(number);
    }
    return Objects.toString(value, "Sin dato");
  }

  private static final class Pages implements AutoCloseable {
    private final PDDocument document;
    private final PDType0Font font;
    private PDPageContentStream stream;
    private float y;
    private String title;

    Pages(PDDocument document, PDType0Font font) {
      this.document = document;
      this.font = font;
    }

    void start(String section) throws IOException {
      if (stream != null) stream.close();
      title = section;
      var page = new PDPage(new PDRectangle(PDRectangle.A4.getHeight(), PDRectangle.A4.getWidth()));
      document.addPage(page);
      stream = new PDPageContentStream(document, page);
      stream.setNonStrokingColor(new Color(23, 84, 31));
      stream.addRect(0, 540, 842, 55);
      stream.fill();
      text("DatosDialogo", 40, 560, 19, Color.WHITE);
      text(section, 40, 513, 12, new Color(23, 84, 31));
      text("INE · Solo lectura · Página " + document.getNumberOfPages(), 40, 25, 8, Color.GRAY);
      y = 488;
    }

    void paragraph(String value, int size, float width) throws IOException {
      for (String line : wrap(value, size, width)) {
        if (y < size + 50) start(title + " (continuación)");
        text(line, 40, y, size, new Color(35, 42, 40));
        y -= size + 5;
      }
      y -= 10;
    }

    void section(String section) throws IOException {
      title = section;
      if (y < 130) start(section);
      else {
        y -= 8;
        text(section, 40, y, 12, new Color(23, 84, 31));
        y -= 24;
      }
    }

    void tableHeader(List<String> columns) throws IOException {
      drawRow(columns, true);
    }

    void row(List<String> values, List<String> columns) throws IOException {
      float height = rowHeight(values);
      if (y - height < 52) {
        start(title);
        tableHeader(columns);
      }
      drawRow(values, false);
    }

    float rowHeight(List<String> values) throws IOException {
      float width = 742f / values.size();
      int count = 1;
      for (String value : values)
        count = Math.max(count, wrap(shortened(value), 9, width - 14).size());
      return count * 13 + 14;
    }

    void drawRow(List<String> values, boolean header) throws IOException {
      float width = 742f / values.size(), height = rowHeight(values);
      stream.setNonStrokingColor(header ? new Color(229, 237, 229) : new Color(248, 249, 248));
      stream.addRect(40, y - height, 742, height);
      stream.fill();
      for (int c = 0; c < values.size(); c++) {
        float baseline = y - 16;
        for (String line : wrap(shortened(values.get(c)), 9, width - 14)) {
          text(line, 47 + c * width, baseline, 9, new Color(35, 42, 40));
          baseline -= 13;
        }
      }
      y -= height + 2;
    }

    String shortened(String value) {
      return value.length() > 400 ? value.substring(0, 397) + "..." : value;
    }

    List<String> wrap(String value, int size, float width) throws IOException {
      var lines = new ArrayList<String>();
      var line = new StringBuilder();
      for (int point : value.replaceAll("[\\p{Cntrl}]", " ").codePoints().toArray()) {
        String next = new String(Character.toChars(point));
        if (!font.hasGlyph(point)) next = "?";
        if (font.getStringWidth(line + next) / 1000 * size > width && !line.isEmpty()) {
          lines.add(line.toString());
          line.setLength(0);
        }
        line.append(next);
      }
      if (!line.isEmpty()) lines.add(line.toString());
      if (lines.isEmpty()) lines.add("");
      return lines;
    }

    void text(String text, float x, float baseline, int size, Color color) throws IOException {
      stream.beginText();
      stream.setFont(font, size);
      stream.setNonStrokingColor(color);
      stream.newLineAtOffset(x, baseline);
      stream.showText(text);
      stream.endText();
    }

    public void close() throws IOException {
      if (stream != null) stream.close();
    }
  }
}

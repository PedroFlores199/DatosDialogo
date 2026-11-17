package es.pedroflores.dialogo;

import java.util.*;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.annotation.Tool;

public class QueryTools {
  private final ReadonlyQueries queries;
  private final SchemaCatalog catalog;

  public QueryTools(ReadonlyQueries queries, SchemaCatalog catalog) {
    this.queries = queries;
    this.catalog = catalog;
  }

  @Tool(description = "Listar las tablas disponibles con su fuente, columnas y nivel de detalle")
  public List<Map<String, String>> listTables(ToolContext context) {
    execution(context).steps.add("Listado del catálogo");
    return catalog.descriptions();
  }

  @Tool(
      description =
          "Consultar las columnas, relaciones, fechas y grano real de una tabla del catálogo antes"
              + " de escribir SQL")
  public String getSchema(String table, ToolContext context) {
    var execution = execution(context);
    execution.steps.add("Esquema: " + table);
    return catalog.get(table);
  }

  @Tool(
      description =
          "Ejecutar UNA consulta SELECT PostgreSQL sobre datos del catálogo. Solo lectura, máximo"
              + " 200 filas, funciones y tablas autorizadas. Si falla puede corregirse una vez."
              + " Devuelve los datos reales; nunca inventar resultados.")
  public Map<String, Object> runReadonlyQuery(String sql, ToolContext context) {
    var execution = execution(context);
    if (++execution.attempts > 2)
      return Map.of(
          "status", "limit_reached", "message", "Ya se ha utilizado el intento de corrección");
    try {
      execution.steps.add(
          execution.attempts == 1 ? "Consulta de solo lectura" : "Corrección de la consulta");
      SemanticRules.validate(execution.question, sql);
      execution.result = queries.execute(sql);
      return Map.of(
          "status",
          "ok",
          "columns",
          execution.result.columns(),
          "rows",
          execution.result.rows().stream().limit(8).toList(),
          "row_count",
          execution.result.rows().size(),
          "truncated",
          execution.result.truncated());
    } catch (Exception exception) {
      String message =
          Objects.toString(exception.getMessage(), "Error de consulta")
              .replaceAll("(?i)jdbc:[^ ]+", "[conexión]");
      execution.errors.add(message.substring(0, Math.min(message.length(), 350)));
      return Map.of(
          "status",
          "invalid_query",
          "message",
          execution.errors.getLast(),
          "remaining_attempts",
          Math.max(0, 2 - execution.attempts));
    }
  }

  private Execution execution(ToolContext context) {
    return (Execution) context.getContext().get("execution");
  }

  public static class Execution {
    public String question = "";
    public int attempts;
    public ReadonlyQueries.QueryResult result;
    public final List<String> steps = new ArrayList<>();
    public final List<String> errors = new ArrayList<>();
  }
}

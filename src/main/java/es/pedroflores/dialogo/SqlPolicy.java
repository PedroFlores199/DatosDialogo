package es.pedroflores.dialogo;

import java.lang.reflect.Array;
import java.lang.reflect.Modifier;
import java.util.*;
import net.sf.jsqlparser.expression.AnalyticExpression;
import net.sf.jsqlparser.expression.CastExpression;
import net.sf.jsqlparser.expression.Function;
import net.sf.jsqlparser.parser.CCJSqlParserUtil;
import net.sf.jsqlparser.schema.Table;
import net.sf.jsqlparser.statement.Statement;
import net.sf.jsqlparser.statement.select.PlainSelect;
import net.sf.jsqlparser.statement.select.Select;
import net.sf.jsqlparser.statement.select.TableFunction;
import net.sf.jsqlparser.util.TablesNamesFinder;

public final class SqlPolicy {
  private static final Set<String> TABLES =
      Set.of(
          "dim_province",
          "dim_date",
          "fact_hotel",
          "fact_markets_annual",
          "fact_region_monthly",
          "fact_housing",
          "fact_forecast");
  private static final Set<String> FUNCTIONS =
      Set.of(
          "sum",
          "avg",
          "count",
          "min",
          "max",
          "round",
          "coalesce",
          "nullif",
          "date_trunc",
          "date_part",
          "extract",
          "to_char",
          "abs",
          "ceil",
          "ceiling",
          "floor",
          "greatest",
          "least",
          "lower",
          "upper",
          "lag",
          "lead",
          "rank",
          "dense_rank",
          "row_number",
          "sqrt",
          "power",
          "stddev_pop",
          "stddev_samp",
          "var_pop",
          "var_samp",
          "corr");
  private static final Set<String> TYPES =
      Set.of(
          "numeric",
          "decimal",
          "double precision",
          "real",
          "float",
          "float8",
          "int",
          "integer",
          "bigint",
          "int8",
          "text",
          "varchar",
          "date",
          "timestamp",
          "boolean");

  public String validate(String text) {
    if (text == null || text.isBlank() || text.length() > 8000)
      throw new IllegalArgumentException("La consulta debe tener entre 1 y 8000 caracteres");
    try {
      var statements = CCJSqlParserUtil.parseStatements(text, parser -> parser.withTimeOut(2000));
      if (statements.size() != 1
          || !(statements.getStatements().getFirst() instanceof Select select))
        throw new IllegalArgumentException("Solo se permite una consulta SELECT");
      Set<String> tables = new TablesNamesFinder().getTables((Statement) select);
      if (tables.isEmpty())
        throw new IllegalArgumentException(
            "La consulta debe utilizar datos analíticos disponibles");
      for (String table : tables) {
        String name = table.replace("\"", "").toLowerCase(Locale.ROOT);
        if (name.startsWith("analytics.")) name = name.substring(10);
        if (!TABLES.contains(name))
          throw new IllegalArgumentException("La tabla no pertenece al catálogo autorizado");
      }
      Set<String> normalized = new HashSet<>();
      for (String table : tables)
        normalized.add(table.replace("\"", "").toLowerCase(Locale.ROOT).replace("analytics.", ""));
      if (!Collections.disjoint(normalized, Set.of("fact_markets_annual", "fact_region_monthly"))
          && !Collections.disjoint(
              normalized, Set.of("fact_hotel", "fact_housing", "fact_forecast", "dim_province")))
        throw new IllegalArgumentException(
            "No se permite mezclar hechos regionales con datos provinciales en esta demo");
      inspect(select, Collections.newSetFromMap(new IdentityHashMap<>()));
      return select.toString();
    } catch (IllegalArgumentException exception) {
      throw exception;
    } catch (Exception exception) {
      throw new IllegalArgumentException("No se ha podido validar la consulta SQL", exception);
    }
  }

  private void inspect(Object node, Set<Object> visited) {
    if (node == null || !visited.add(node)) return;
    if (visited.size() > 6000)
      throw new IllegalArgumentException("La consulta es demasiado compleja");
    if (node instanceof Iterable<?> items) {
      for (Object item : items) inspect(item, visited);
      return;
    }
    if (node.getClass().isArray()) {
      for (int i = 0; i < Array.getLength(node); i++) inspect(Array.get(node, i), visited);
      return;
    }
    String packageName = node.getClass().getPackageName();
    if (!packageName.startsWith("net.sf.jsqlparser.statement")
        && !packageName.startsWith("net.sf.jsqlparser.expression")
        && !packageName.startsWith("net.sf.jsqlparser.schema")) return;
    if (node instanceof Statement && !(node instanceof Select))
      throw new IllegalArgumentException(
          "No se admiten instrucciones de escritura, tampoco dentro de CTE");
    if (node instanceof Function function) checkFunction(function.getName());
    if (node instanceof AnalyticExpression expression) checkFunction(expression.getName());
    if (node instanceof TableFunction)
      throw new IllegalArgumentException("No se admiten funciones como fuente de filas");
    if (node instanceof Table table
        && table.getSchemaName() != null
        && !table.getSchemaName().replace("\"", "").equalsIgnoreCase("analytics"))
      throw new IllegalArgumentException("El esquema no está autorizado");
    if (node instanceof CastExpression cast
        && !TYPES.contains(cast.getColDataType().getDataType().toLowerCase(Locale.ROOT)))
      throw new IllegalArgumentException("Conversión de tipo no autorizada");
    if (node instanceof PlainSelect plain
        && (plain.getIntoTables() != null && !plain.getIntoTables().isEmpty()
            || plain.getForMode() != null))
      throw new IllegalArgumentException("No se permite SELECT INTO ni bloqueo de filas");
    for (var method : node.getClass().getMethods()) {
      if (!Modifier.isPublic(method.getModifiers())
          || method.getParameterCount() != 0
          || !method.getName().startsWith("get")
          || Set.of("getClass", "getASTNode").contains(method.getName())) continue;
      Object child;
      try {
        child = method.invoke(node);
      } catch (ReflectiveOperationException | ClassCastException exception) {
        continue;
      }
      inspect(child, visited);
    }
  }

  private void checkFunction(String name) {
    if (name == null || !FUNCTIONS.contains(name.replace("\"", "").toLowerCase(Locale.ROOT)))
      throw new IllegalArgumentException("La función SQL no está autorizada");
  }
}

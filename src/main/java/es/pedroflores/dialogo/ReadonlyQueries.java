package es.pedroflores.dialogo;

import java.sql.*;
import java.util.*;
import javax.sql.DataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class ReadonlyQueries {
  private static final Logger LOG = LoggerFactory.getLogger(ReadonlyQueries.class);
  private final DataSource source;
  private final SqlPolicy policy = new SqlPolicy();

  public ReadonlyQueries(DataSource source) {
    this.source = source;
  }

  public QueryResult execute(String sql) throws SQLException {
    String id = UUID.randomUUID().toString();
    String fingerprint;
    try {
      fingerprint =
          HexFormat.of()
              .formatHex(
                  java.security.MessageDigest.getInstance("SHA-256")
                      .digest(
                          Objects.toString(sql, "")
                              .getBytes(java.nio.charset.StandardCharsets.UTF_8)));
    } catch (java.security.NoSuchAlgorithmException exception) {
      throw new IllegalStateException(exception);
    }
    LOG.info("Query received id={} sql_sha256={}", id, fingerprint);
    String approved = policy.validate(sql);
    long started = System.nanoTime();
    try (Connection connection = source.getConnection()) {
      connection.setReadOnly(true);
      connection.setAutoCommit(false);
      try (var settings = connection.createStatement()) {
        settings.execute("SET LOCAL statement_timeout='2500ms'");
        settings.execute("SET LOCAL lock_timeout='500ms'");
        settings.execute("SET LOCAL search_path=analytics,pg_catalog");
      }
      try (PreparedStatement query =
          connection.prepareStatement(
              "SELECT * FROM (" + approved + ") AS guarded_query LIMIT 201")) {
        query.setQueryTimeout(3);
        query.setMaxRows(201);
        try (ResultSet result = query.executeQuery()) {
          var metadata = result.getMetaData();
          var columns = new ArrayList<String>();
          for (int index = 1; index <= metadata.getColumnCount(); index++) {
            String name = metadata.getColumnLabel(index);
            if (columns.contains(name))
              throw new IllegalArgumentException(
                  "Usa nombres distintos para las columnas del resultado");
            columns.add(name);
          }
          var rows = new ArrayList<Map<String, Object>>();
          boolean truncated = false;
          while (result.next()) {
            if (rows.size() == 200) {
              truncated = true;
              break;
            }
            var row = new LinkedHashMap<String, Object>();
            for (int index = 1; index <= columns.size(); index++) {
              Object value = result.getObject(index);
              if (value != null && !(value instanceof Number) && !(value instanceof Boolean))
                value = value.toString();
              row.put(columns.get(index - 1), value);
            }
            rows.add(row);
          }
          connection.rollback();
          double milliseconds = (System.nanoTime() - started) / 1_000_000.;
          LOG.info(
              "Query completed id={} rows={} truncated={} milliseconds={}",
              id,
              rows.size(),
              truncated,
              milliseconds);
          return new QueryResult(id, approved, columns, rows, truncated, milliseconds);
        }
      } finally {
        connection.rollback();
      }
    }
  }

  public record QueryResult(
      String id,
      String sql,
      List<String> columns,
      List<Map<String, Object>> rows,
      boolean truncated,
      double milliseconds) {}
}

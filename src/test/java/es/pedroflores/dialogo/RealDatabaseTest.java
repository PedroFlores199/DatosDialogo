package es.pedroflores.dialogo;

import static org.junit.jupiter.api.Assertions.*;

import java.sql.SQLException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

@EnabledIfEnvironmentVariable(named = "DIALOGO_TEST_DB_URL", matches = ".+")
class RealDatabaseTest {
  private DriverManagerDataSource source() {
    return new DriverManagerDataSource(
        System.getenv("DIALOGO_TEST_DB_URL"), "analytics_reader", "local-reader-demo");
  }

  @Test
  void queries_real_snapshot_and_caps_rows() throws Exception {
    var queries = new ReadonlyQueries(source());
    var count = queries.execute("SELECT COUNT(*) AS rows FROM fact_hotel");
    assertEquals(480L, ((Number) count.rows().getFirst().get("rows")).longValue());
    var all =
        queries.execute(
            "SELECT province,period_start FROM fact_hotel ORDER BY province,period_start");
    assertEquals(200, all.rows().size());
    assertTrue(all.truncated());
  }

  @Test
  void database_permissions_refuse_writes_even_without_parser() throws Exception {
    try (var connection = source().getConnection();
        var statement = connection.createStatement()) {
      statement.execute("SET default_transaction_read_only=off");
      var denied =
          assertThrows(
              SQLException.class,
              () ->
                  statement.execute("UPDATE analytics.fact_hotel SET nights_total=0 WHERE FALSE"));
      assertEquals("42501", denied.getSQLState());
      assertThrows(
          SQLException.class, () -> statement.executeQuery("SELECT * FROM rag.schema_embeddings"));
    }
  }

  @Test
  void expensive_query_times_out_and_next_query_still_works() throws Exception {
    var queries = new ReadonlyQueries(source());
    var timedOut =
        assertThrows(
            SQLException.class,
            () ->
                queries.execute(
                    "SELECT SUM(a.nights_total+b.nights_total+c.nights_total+d.nights_total) FROM"
                        + " fact_hotel a CROSS JOIN fact_hotel b CROSS JOIN fact_hotel c CROSS JOIN"
                        + " fact_hotel d"));
    assertEquals("57014", timedOut.getSQLState());
    assertFalse(queries.execute("SELECT province FROM fact_hotel LIMIT 1").rows().isEmpty());
  }
}

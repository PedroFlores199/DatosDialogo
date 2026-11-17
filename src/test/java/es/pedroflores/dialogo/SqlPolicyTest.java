package es.pedroflores.dialogo;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class SqlPolicyTest {
  private final SqlPolicy policy = new SqlPolicy();

  @ParameterizedTest
  @ValueSource(
      strings = {
        "SELECT province, SUM(nights_total) AS nights FROM fact_hotel WHERE year=2025 GROUP BY"
            + " province ORDER BY nights DESC",
        "WITH totals AS (SELECT province, SUM(nights_total) n FROM analytics.fact_hotel GROUP BY"
            + " province) SELECT * FROM totals",
        "SELECT province, round(AVG(occupancy_pct)::numeric, 2) FROM fact_hotel GROUP BY province",
        "SELECT COUNT(*) FROM fact_hotel WHERE province='Cádiz'"
      })
  void accepts_analytical_selects(String sql) {
    assertNotNull(policy.validate(sql));
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "DELETE FROM fact_hotel",
        "SELECT * FROM fact_hotel; DROP TABLE fact_hotel",
        "SELECT * INTO stolen FROM fact_hotel",
        "SELECT * FROM fact_hotel FOR UPDATE",
        "SELECT * FROM pg_catalog.pg_authid",
        "SELECT pg_sleep(10) FROM fact_hotel",
        "SELECT coalesce((SELECT pg_sleep(10)),null) FROM fact_hotel",
        "WITH x AS (DELETE FROM fact_hotel RETURNING *) SELECT * FROM x",
        "SELECT * FROM dblink('private','SELECT 1')",
        "SELECT 'pg_authid'::regclass FROM fact_hotel",
        "SELECT pg_read_file('/etc/passwd') FROM fact_hotel",
        "SELECT * FROM information_schema.tables",
        "SELECT * FROM other.fact_hotel"
      })
  void rejects_unsafe_statements_including_nested_calls(String sql) {
    assertThrows(IllegalArgumentException.class, () -> policy.validate(sql));
  }

  @Test
  void keywords_inside_data_are_not_executable_statements() {
    assertNotNull(policy.validate("SELECT province FROM fact_hotel WHERE province='DROP TABLE'"));
  }

  @Test
  void regional_spending_cannot_be_joined_to_provinces() {
    assertThrows(
        IllegalArgumentException.class,
        () ->
            policy.validate(
                "SELECT h.province,m.spend_eur FROM fact_hotel h CROSS JOIN fact_markets_annual"
                    + " m"));
  }
}

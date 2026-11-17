package es.pedroflores.dialogo;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class SemanticRulesTest {
  @Test
  void mismatched_metrics_are_rejected_before_execution() {
    assertThrows(
        IllegalArgumentException.class,
        () ->
            SemanticRules.validate(
                "Pernoctaciones por mes", "SELECT spend_eur FROM fact_region_monthly"));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            SemanticRules.validate(
                "Pernoctaciones por mes",
                "SELECT SUM(spend_eur) AS nights_total FROM fact_region_monthly"));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            SemanticRules.validate(
                "Viajeros residentes en España", "SELECT SUM(travelers_total) FROM fact_hotel"));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            SemanticRules.validate(
                "Turistas internacionales según FRONTUR",
                "SELECT SUM(nights_foreign) FROM fact_hotel"));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            SemanticRules.validate(
                "Plazas de viviendas turísticas", "SELECT SUM(homes) FROM fact_housing"));
  }

  @Test
  void snapshots_and_regions_keep_the_real_grain() {
    assertThrows(
        IllegalArgumentException.class,
        () ->
            SemanticRules.validate(
                "Viviendas en la última captura de 2023",
                "SELECT province, homes FROM fact_housing WHERE year=2023"));
    assertDoesNotThrow(
        () ->
            SemanticRules.validate(
                "Viviendas en la última captura de 2023",
                "SELECT province, homes FROM fact_housing WHERE period_start=(SELECT"
                    + " MAX(period_start) FROM fact_housing WHERE year=2023)"));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            SemanticRules.validate(
                "Noches de Andalucía",
                "SELECT SUM(nights_total) FROM fact_hotel WHERE province='Andalucía'"));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            SemanticRules.validate(
                "Noches de Andalucía",
                "SELECT SUM(nights_total) FROM fact_hotel WHERE province IS NULL"));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            SemanticRules.validate(
                "Viviendas turísticas de cada provincia en la última captura",
                "SELECT MAX(period_start) FROM fact_housing"));
  }

  @Test
  void weighted_percentage_is_not_scaled_twice() {
    assertThrows(
        IllegalArgumentException.class,
        () ->
            SemanticRules.validate(
                "Ocupación ponderada por plazas y días",
                "SELECT AVG(occupancy_pct) FROM fact_hotel"));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            SemanticRules.validate(
                "Ocupación ponderada por plazas y días",
                "SELECT SUM(occupancy_pct*hotel_beds*EXTRACT(DAY FROM"
                    + " period_start))*100/SUM(hotel_beds) FROM fact_hotel"));
    assertDoesNotThrow(
        () ->
            SemanticRules.validate(
                "Ocupación ponderada por plazas y días",
                "SELECT SUM(occupancy_pct*hotel_beds*EXTRACT(DAY FROM"
                    + " period_start))/SUM(hotel_beds) FROM fact_hotel"));
  }
}

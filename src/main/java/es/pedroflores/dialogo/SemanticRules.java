package es.pedroflores.dialogo;

import java.text.Normalizer;
import java.util.Locale;

public final class SemanticRules {
  private SemanticRules() {}

  public static void validate(String question, String sql) {
    String intent =
        Normalizer.normalize(question, Normalizer.Form.NFD)
            .replaceAll("\\p{M}", "")
            .toLowerCase(Locale.ROOT);
    String query = sql.toLowerCase(Locale.ROOT);
    if ((intent.contains("pernoct") || intent.contains("noches"))
        && !query.contains("fact_hotel")
        && !query.contains("fact_forecast"))
      reject(
          "Las pernoctaciones proceden de fact_hotel o, para previsiones, fact_forecast. Un alias"
              + " no convierte gasto regional en noches.");
    if (query.matches("(?s).*\\bprovince\\s*=\\s*'andaluc[ií]a'.*"))
      reject("Andalucía no es una provincia: agrega las ocho provincias sin ese filtro.");
    if (query.matches("(?s).*\\bprovince\\s+is\\s+null.*"))
      reject(
          "Todas las observaciones provinciales tienen provincia: no hay una fila regional con"
              + " province NULL. Agrega las ocho provincias sin ese filtro.");
    if (query.matches("(?s).*\\bregion\\s*=\\s*'andalucia'.*"))
      reject("El valor regional exacto es 'Andalucía', con tilde.");
    if ((intent.contains("pernoct") || intent.contains("noches"))
        && !query.matches(
            "(?s).*\\b(nights_total|nights_foreign|nights_domestic|predicted_nights)\\b.*"))
      reject(
          "La pregunta pide noches, no gasto ni viajeros. Usa una columna nights del hecho"
              + " correspondiente.");
    if (intent.contains("viajer")
        && intent.contains("residentes en espana")
        && !query.contains("travelers_domestic"))
      reject("Residentes en España corresponde a travelers_domestic, no travelers_total.");
    if ((intent.contains("frontur") || intent.contains("turistas internacionales"))
        && !query.contains("international_tourists"))
      reject(
          "FRONTUR corresponde a fact_region_monthly.international_tourists, no a viajeros ni"
              + " noches hoteleras.");
    if (intent.contains("viviend")
        && intent.contains("plazas")
        && !query.matches("(?s).*\\bbeds\\b.*"))
      reject("Las plazas de viviendas son beds; homes cuenta viviendas.");
    if (intent.contains("viviendas turisticas")
        && !intent.contains("plazas")
        && !intent.contains("fecha")
        && !query.matches("(?s).*\\bhomes\\b.*"))
      reject(
          "La respuesta debe contener el número de viviendas (homes), no solo la fecha de la"
              + " captura. Usa una subconsulta para la última fecha.");
    if (intent.contains("ultima") && intent.contains("captura") && query.contains("fact_housing")) {
      boolean maximum = query.matches("(?s).*max\\s*\\(\\s*(?:\\w+\\.)?period_start\\s*\\).*"),
          ordered =
              query.contains("order by")
                  && query.contains("period_start")
                  && query.contains("desc")
                  && query.contains("limit");
      if (!maximum && !ordered)
        reject(
            "Selecciona la última fecha de captura antes de devolver o sumar viviendas; no mezcles"
                + " los dos snapshots del año.");
    }
    if (intent.contains("ocupacion")
        && intent.contains("ponder")
        && !intent.contains("variacion")) {
      if (!query.contains("hotel_beds") || !query.matches("(?s).*extract\\s*\\(\\s*day\\b.*"))
        reject("La ocupación agregada requiere ponderar por hotel_beds y días de mes.");
      if (query.matches("(?s).*(?:\\*\\s*100(?:\\.0)?\\b|\\b100(?:\\.0)?\\s*\\*).*"))
        reject("occupancy_pct ya está en porcentaje: no multipliques otra vez por 100.");
    }
    if (intent.contains("variacion")
        && intent.contains("pernoct")
        && (!query.contains("100") || !query.contains("-")))
      reject(
          "Devuelve el porcentaje de variación entre los dos totales anuales, no dos filas con"
              + " cifras de cada año.");
  }

  private static void reject(String message) {
    throw new IllegalArgumentException("Coherencia del indicador: " + message);
  }
}

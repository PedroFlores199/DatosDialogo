package es.pedroflores.dialogo;

import java.nio.charset.StandardCharsets;
import java.util.*;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.pgvector.PgVectorStore;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

@Component
public class SchemaCatalog implements ApplicationRunner {
  private final PgVectorStore vectors;
  private final LinkedHashMap<String, String> tables = new LinkedHashMap<>();

  public SchemaCatalog(PgVectorStore vectors) {
    this.vectors = vectors;
    tables.put(
        "fact_hotel",
        "Una fila por provincia y mes, octubre 2021-septiembre 2026. Columnas: province text,"
            + " period_start date, year int, travelers_total, travelers_domestic,"
            + " travelers_foreign, nights_total, nights_domestic, nights_foreign, occupancy_pct,"
            + " hotel_beds, hotels, mean_stay (numéricas). Viajeros y noches no son lo mismo."
            + " Estancia agregada=SUM(nights_total)/SUM(travelers_total). Ocupación=media ponderada"
            + " por hotel_beds y días de cada mes; no sumar porcentajes. No contiene país concreto"
            + " de residencia ni gasto. Ejemplo: SELECT province, SUM(nights_total) AS nights FROM"
            + " fact_hotel WHERE year=2025 GROUP BY province ORDER BY nights DESC.");
    tables.put(
        "fact_markets_annual",
        "Una fila por Andalucía, país y año, 2016-2025. Columnas: region text, country text, year"
            + " int, spend_eur, spend_per_person_eur, daily_spend_eur, mean_trip_days. Gasto ANUAL"
            + " y REGIONAL por países Alemania, Francia, Italia, Reino Unido y Países Nórdicos;"
            + " country='Total' es el total regional. No hay provincia, mes ni número de viajeros."
            + " Para total, usar country='Total'; para ranking de países excluir country='Total'."
            + " Nunca sumar total y países ni desglosar gasto por provincia.");
    tables.put(
        "fact_region_monthly",
        "Una fila por Andalucía y mes, octubre 2021-septiembre 2026. Columnas: region text,"
            + " period_start date, year int, international_tourists, spend_eur,"
            + " spend_per_person_eur, daily_spend_eur, mean_trip_days. Turistas internacionales"
            + " FRONTUR y gasto EGATUR. Solo región, sin país ni provincia. Gasto total se suma;"
            + " gasto por persona agregado requiere ponderar por turistas. No mezclar turistas"
            + " internacionales de región con viajeros hoteleros.");
    tables.put(
        "fact_housing",
        "Una fila por provincia y captura semestral de vivienda turística. Columnas: province text,"
            + " period_start date, year int, homes, beds, beds_per_home. Es un snapshot, no un"
            + " flujo: no sumar distintas fechas. Para última cifra, buscar MAX(period_start) en la"
            + " selección y sumar provincias solo de esa fecha. No hay meses interpolados.");
    tables.put(
        "fact_forecast",
        "Previsiones mensuales desde octubre 2026 hasta marzo 2027. Columnas: province text,"
            + " period_start date, predicted_nights bigint, lower bigint, upper bigint, model text,"
            + " input_last_month date. Son previsiones orientativas, no observaciones. Límites"
            + " provinciales agregados no forman un intervalo conjunto calibrado.");
    tables.put(
        "dim_province",
        "Ocho provincias: Almería, Cádiz, Córdoba, Granada, Huelva, Jaén, Málaga, Sevilla."
            + " Columnas: code text, province text, region text, latitude float, longitude float."
            + " Relación province con fact_hotel, fact_housing y fact_forecast. No relaciona países"
            + " ni reparte gasto regional.");
    tables.put(
        "dim_date",
        "Calendario diario. Columnas: date date, year int, month int, quarter int. Relacionar date"
            + " con period_start de los hechos temporales. Los hechos hoteleros siguen siendo"
            + " mensuales, aunque el calendario sea diario. No hay datos meteorológicos,"
            + " restaurantes, ocupación futura real ni información personal.");
  }

  @Override
  public void run(ApplicationArguments args) {
    var documents =
        tables.entrySet().stream()
            .map(
                entry ->
                    new Document(
                        UUID.nameUUIDFromBytes(
                                ("schema-v1-" + entry.getKey()).getBytes(StandardCharsets.UTF_8))
                            .toString(),
                        entry.getKey() + ": " + entry.getValue(),
                        Map.of("table", entry.getKey(), "source", "INE / TurismoLakehouse")))
            .toList();
    vectors.add(documents);
  }

  public String get(String table) {
    if (!tables.containsKey(table)) throw new IllegalArgumentException("Tabla fuera del catálogo");
    return table + ": " + tables.get(table);
  }

  public List<Map<String, String>> descriptions() {
    return tables.entrySet().stream()
        .map(entry -> Map.of("table", entry.getKey(), "description", entry.getValue()))
        .toList();
  }

  public List<String> retrieve(String question) {
    return vectors
        .similaritySearch(
            SearchRequest.builder().query(question).topK(4).similarityThreshold(0.).build())
        .stream()
        .map(Document::getText)
        .toList();
  }

  public String compact() {
    return String.join("\n", tables.keySet());
  }
}

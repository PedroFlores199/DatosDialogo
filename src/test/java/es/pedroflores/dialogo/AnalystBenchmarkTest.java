package es.pedroflores.dialogo;

import static org.junit.jupiter.api.Assertions.*;

import com.fasterxml.jackson.databind.*;
import java.net.*;
import java.net.http.*;
import java.nio.file.*;
import java.time.*;
import java.util.*;
import java.util.stream.Stream;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

@EnabledIfEnvironmentVariable(named = "DIALOGO_EVALUATE_LLM", matches = "1")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class AnalystBenchmarkTest {
  private final ObjectMapper json = new ObjectMapper();
  private final List<Map<String, Object>> results = new ArrayList<>();
  private final Map<String, HttpClient> clients = new HashMap<>();
  private final String url =
      System.getenv().getOrDefault("DIALOGO_EVAL_URL", "http://localhost:5104");
  private final ReadonlyQueries reference =
      new ReadonlyQueries(
          new DriverManagerDataSource(
              System.getenv()
                  .getOrDefault("DIALOGO_TEST_DB_URL", "jdbc:postgresql://localhost:55434/dialogo"),
              "analytics_reader",
              "local-reader-demo"));

  @BeforeAll
  void ready() throws Exception {
    try (var client = HttpClient.newHttpClient()) {
      for (int attempt = 0; attempt < 90; attempt++) {
        try {
          var response =
              client.send(
                  HttpRequest.newBuilder(URI.create(url + "/actuator/health/readiness"))
                      .timeout(Duration.ofSeconds(3))
                      .GET()
                      .build(),
                  HttpResponse.BodyHandlers.ofString());
          if (response.statusCode() == 200
              && json.readTree(response.body()).path("status").asText().equals("UP")) return;
        } catch (java.io.IOException ignored) {
        }
        Thread.sleep(2000);
      }
    }
    fail("La aplicación no está preparada para ejecutar el banco de preguntas");
  }

  @TestFactory
  Stream<DynamicTest> fixed_questions_match_the_reference_database() throws Exception {
    var questions = json.readTree(Path.of("evaluation/questions.json").toFile());
    var nodes = new ArrayList<JsonNode>();
    questions.forEach(nodes::add);
    return nodes.stream()
        .map(item -> DynamicTest.dynamicTest(item.get("id").asText(), () -> check(item)));
  }

  void check(JsonNode item) throws Exception {
    String id = item.get("id").asText(),
        group = item.has("conversation") ? item.get("conversation").asText() : id;
    var client =
        clients.computeIfAbsent(
            group,
            key ->
                HttpClient.newBuilder()
                    .cookieHandler(new CookieManager(null, CookiePolicy.ACCEPT_ALL))
                    .connectTimeout(Duration.ofSeconds(10))
                    .build());
    var record = new LinkedHashMap<String, Object>();
    record.put("id", id);
    record.put("question", item.get("question").asText());
    long started = System.nanoTime();
    boolean passed = false;
    try {
      var request =
          HttpRequest.newBuilder(URI.create(url + "/api/chat"))
              .timeout(Duration.ofSeconds(180))
              .header("Content-Type", "application/json")
              .POST(
                  HttpRequest.BodyPublishers.ofString(
                      json.writeValueAsString(Map.of("question", item.get("question").asText()))))
              .build();
      var response = client.send(request, HttpResponse.BodyHandlers.ofString());
      var answer = json.readTree(response.body());
      record.put("http_status", response.statusCode());
      record.put("answer", answer);
      if (item.hasNonNull("reference_sql")) {
        var expected = reference.execute(item.get("reference_sql").asText());
        var actual = answer.get("result");
        record.put("reference_sql", item.get("reference_sql").asText());
        record.put("expected_rows", expected.rows());
        passed =
            response.statusCode() == 200
                && answer.path("status").asText().equals("ok")
                && actual != null
                && !actual.isNull()
                && equalRows(actual, expected);
      } else
        passed =
            response.statusCode() == 200
                && answer.path("status").asText().equals("unsupported")
                && (!answer.has("result") || answer.path("result").isNull());
    } finally {
      record.put("passed", passed);
      record.put("seconds", (System.nanoTime() - started) / 1e9);
      results.add(record);
      if (!item.has("conversation") || item.path("end_conversation").asBoolean()) {
        try {
          client.send(
              HttpRequest.newBuilder(URI.create(url + "/api/session"))
                  .timeout(Duration.ofSeconds(5))
                  .DELETE()
                  .build(),
              HttpResponse.BodyHandlers.discarding());
        } finally {
          client.close();
          clients.remove(group);
        }
      }
    }
    assertTrue(passed, "El resultado no coincide con la consulta de referencia: " + id);
  }

  boolean equalRows(JsonNode actual, ReadonlyQueries.QueryResult expected) {
    var columns = actual.path("columns");
    var rows = actual.path("rows");
    if (rows.size() != expected.rows().size() || columns.size() != expected.columns().size())
      return false;
    for (int r = 0; r < rows.size(); r++)
      for (int c = 0; c < columns.size(); c++) {
        JsonNode value = rows.get(r).get(columns.get(c).asText());
        Object other = expected.rows().get(r).get(expected.columns().get(c));
        if (other == null) {
          if (value != null && !value.isNull()) return false;
        } else if (other instanceof Number number) {
          double a;
          try {
            a = Double.parseDouble(value.asText());
          } catch (Exception exception) {
            return false;
          }
          double b = number.doubleValue();
          if (!Double.isFinite(a) || Math.abs(a - b) > Math.max(.011, Math.abs(b) * 1e-9))
            return false;
        } else if (value == null || !value.asText().equals(other.toString())) return false;
      }
    return true;
  }

  @AfterAll
  void report() throws Exception {
    clients.values().forEach(HttpClient::close);
    long sql = results.stream().filter(r -> r.containsKey("reference_sql")).count();
    long sqlPassed =
        results.stream()
            .filter(r -> r.containsKey("reference_sql") && Boolean.TRUE.equals(r.get("passed")))
            .count();
    long refusedPassed =
        results.stream()
            .filter(r -> !r.containsKey("reference_sql") && Boolean.TRUE.equals(r.get("passed")))
            .count();
    var summary = new LinkedHashMap<String, Object>();
    summary.put("complete", results.size() == 40);
    summary.put("at", Instant.now().toString());
    summary.put(
        "model", System.getenv().getOrDefault("CHAT_MODEL", "qwen3:4b-instruct-2507-q4_K_M"));
    summary.put("runner", "JUnit 5 / Java 21 / real HTTP API and PostgreSQL reference queries");
    summary.put("sql_cases", sql);
    summary.put("sql_execution_accuracy", sql == 0 ? 0 : (double) sqlPassed / sql);
    summary.put("refusal_cases", results.size() - sql);
    summary.put("correct_refusals", refusedPassed);
    summary.put("results", results);
    Path destination =
        Path.of(
            System.getenv().getOrDefault("DIALOGO_EVAL_REPORT", "evaluation/junit-results.json"));
    Files.createDirectories(destination.toAbsolutePath().getParent());
    json.writerWithDefaultPrettyPrinter().writeValue(destination.toFile(), summary);
  }
}

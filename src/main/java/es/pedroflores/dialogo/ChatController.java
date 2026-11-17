package es.pedroflores.dialogo;

import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.*;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api")
public class ChatController {
  private final Analyst analyst;
  private final SchemaCatalog catalog;

  public ChatController(Analyst analyst, SchemaCatalog catalog) {
    this.analyst = analyst;
    this.catalog = catalog;
  }

  @PostMapping("/chat")
  public Analyst.Answer chat(@Valid @RequestBody Question body, HttpSession session) {
    synchronized (session) {
      return analyst.answer(body.question(), conversation(session));
    }
  }

  @GetMapping("/catalog")
  public List<Map<String, String>> catalog() {
    return catalog.descriptions();
  }

  @GetMapping("/export/{format}")
  public ResponseEntity<byte[]> export(
      @PathVariable String format, @RequestParam String id, HttpSession session)
      throws java.io.IOException {
    synchronized (session) {
      var memory = conversation(session);
      if (memory.latest == null || !memory.latest.id().equals(id))
        throw new ResponseStatusException(
            HttpStatus.CONFLICT,
            "El resultado ya no está disponible. Vuelve a ejecutar la consulta.");
      String question =
          Objects.requireNonNullElse(memory.latestQuestion, memory.turns.getLast().question());
      if (!Set.of("pdf", "xlsx").contains(format))
        throw new ResponseStatusException(HttpStatus.NOT_FOUND);
      byte[] data =
          format.equals("pdf")
              ? Exports.pdf(question, memory.latest)
              : Exports.excel(question, memory.latest);
      return ResponseEntity.ok()
          .header(
              HttpHeaders.CONTENT_TYPE,
              format.equals("pdf")
                  ? "application/pdf"
                  : "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
          .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=datos-dialogo." + format)
          .header(HttpHeaders.CACHE_CONTROL, "no-store")
          .body(data);
    }
  }

  @DeleteMapping("/session")
  public Map<String, String> clear(HttpSession session) {
    session.invalidate();
    return Map.of("status", "cleared");
  }

  static Analyst.Conversation conversation(HttpSession session) {
    var memory = (Analyst.Conversation) session.getAttribute("conversation");
    if (memory == null) {
      memory = new Analyst.Conversation();
      session.setAttribute("conversation", memory);
    }
    return memory;
  }

  @ExceptionHandler(Analyst.Busy.class)
  @ResponseStatus(HttpStatus.TOO_MANY_REQUESTS)
  Map<String, String> busy() {
    return Map.of(
        "detail", "El modelo está atendiendo otra consulta. Prueba de nuevo en unos segundos.");
  }

  @ExceptionHandler(IllegalArgumentException.class)
  @ResponseStatus(HttpStatus.UNPROCESSABLE_ENTITY)
  Map<String, String> invalid() {
    return Map.of(
        "detail", "No se ha podido validar esta consulta. Revisa el indicador y la selección.");
  }

  @ExceptionHandler(RuntimeException.class)
  @ResponseStatus(HttpStatus.SERVICE_UNAVAILABLE)
  Map<String, String> unavailable(RuntimeException exception) {
    if (exception instanceof ResponseStatusException status) throw status;
    return Map.of(
        "detail",
        "No se ha podido completar el análisis. Comprueba que el modelo local y la base de datos"
            + " estén disponibles.");
  }

  public record Question(@NotBlank @Size(max = 700) String question) {}
}

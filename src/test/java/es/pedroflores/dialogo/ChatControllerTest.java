package es.pedroflores.dialogo;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.util.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class ChatControllerTest {
  private MockMvc mvc;

  @BeforeEach
  void setup() {
    mvc =
        MockMvcBuilders.standaloneSetup(
                new ChatController(mock(Analyst.class), mock(SchemaCatalog.class)))
            .build();
  }

  @Test
  void export_requires_the_matching_result_in_the_same_session() throws Exception {
    var session = new MockHttpSession();
    var memory = new Analyst.Conversation();
    memory.latest =
        new ReadonlyQueries.QueryResult(
            "current",
            "SELECT province FROM fact_hotel",
            List.of("province"),
            List.of(Map.of("province", "Almería")),
            false,
            1.);
    memory.turns.add(new Analyst.Turn("Provincia", memory.latest.sql()));
    session.setAttribute("conversation", memory);
    mvc.perform(get("/api/export/xlsx?id=old").session(session)).andExpect(status().isConflict());
    mvc.perform(get("/api/export/xlsx?id=current").session(session))
        .andExpect(status().isOk())
        .andExpect(header().string("Cache-Control", "no-store"));
    mvc.perform(get("/api/export/pdf?id=current")).andExpect(status().isConflict());
    mvc.perform(delete("/api/session").session(session)).andExpect(status().isOk());
  }

  @Test
  void malformed_or_empty_questions_do_not_reach_the_analyst() throws Exception {
    mvc.perform(post("/api/chat").contentType("application/json").content("{\"question\":\" \"}"))
        .andExpect(status().isBadRequest());
    mvc.perform(post("/api/chat").contentType("application/json").content("{}"))
        .andExpect(status().isBadRequest());
  }
}

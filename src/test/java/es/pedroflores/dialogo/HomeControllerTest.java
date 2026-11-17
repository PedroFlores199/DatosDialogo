package es.pedroflores.dialogo;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(HomeController.class)
@org.springframework.test.context.ContextConfiguration(classes = HomeController.class)
class HomeControllerTest {
  @Autowired private MockMvc mvc;
  @MockitoBean private SchemaCatalog catalog;

  @Test
  void server_renders_the_catalog_size_from_the_backend() throws Exception {
    when(catalog.descriptions())
        .thenReturn(List.of(Map.of("table", "one"), Map.of("table", "two")));
    mvc.perform(get("/"))
        .andExpect(status().isOk())
        .andExpect(content().string(containsString("DatosDialogo")))
        .andExpect(content().string(containsString("2 tablas")));
  }
}

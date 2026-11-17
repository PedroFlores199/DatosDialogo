package es.pedroflores.dialogo;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class HomeController {
  private final SchemaCatalog catalog;

  public HomeController(SchemaCatalog catalog) {
    this.catalog = catalog;
  }

  @GetMapping("/")
  public String home(Model model) {
    model.addAttribute("tableCount", catalog.descriptions().size());
    return "index";
  }
}

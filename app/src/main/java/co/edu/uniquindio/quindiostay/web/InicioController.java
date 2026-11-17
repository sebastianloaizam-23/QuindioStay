package co.edu.uniquindio.quindiostay.web;

import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import co.edu.uniquindio.quindiostay.crud.CatalogoTablas;
import co.edu.uniquindio.quindiostay.seguridad.UsuarioActual;

@Controller
public class InicioController {

    private final JdbcTemplate jdbc;

    public InicioController(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @GetMapping("/login")
    public String login() {
        return "login";
    }

    /** Después del login cada rol va a su pantalla. */
    @GetMapping("/")
    public String inicio(@AuthenticationPrincipal UsuarioActual u, Model model) {
        if (u.tieneRol("CLIENTE")) return "redirect:/mi-cuenta";
        if (u.tieneRol("ENCARGADO")) return "redirect:/encargado";

        // Prueba de conexión: cuenta los registros de cada tabla (cliente debe dar 150)
        Map<String, Long> conteos = new LinkedHashMap<>();
        for (String tabla : CatalogoTablas.TITULOS.keySet()) {
            if (!u.tieneRol("ADMIN") && CatalogoTablas.SOLO_ADMIN.contains(tabla)) continue;
            conteos.put(tabla, jdbc.queryForObject("SELECT COUNT(*) FROM `" + tabla + "`", Long.class));
        }
        model.addAttribute("conteos", conteos);
        model.addAttribute("titulos", CatalogoTablas.TITULOS);
        model.addAttribute("fidelizacion", CatalogoTablas.FIDELIZACION);
        return "inicio";
    }
}

package co.edu.uniquindio.quindiostay.fidelizacion;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import co.edu.uniquindio.quindiostay.web.ErroresBD;

/**
 * Canje de beneficios (RF-07). Nunca se inserta en redencion a mano:
 * se llama al procedimiento sp_redimir, que en UNA transacción crea el movimiento
 * negativo y la redención, y valida saldo, costo vigente y nivel con los triggers.
 */
@Controller
@RequestMapping("/redimir")
public class RedencionController {

    private final JdbcTemplate jdbc;

    public RedencionController(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @GetMapping
    public String formulario(Model model) {
        model.addAttribute("membresias", jdbc.queryForList("""
                SELECT s.id_membresia AS id,
                       CONCAT(c.nombres, ' ', c.apellidos, ' - saldo ', s.saldo, ' pts - ', n.nivel) AS etiqueta
                FROM v_saldo_membresia s
                JOIN v_nivel_membresia n ON n.id_membresia = s.id_membresia
                JOIN cliente c ON c.id_cliente = s.id_cliente
                ORDER BY c.nombres, c.apellidos
                """));
        model.addAttribute("beneficios", jdbc.queryForList("""
                SELECT b.id_beneficio AS id,
                       CONCAT(b.nombre, ' - ', b.costo_puntos, ' pts - nivel ', nv.nombre, ' - ', p.nombre_comercial) AS etiqueta
                FROM beneficio b
                JOIN nivel nv ON nv.id_nivel = b.id_nivel
                JOIN prestador p ON p.id_prestador = b.id_prestador_aliado
                ORDER BY b.costo_puntos
                """));
        model.addAttribute("ultimas", jdbc.queryForList("""
                SELECT m.id_movimiento AS movimiento, m.fecha, CONCAT(c.nombres, ' ', c.apellidos) AS cliente,
                       b.nombre AS beneficio, -m.cantidad AS puntos
                FROM redencion r
                JOIN movimiento_puntos m ON m.id_movimiento = r.id_movimiento
                JOIN beneficio b ON b.id_beneficio = r.id_beneficio
                JOIN membresia mb ON mb.id_membresia = m.id_membresia
                JOIN cliente c ON c.id_cliente = mb.id_cliente
                ORDER BY m.id_movimiento DESC
                LIMIT 15
                """));
        return "redimir";
    }

    @PostMapping
    public String redimir(@RequestParam int idMembresia, @RequestParam int idBeneficio, RedirectAttributes ra) {
        try {
            jdbc.update("CALL sp_redimir(?, ?)", idMembresia, idBeneficio);
            ra.addFlashAttribute("ok", "Canje registrado: se descontaron los puntos y se creó la redención.");
        } catch (RuntimeException e) {
            ra.addFlashAttribute("error", ErroresBD.traducir(e));
        }
        return "redirect:/redimir";
    }
}

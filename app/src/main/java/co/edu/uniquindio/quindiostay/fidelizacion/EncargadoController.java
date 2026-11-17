package co.edu.uniquindio.quindiostay.fidelizacion;

import java.util.List;
import java.util.Map;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import co.edu.uniquindio.quindiostay.seguridad.UsuarioActual;

/**
 * Pantalla del encargado de prestador: solo ve lo de SU prestador
 * (planes, reservas, beneficios y movimientos de puntos). Todo se filtra por id_prestador.
 */
@Controller
public class EncargadoController {

    private final JdbcTemplate jdbc;

    public EncargadoController(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @GetMapping("/encargado")
    public String panel(@AuthenticationPrincipal UsuarioActual u, Model model) {
        int idPrestador = u.getIdPrestador();
        model.addAttribute("prestador", jdbc.queryForMap("""
                SELECT p.*, m.nombre AS municipio, t.nombre AS tipo
                FROM prestador p
                JOIN municipio m ON m.id_municipio = p.id_municipio
                JOIN tipo_prestador t ON t.id_tipo_prestador = p.id_tipo_prestador
                WHERE p.id_prestador = ?
                """, idPrestador));
        List<Map<String, Object>> aliado = jdbc.queryForList(
                "SELECT puntos_por_1000 FROM prestador_aliado WHERE id_prestador = ?", idPrestador);
        model.addAttribute("puntosPor1000", aliado.isEmpty() ? null : aliado.get(0).get("puntos_por_1000"));

        model.addAttribute("beneficios", jdbc.queryForList("""
                SELECT b.id_beneficio, b.nombre, b.costo_puntos, nv.nombre AS nivel_minimo,
                       (SELECT COUNT(*) FROM redencion r WHERE r.id_beneficio = b.id_beneficio) AS canjes
                FROM beneficio b
                JOIN nivel nv ON nv.id_nivel = b.id_nivel
                WHERE b.id_prestador_aliado = ?
                ORDER BY b.costo_puntos
                """, idPrestador));
        // v_movimiento_aliado resuelve el aliado de cada movimiento (por la reserva o por el beneficio)
        model.addAttribute("movimientos", jdbc.queryForList("""
                SELECT v.id_movimiento AS movimiento, mp.fecha, v.tipo, v.cantidad AS puntos,
                       CONCAT(c.nombres, ' ', c.apellidos) AS cliente
                FROM v_movimiento_aliado v
                JOIN movimiento_puntos mp ON mp.id_movimiento = v.id_movimiento
                JOIN membresia mb ON mb.id_membresia = v.id_membresia
                JOIN cliente c ON c.id_cliente = mb.id_cliente
                WHERE v.id_prestador_aliado = ?
                ORDER BY mp.fecha DESC
                LIMIT 50
                """, idPrestador));
        model.addAttribute("planes", jdbc.queryForList("""
                SELECT id_plan, nombre, tipo, precio, fecha_inicio, fecha_fin, estado
                FROM plan WHERE id_prestador = ? ORDER BY estado, nombre
                """, idPrestador));
        model.addAttribute("reservas", jdbc.queryForList("""
                SELECT r.id_reserva, r.fecha_servicio, pl.nombre AS plan,
                       CONCAT(c.nombres, ' ', c.apellidos) AS cliente, r.estado, r.valor_total
                FROM reserva r
                JOIN plan pl ON pl.id_plan = r.id_plan
                JOIN cliente c ON c.id_cliente = r.id_cliente
                WHERE pl.id_prestador = ?
                ORDER BY r.fecha_servicio DESC
                LIMIT 30
                """, idPrestador));
        return "encargado/panel";
    }
}

package co.edu.uniquindio.quindiostay.fidelizacion;

import java.util.List;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import co.edu.uniquindio.quindiostay.documentos.DocumentoService;
import co.edu.uniquindio.quindiostay.seguridad.UsuarioActual;
import co.edu.uniquindio.quindiostay.web.ErroresBD;

/** Pantalla del cliente: su membresía, nivel, saldo, movimientos, reservas y canjes. */
@Controller
@RequestMapping("/mi-cuenta")
public class MiCuentaController {

    private final JdbcTemplate jdbc;
    private final DocumentoService documentos;

    public MiCuentaController(JdbcTemplate jdbc, DocumentoService documentos) {
        this.jdbc = jdbc;
        this.documentos = documentos;
    }

    @GetMapping
    public String miCuenta(@AuthenticationPrincipal UsuarioActual u, Model model) {
        int idCliente = u.getId();
        model.addAttribute("cliente", jdbc.queryForMap("SELECT * FROM cliente WHERE id_cliente = ?", idCliente));

        List<Map<String, Object>> mem = jdbc.queryForList("""
                SELECT s.id_membresia, mb.fecha_inscripcion, s.saldo, n.acumulado_12m, n.nivel
                FROM v_saldo_membresia s
                JOIN v_nivel_membresia n ON n.id_membresia = s.id_membresia
                JOIN membresia mb ON mb.id_membresia = s.id_membresia
                WHERE s.id_cliente = ?
                """, idCliente);
        Map<String, Object> membresia = mem.isEmpty() ? null : mem.get(0);
        model.addAttribute("membresia", membresia);

        if (membresia != null) {
            model.addAttribute("movimientos", jdbc.queryForList("""
                    SELECT m.fecha, m.tipo, m.cantidad AS puntos, b.nombre AS beneficio, m.id_reserva AS reserva
                    FROM movimiento_puntos m
                    LEFT JOIN redencion r ON r.id_movimiento = m.id_movimiento
                    LEFT JOIN beneficio b ON b.id_beneficio = r.id_beneficio
                    WHERE m.id_membresia = ?
                    ORDER BY m.fecha DESC
                    LIMIT 20
                    """, membresia.get("id_membresia")));
            model.addAttribute("beneficios", jdbc.queryForList("""
                    SELECT b.id_beneficio, b.nombre, b.costo_puntos, nv.nombre AS nivel_minimo, p.nombre_comercial AS aliado
                    FROM beneficio b
                    JOIN nivel nv ON nv.id_nivel = b.id_nivel
                    JOIN prestador p ON p.id_prestador = b.id_prestador_aliado
                    ORDER BY b.costo_puntos
                    """));
        }
        model.addAttribute("reservas", jdbc.queryForList("""
                SELECT r.id_reserva, r.fecha_servicio, pl.nombre AS plan, p.nombre_comercial AS prestador,
                       r.estado, r.valor_total
                FROM reserva r
                JOIN plan pl ON pl.id_plan = r.id_plan
                JOIN prestador p ON p.id_prestador = pl.id_prestador
                WHERE r.id_cliente = ?
                ORDER BY r.fecha_servicio DESC
                """, idCliente));
        return "cliente/mi-cuenta";
    }

    /** El cliente canjea un beneficio con su propia membresía. */
    @PostMapping("/canjear")
    public String canjear(@AuthenticationPrincipal UsuarioActual u, @RequestParam int idBeneficio, RedirectAttributes ra) {
        List<Integer> mem = jdbc.queryForList("SELECT id_membresia FROM membresia WHERE id_cliente = ?", Integer.class, u.getId());
        if (mem.isEmpty()) {
            ra.addFlashAttribute("error", "No tienes membresía en el programa de puntos.");
            return "redirect:/mi-cuenta";
        }
        try {
            jdbc.update("CALL sp_redimir(?, ?)", mem.get(0), idBeneficio);
            ra.addFlashAttribute("ok", "¡Canje realizado! Ya se descontaron los puntos.");
        } catch (RuntimeException e) {
            ra.addFlashAttribute("error", ErroresBD.traducir(e));
        }
        return "redirect:/mi-cuenta";
    }

    @GetMapping("/certificado")
    public ResponseEntity<byte[]> certificado(@AuthenticationPrincipal UsuarioActual u) {
        return DocumentoService.respuesta(documentos.certificadoPuntos(u.getId()), "certificado-puntos.pdf");
    }

    /** Comprobante de una reserva, solo si es del cliente que inició sesión. */
    @GetMapping("/comprobante")
    public ResponseEntity<byte[]> comprobante(@AuthenticationPrincipal UsuarioActual u, @RequestParam int id) {
        Integer n = jdbc.queryForObject("SELECT COUNT(*) FROM reserva WHERE id_reserva = ? AND id_cliente = ?",
                Integer.class, id, u.getId());
        if (n == null || n == 0) throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Esa reserva no es tuya");
        return DocumentoService.respuesta(documentos.comprobanteReserva(id), "comprobante-reserva-" + id + ".pdf");
    }
}

package co.edu.uniquindio.quindiostay.reportes;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

/**
 * Los 10 reportes. Las consultas son las mismas de 04_reportes.sql, sin cambios,
 * para que lo que se ve en la aplicación sea exactamente lo que se probó en MySQL.
 */
@Service
public class ReporteService {

    private final JdbcTemplate jdbc;
    private final Map<String, Reporte> reportes = new LinkedHashMap<>();

    public ReporteService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;

        agregar(new Reporte("R1", "Prestadores por estado", "Simple",
                "¿Cuántos prestadores hay en cada estado?", """
                SELECT estado,
                       COUNT(*) AS prestadores
                FROM prestador
                GROUP BY estado
                ORDER BY prestadores DESC
                """, null, null, List.of()));

        agregar(new Reporte("R2", "Reservas por estado", "Simple",
                "¿Cuántas reservas hay en cada estado y qué valor suman?", """
                SELECT estado,
                       COUNT(*)                   AS reservas,
                       SUM(valor_total)           AS valor_total,
                       ROUND(AVG(valor_total), 0) AS valor_promedio
                FROM reserva
                GROUP BY estado
                ORDER BY reservas DESC
                """, null, null, List.of()));

        agregar(new Reporte("R3", "Planes vigentes", "Simple",
                "¿Qué planes están activos y vigentes hoy y a qué precio?", """
                SELECT id_plan,
                       nombre,
                       tipo,
                       precio,
                       fecha_inicio,
                       fecha_fin,
                       id_prestador
                FROM plan
                WHERE estado = 'activo'
                  AND CURDATE() BETWEEN fecha_inicio AND fecha_fin
                ORDER BY precio DESC
                """, null, null, List.of()));

        agregar(new Reporte("R4", "Ingresos por prestador y mes", "Intermedio",
                "¿Cuánto recaudó cada prestador por mes en pagos exitosos?", """
                SELECT DATE_FORMAT(pg.fecha, '%Y-%m') AS mes,
                       p.nombre_comercial             AS prestador,
                       COUNT(*)                       AS pagos,
                       SUM(pg.monto)                  AS ingresos
                FROM pago pg
                JOIN reserva r   ON r.id_reserva = pg.id_reserva
                JOIN plan pl     ON pl.id_plan = r.id_plan
                JOIN prestador p ON p.id_prestador = pl.id_prestador
                WHERE pg.estado = 'exitoso'
                GROUP BY DATE_FORMAT(pg.fecha, '%Y-%m'), p.id_prestador, p.nombre_comercial
                ORDER BY mes, ingresos DESC
                """, null, null, List.of()));

        agregar(new Reporte("R5", "Efectividad de las notificaciones", "Intermedio",
                "¿Qué porcentaje de notificaciones llega, se lee o falla, por canal y tipo, y cuántas respetan el canal preferido?", """
                SELECT n.canal,
                       n.tipo,
                       COUNT(*) AS total,
                       ROUND(100 * SUM(n.estado IN ('enviada', 'leída')) / COUNT(*), 1) AS pct_entregadas,
                       ROUND(100 * SUM(n.estado = 'leída')   / COUNT(*), 1) AS pct_leidas,
                       ROUND(100 * SUM(n.estado = 'fallida') / COUNT(*), 1) AS pct_fallidas,
                       SUM(CASE WHEN pc.canal_preferido = n.canal THEN 1 ELSE 0 END) AS en_canal_preferido
                FROM notificacion n
                LEFT JOIN perfil_cliente pc ON pc.id_cliente = n.id_cliente
                WHERE n.estado <> 'pendiente'
                GROUP BY n.canal, n.tipo
                HAVING COUNT(*) >= 5
                ORDER BY n.tipo, pct_fallidas DESC
                """, null, null, List.of()));

        agregar(new Reporte("R6", "Beneficios más redimidos", "Intermedio",
                "¿Qué beneficios se canjean más y cuántos puntos consumen?", """
                SELECT b.nombre                 AS beneficio,
                       p.nombre_comercial       AS aliado,
                       COUNT(*)                 AS canjes,
                       SUM(-m.cantidad)         AS puntos_consumidos
                FROM redencion rd
                JOIN movimiento_puntos m  ON m.id_movimiento = rd.id_movimiento
                JOIN beneficio b          ON b.id_beneficio = rd.id_beneficio
                JOIN prestador_aliado pa  ON pa.id_prestador = b.id_prestador_aliado
                JOIN prestador p          ON p.id_prestador = pa.id_prestador
                GROUP BY b.id_beneficio, b.nombre, p.nombre_comercial
                ORDER BY canjes DESC, puntos_consumidos DESC
                """, "bar", "beneficio", List.of("canjes")));

        agregar(new Reporte("R7", "Clientes por nivel", "Avanzado",
                "¿Cuántos clientes hay en cada nivel según sus acumulaciones de los últimos 12 meses?", """
                SELECT n.nombre AS nivel,
                       n.umbral_minimo,
                       COUNT(t.id_membresia) AS clientes
                FROM nivel n
                LEFT JOIN (
                    SELECT mb.id_membresia,
                           (SELECT MAX(n2.umbral_minimo)
                              FROM nivel n2
                             WHERE n2.umbral_minimo <=
                                   (SELECT COALESCE(SUM(m.cantidad), 0)
                                      FROM movimiento_puntos m
                                     WHERE m.id_membresia = mb.id_membresia
                                       AND m.tipo = 'acumulación'
                                       AND m.fecha > DATE_SUB(NOW(), INTERVAL 12 MONTH))
                           ) AS umbral_alcanzado
                    FROM membresia mb
                ) t ON t.umbral_alcanzado = n.umbral_minimo
                GROUP BY n.id_nivel, n.nombre, n.umbral_minimo
                ORDER BY n.umbral_minimo
                """, "pie", "nivel", List.of("clientes")));

        agregar(new Reporte("R8", "Puntos emitidos frente a redimidos por mes", "Avanzado",
                "¿Cómo evoluciona cada mes lo emitido, redimido y vencido, y cuál es el saldo acumulado del programa?", """
                SELECT t.mes,
                       t.emitidos,
                       t.redimidos,
                       t.vencidos,
                       t.ajustes,
                       (SELECT COALESCE(SUM(m2.cantidad), 0)
                          FROM movimiento_puntos m2
                         WHERE DATE_FORMAT(m2.fecha, '%Y-%m') <= t.mes) AS saldo_acumulado
                FROM (
                    SELECT DATE_FORMAT(fecha, '%Y-%m') AS mes,
                           SUM(CASE WHEN tipo = 'acumulación'   THEN cantidad  ELSE 0 END) AS emitidos,
                           SUM(CASE WHEN tipo = 'redención'     THEN -cantidad ELSE 0 END) AS redimidos,
                           SUM(CASE WHEN tipo = 'vencimiento'   THEN -cantidad ELSE 0 END) AS vencidos,
                           SUM(CASE WHEN tipo = 'ajuste manual' THEN cantidad  ELSE 0 END) AS ajustes
                    FROM movimiento_puntos
                    GROUP BY DATE_FORMAT(fecha, '%Y-%m')
                ) t
                ORDER BY t.mes
                """, "line", "mes", List.of("emitidos", "redimidos", "vencidos")));

        agregar(new Reporte("R9", "Puntos próximos a vencer", "Avanzado",
                "¿Qué membresías tienen puntos que cumplirán 12 meses en los próximos 30 días sin haberse consumido?", """
                SELECT c.id_cliente,
                       CONCAT(c.nombres, ' ', c.apellidos) AS cliente,
                       c.correo,
                       x.saldo_actual,
                       x.ganado_hasta_corte - x.consumido  AS puntos_por_vencer
                FROM (
                    SELECT mb.id_membresia,
                           mb.id_cliente,
                           (SELECT COALESCE(SUM(m.cantidad), 0)
                              FROM movimiento_puntos m
                             WHERE m.id_membresia = mb.id_membresia) AS saldo_actual,
                           (SELECT COALESCE(SUM(m.cantidad), 0)
                              FROM movimiento_puntos m
                             WHERE m.id_membresia = mb.id_membresia
                               AND m.tipo = 'acumulación'
                               AND m.fecha <= DATE_SUB(DATE_ADD(NOW(), INTERVAL 30 DAY),
                                                       INTERVAL 12 MONTH)) AS ganado_hasta_corte,
                           (SELECT COALESCE(-SUM(m.cantidad), 0)
                              FROM movimiento_puntos m
                             WHERE m.id_membresia = mb.id_membresia
                               AND m.cantidad < 0) AS consumido
                    FROM membresia mb
                ) x
                JOIN cliente c ON c.id_cliente = x.id_cliente
                WHERE x.ganado_hasta_corte - x.consumido > 0
                ORDER BY puntos_por_vencer DESC
                """, null, null, List.of()));

        agregar(new Reporte("R10", "Aliados frente a no aliados", "Intermedio",
                "¿Cuánto ingreso y cuántos puntos genera cada prestador, y cómo se comparan los aliados con los que no lo son?", """
                SELECT p.nombre_comercial AS prestador,
                       CASE WHEN pa.id_prestador IS NULL THEN 'no aliado' ELSE 'aliado' END AS condicion,
                       COUNT(DISTINCT r.id_reserva)          AS reservas_completadas,
                       COALESCE(SUM(r.valor_total), 0)       AS ingreso_reservas,
                       COALESCE(SUM(m.cantidad), 0)          AS puntos_otorgados
                FROM prestador p
                LEFT JOIN prestador_aliado pa ON pa.id_prestador = p.id_prestador
                LEFT JOIN plan pl             ON pl.id_prestador = p.id_prestador
                LEFT JOIN reserva r           ON r.id_plan = pl.id_plan AND r.estado = 'completada'
                LEFT JOIN movimiento_puntos m ON m.id_reserva = r.id_reserva
                GROUP BY p.id_prestador, p.nombre_comercial, pa.id_prestador
                ORDER BY condicion, ingreso_reservas DESC
                """, null, null, List.of()));
    }

    private void agregar(Reporte r) {
        reportes.put(r.getCodigo(), r);
    }

    public List<Reporte> todos() {
        return new ArrayList<>(reportes.values());
    }

    public Reporte buscar(String codigo) {
        Reporte r = reportes.get(codigo.toUpperCase());
        if (r == null) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "No existe el reporte " + codigo);
        return r;
    }

    public List<Map<String, Object>> ejecutar(Reporte r) {
        return jdbc.queryForList(r.getSql());
    }

    /**
     * Detalle del R7 como en el prototipo: cada cliente con su nivel, agrupado por nivel,
     * con filtros por nivel y por rango de acumulaciones de los últimos 12 meses.
     * Usa la vista v_nivel_membresia (02_triggers.sql), que calcula el mismo nivel del R7.
     */
    public List<Map<String, Object>> detalleR7(String nivel, Integer minimo, Integer maximo) {
        StringBuilder sql = new StringBuilder("""
                SELECT v.nivel, c.id_cliente, c.nombres, c.apellidos, v.acumulado_12m AS puntos_12m,
                       s.saldo, c.ciudad_origen AS ciudad
                FROM v_nivel_membresia v
                JOIN cliente c ON c.id_cliente = v.id_cliente
                JOIN v_saldo_membresia s ON s.id_membresia = v.id_membresia
                WHERE 1 = 1
                """);
        List<Object> params = new ArrayList<>();
        if (nivel != null && !nivel.isBlank()) { sql.append(" AND v.nivel = ?"); params.add(nivel); }
        if (minimo != null) { sql.append(" AND v.acumulado_12m >= ?"); params.add(minimo); }
        if (maximo != null) { sql.append(" AND v.acumulado_12m <= ?"); params.add(maximo); }
        sql.append(" ORDER BY v.acumulado_12m DESC, c.nombres");
        return jdbc.queryForList(sql.toString(), params.toArray());
    }
}

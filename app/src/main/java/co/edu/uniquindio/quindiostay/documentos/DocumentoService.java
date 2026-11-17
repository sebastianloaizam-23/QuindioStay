package co.edu.uniquindio.quindiostay.documentos;

import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import com.lowagie.text.Document;
import com.lowagie.text.DocumentException;

import co.edu.uniquindio.quindiostay.pdf.PdfService;
import co.edu.uniquindio.quindiostay.web.Formato;

/**
 * Plantillas de documentos generadas desde el sistema (Entrega 3):
 * comprobante de reserva, comprobante de pago, factura, autorización de tratamiento
 * de datos (con versión para menores de edad) y certificado de puntos.
 * Cada una toma los datos reales de la base con un SELECT y los pone en un PDF.
 */
@Service
public class DocumentoService {

    private final JdbcTemplate jdbc;
    private final PdfService pdf;
    private final Formato fmt;

    public DocumentoService(JdbcTemplate jdbc, PdfService pdf, Formato fmt) {
        this.jdbc = jdbc;
        this.pdf = pdf;
        this.fmt = fmt;
    }

    /** Respuesta HTTP que abre el PDF en el navegador. */
    public static ResponseEntity<byte[]> respuesta(byte[] contenido, String archivo) {
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + archivo + "\"")
                .body(contenido);
    }

    private interface Contenido {
        void escribir(Document doc) throws DocumentException;
    }

    private byte[] generar(Contenido contenido) {
        ByteArrayOutputStream salida = new ByteArrayOutputStream();
        Document doc = pdf.abrir(salida, false);
        try {
            contenido.escribir(doc);
            pdf.pie(doc);
        } catch (DocumentException e) {
            throw new IllegalStateException("No se pudo generar el PDF", e);
        } finally {
            doc.close();
        }
        return salida.toByteArray();
    }

    private Map<String, Object> uno(String sql, Object id, String queNoExiste) {
        try {
            return jdbc.queryForMap(sql, id);
        } catch (EmptyResultDataAccessException e) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, queNoExiste);
        }
    }

    private String v(Object o) { return fmt.v(o); }
    private String plata(Object o) { return fmt.dinero(o); }

    // ---------------------------------------------------------------- 1. Comprobante de reserva

    public byte[] comprobanteReserva(int idReserva) {
        Map<String, Object> r = uno("""
                SELECT r.id_reserva, r.fecha_creacion, r.fecha_servicio, r.estado, r.valor_total, r.canal,
                       c.nombres, c.apellidos, c.tipo_documento, c.numero_documento, c.correo, c.telefono,
                       pl.nombre AS plan, pl.tipo AS tipo_plan, pl.descripcion,
                       p.nombre_comercial, p.nit_documento, p.rnt, p.direccion, p.telefono AS telefono_prestador,
                       m.nombre AS municipio
                FROM reserva r
                JOIN cliente c   ON c.id_cliente = r.id_cliente
                JOIN plan pl     ON pl.id_plan = r.id_plan
                JOIN prestador p ON p.id_prestador = pl.id_prestador
                JOIN municipio m ON m.id_municipio = p.id_municipio
                WHERE r.id_reserva = ?
                """, idReserva, "No existe la reserva " + idReserva);
        Map<String, Object> pagos = totalesPagos(idReserva);

        return generar(doc -> {
            pdf.encabezado(doc, "Comprobante de reserva N.º " + r.get("id_reserva"),
                    "Constancia de la reserva registrada en QuindioStay");
            doc.add(pdf.seccion("Datos de la reserva"));
            doc.add(pdf.datos(new String[][]{
                    {"Fecha de creación", v(r.get("fecha_creacion"))},
                    {"Fecha del servicio", v(r.get("fecha_servicio"))},
                    {"Estado", v(r.get("estado"))},
                    {"Canal", v(r.get("canal"))},
                    {"Valor total", plata(r.get("valor_total"))},
                    {"Pagado (pagos exitosos)", plata(pagos.get("exitosos"))}}));
            doc.add(pdf.seccion("Cliente"));
            doc.add(pdf.datos(new String[][]{
                    {"Nombre", r.get("nombres") + " " + r.get("apellidos")},
                    {"Documento", r.get("tipo_documento") + " " + r.get("numero_documento")},
                    {"Correo", v(r.get("correo"))},
                    {"Teléfono", v(r.get("telefono"))}}));
            doc.add(pdf.seccion("Plan y prestador"));
            doc.add(pdf.datos(new String[][]{
                    {"Plan", r.get("plan") + " (" + r.get("tipo_plan") + ")"},
                    {"Descripción", v(r.get("descripcion"))},
                    {"Prestador", v(r.get("nombre_comercial"))},
                    {"NIT / RNT", r.get("nit_documento") + " / " + v(r.get("rnt"))},
                    {"Dirección", r.get("direccion") + ", " + r.get("municipio") + " (Quindío)"},
                    {"Teléfono", v(r.get("telefono_prestador"))}}));
            doc.add(pdf.parrafo(" "));
            doc.add(pdf.parrafo("Presente este comprobante el día del servicio. Las cancelaciones con más de 5 días "
                    + "de anticipación tienen reembolso del 80 % de lo pagado. Si es miembro del programa de "
                    + "fidelización y el prestador es aliado, los puntos se acumulan automáticamente cuando la "
                    + "reserva queda completada."));
        });
    }

    private Map<String, Object> totalesPagos(int idReserva) {
        return jdbc.queryForMap("""
                SELECT COALESCE(SUM(CASE WHEN estado = 'exitoso' THEN monto END), 0)     AS exitosos,
                       COALESCE(SUM(CASE WHEN estado = 'reembolsado' THEN monto END), 0) AS reembolsado
                FROM pago WHERE id_reserva = ?
                """, idReserva);
    }

    // ---------------------------------------------------------------- 2. Comprobante de pago

    public byte[] comprobantePago(int idPago) {
        Map<String, Object> p = uno("""
                SELECT pg.id_pago, pg.fecha, pg.monto, pg.metodo, pg.estado, pg.referencia_transaccion,
                       r.id_reserva, r.fecha_servicio, r.valor_total,
                       c.nombres, c.apellidos, c.tipo_documento, c.numero_documento,
                       pl.nombre AS plan, pr.nombre_comercial
                FROM pago pg
                JOIN reserva r    ON r.id_reserva = pg.id_reserva
                JOIN cliente c    ON c.id_cliente = r.id_cliente
                JOIN plan pl      ON pl.id_plan = r.id_plan
                JOIN prestador pr ON pr.id_prestador = pl.id_prestador
                WHERE pg.id_pago = ?
                """, idPago, "No existe el pago " + idPago);
        Map<String, Object> pagos = totalesPagos(((Number) p.get("id_reserva")).intValue());
        BigDecimal saldo = ((BigDecimal) p.get("valor_total")).subtract((BigDecimal) pagos.get("exitosos"));

        return generar(doc -> {
            pdf.encabezado(doc, "Comprobante de pago N.º " + p.get("id_pago"),
                    "Constancia del pago aplicado a la reserva N.º " + p.get("id_reserva"));
            doc.add(pdf.seccion("Pago"));
            doc.add(pdf.datos(new String[][]{
                    {"Fecha", v(p.get("fecha"))},
                    {"Monto", plata(p.get("monto"))},
                    {"Método", v(p.get("metodo"))},
                    {"Estado", v(p.get("estado"))},
                    {"Referencia", p.get("referencia_transaccion") == null ? "No aplica" : v(p.get("referencia_transaccion"))}}));
            doc.add(pdf.seccion("Reserva"));
            doc.add(pdf.datos(new String[][]{
                    {"Cliente", p.get("nombres") + " " + p.get("apellidos")},
                    {"Documento", p.get("tipo_documento") + " " + p.get("numero_documento")},
                    {"Plan", p.get("plan") + " - " + p.get("nombre_comercial")},
                    {"Fecha del servicio", v(p.get("fecha_servicio"))},
                    {"Valor total", plata(p.get("valor_total"))},
                    {"Total pagado", plata(pagos.get("exitosos"))},
                    {"Saldo pendiente", plata(saldo.signum() < 0 ? BigDecimal.ZERO : saldo)}}));
        });
    }

    // ---------------------------------------------------------------- 3. Factura

    public byte[] factura(int idReserva) {
        Map<String, Object> r = uno("""
                SELECT r.id_reserva, r.fecha_creacion, r.fecha_servicio, r.valor_total, r.estado,
                       c.nombres, c.apellidos, c.tipo_documento, c.numero_documento, c.correo, c.ciudad_origen,
                       pl.nombre AS plan, pl.tipo AS tipo_plan,
                       p.nombre_comercial, p.nit_documento, p.rnt, p.direccion, p.telefono, p.correo AS correo_prestador,
                       m.nombre AS municipio
                FROM reserva r
                JOIN cliente c   ON c.id_cliente = r.id_cliente
                JOIN plan pl     ON pl.id_plan = r.id_plan
                JOIN prestador p ON p.id_prestador = pl.id_prestador
                JOIN municipio m ON m.id_municipio = p.id_municipio
                WHERE r.id_reserva = ?
                """, idReserva, "No existe la reserva " + idReserva);
        List<Map<String, Object>> pagos = jdbc.queryForList("""
                SELECT id_pago AS pago, fecha, metodo, estado, monto
                FROM pago WHERE id_reserva = ? ORDER BY fecha
                """, idReserva);
        Map<String, Object> totales = totalesPagos(idReserva);
        BigDecimal saldo = ((BigDecimal) r.get("valor_total")).subtract((BigDecimal) totales.get("exitosos"));

        return generar(doc -> {
            pdf.encabezado(doc, "Factura de venta N.º FV-" + String.format("%06d", idReserva),
                    "Fecha de expedición: " + v(r.get("fecha_creacion")));
            doc.add(pdf.seccion("Vendedor (prestador)"));
            doc.add(pdf.datos(new String[][]{
                    {"Razón social", v(r.get("nombre_comercial"))},
                    {"NIT", v(r.get("nit_documento"))},
                    {"RNT", v(r.get("rnt"))},
                    {"Dirección", r.get("direccion") + ", " + r.get("municipio") + " (Quindío)"},
                    {"Contacto", r.get("telefono") + " · " + r.get("correo_prestador")}}));
            doc.add(pdf.seccion("Comprador (cliente)"));
            doc.add(pdf.datos(new String[][]{
                    {"Nombre", r.get("nombres") + " " + r.get("apellidos")},
                    {"Documento", r.get("tipo_documento") + " " + r.get("numero_documento")},
                    {"Correo", v(r.get("correo"))},
                    {"Ciudad", v(r.get("ciudad_origen"))}}));
            doc.add(pdf.seccion("Detalle"));
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("descripcion", "Plan " + r.get("plan") + " (" + r.get("tipo_plan") + ") - servicio del " + v(r.get("fecha_servicio")));
            item.put("cantidad", 1);
            item.put("valor_unitario", plata(r.get("valor_total")));
            item.put("total", plata(r.get("valor_total")));
            doc.add(pdf.tabla(List.of(item)));
            doc.add(pdf.seccion("Pagos recibidos"));
            doc.add(pdf.tabla(pagos));
            doc.add(pdf.datos(new String[][]{
                    {"TOTAL FACTURA", plata(r.get("valor_total"))},
                    {"Total pagado", plata(totales.get("exitosos"))},
                    {"Reembolsado", plata(totales.get("reembolsado"))},
                    {"Saldo pendiente", plata(saldo.signum() < 0 ? BigDecimal.ZERO : saldo)}}));
            doc.add(pdf.parrafo(" "));
            doc.add(pdf.parrafo("Documento generado con fines académicos dentro del proyecto QuindioStay; "
                    + "no reemplaza la factura electrónica de venta."));
        });
    }

    // ---------------------------------------------------------------- 4. Autorización de datos

    public byte[] autorizacion(int idCliente) {
        Map<String, Object> c = uno("""
                SELECT c.*, TIMESTAMPDIFF(YEAR, c.fecha_nacimiento, CURDATE()) AS edad,
                       pc.canal_preferido, pc.acepta_promociones
                FROM cliente c
                LEFT JOIN perfil_cliente pc ON pc.id_cliente = c.id_cliente
                WHERE c.id_cliente = ?
                """, idCliente, "No existe el cliente " + idCliente);
        int edad = ((Number) c.get("edad")).intValue();
        boolean menor = edad < 18;
        Object promo = c.get("acepta_promociones");
        boolean acepta = Boolean.TRUE.equals(promo) || (promo instanceof Number n && n.intValue() == 1);
        String nombre = c.get("nombres") + " " + c.get("apellidos");

        return generar(doc -> {
            pdf.encabezado(doc, menor
                            ? "Autorización de tratamiento de datos personales de menor de edad"
                            : "Autorización de tratamiento de datos personales",
                    "Ley 1581 de 2012 (protección de datos personales en Colombia)");
            doc.add(pdf.seccion("Titular de los datos"));
            doc.add(pdf.datos(new String[][]{
                    {"Nombre", nombre},
                    {"Documento", c.get("tipo_documento") + " " + c.get("numero_documento")},
                    {"Fecha de nacimiento", v(c.get("fecha_nacimiento")) + " (" + edad + " años)"},
                    {"Correo", v(c.get("correo"))},
                    {"Teléfono", v(c.get("telefono"))}}));
            if (menor) {
                doc.add(pdf.seccion("Representante legal (padre, madre o tutor)"));
                doc.add(pdf.datos(new String[][]{
                        {"Nombre", ""}, {"Documento", ""}, {"Parentesco", ""}, {"Teléfono", ""}}));
                doc.add(pdf.parrafo(" "));
                doc.add(pdf.parrafo("Como representante legal del menor, autorizo a QuindioStay a recolectar y tratar "
                        + "sus datos personales para las finalidades descritas abajo. Declaro que se respetó el interés "
                        + "superior del menor y que puede ejercer sus derechos por medio de mí."));
            } else {
                doc.add(pdf.parrafo(" "));
                doc.add(pdf.parrafo("Yo, " + nombre + ", autorizo de manera libre, previa y expresa a QuindioStay "
                        + "para recolectar, almacenar, usar y actualizar mis datos personales para las finalidades "
                        + "descritas abajo."));
            }
            doc.add(pdf.seccion("Finalidades"));
            doc.add(pdf.parrafo("1. Gestionar reservas, pagos y comprobantes de los servicios turísticos.\n"
                    + "2. Enviar recordatorios y notificaciones por el canal preferido ("
                    + (c.get("canal_preferido") == null ? "correo" : c.get("canal_preferido")) + ").\n"
                    + "3. Administrar la membresía del programa de fidelización (puntos, niveles y canjes).\n"
                    + "4. Enviar promociones, solo si el titular lo acepta."));
            doc.add(pdf.datos(new String[][]{
                    {"¿Acepta promociones?", acepta ? "[X] Sí    [  ] No" : "[  ] Sí    [X] No"}}));
            doc.add(pdf.seccion("Derechos del titular"));
            doc.add(pdf.parrafo("El titular puede conocer, actualizar, rectificar y pedir la supresión de sus datos, "
                    + "así como revocar esta autorización, escribiendo a los canales de atención de QuindioStay."));
            if (menor) {
                doc.add(pdf.firmas("Firma del representante legal", "Firma del menor (opcional)"));
            } else {
                doc.add(pdf.firmas("Firma del titular", "Fecha"));
            }
        });
    }

    // ---------------------------------------------------------------- 5. Certificado de puntos

    public byte[] certificadoPuntos(int idCliente) {
        List<Map<String, Object>> filas = jdbc.queryForList("""
                SELECT c.nombres, c.apellidos, c.tipo_documento, c.numero_documento,
                       mb.id_membresia, mb.fecha_inscripcion, s.saldo, n.acumulado_12m, n.nivel
                FROM cliente c
                JOIN membresia mb        ON mb.id_cliente = c.id_cliente
                JOIN v_saldo_membresia s ON s.id_membresia = mb.id_membresia
                JOIN v_nivel_membresia n ON n.id_membresia = mb.id_membresia
                WHERE c.id_cliente = ?
                """, idCliente);
        if (filas.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "El cliente " + idCliente + " no tiene membresía en el programa de puntos");
        }
        Map<String, Object> m = filas.get(0);
        List<Map<String, Object>> movimientos = jdbc.queryForList("""
                SELECT mp.fecha, mp.tipo, mp.cantidad AS puntos, b.nombre AS beneficio
                FROM movimiento_puntos mp
                LEFT JOIN redencion r ON r.id_movimiento = mp.id_movimiento
                LEFT JOIN beneficio b ON b.id_beneficio = r.id_beneficio
                WHERE mp.id_membresia = ?
                ORDER BY mp.fecha DESC
                LIMIT 15
                """, m.get("id_membresia"));

        return generar(doc -> {
            pdf.encabezado(doc, "Certificado de puntos", "Programa de fidelización QuindioStay");
            doc.add(pdf.parrafo("QuindioStay certifica que " + m.get("nombres") + " " + m.get("apellidos")
                    + ", identificado(a) con " + m.get("tipo_documento") + " " + m.get("numero_documento")
                    + ", es miembro del programa de fidelización con la siguiente información:"));
            doc.add(pdf.datos(new String[][]{
                    {"Membresía N.º", v(m.get("id_membresia"))},
                    {"Fecha de inscripción", v(m.get("fecha_inscripcion"))},
                    {"Nivel actual", String.valueOf(m.get("nivel")).toUpperCase()},
                    {"Puntos acumulados (últimos 12 meses)", fmt.numero(m.get("acumulado_12m"))},
                    {"Saldo disponible", fmt.numero(m.get("saldo")) + " puntos"}}));
            doc.add(pdf.seccion("Últimos movimientos"));
            doc.add(pdf.tabla(movimientos));
            doc.add(pdf.parrafo(" "));
            doc.add(pdf.parrafo("El nivel se calcula con las acumulaciones de los últimos 12 meses y el saldo con la suma "
                    + "de todos los movimientos. Los puntos vencen a los 12 meses si no se han usado."));
        });
    }
}

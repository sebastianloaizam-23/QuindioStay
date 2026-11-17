package co.edu.uniquindio.quindiostay.crud;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

/**
 * Catálogo de las 17 tablas. En vez de escribir 17 pantallas, el CRUD lee la estructura
 * de cada tabla desde information_schema (columnas, tipos, PK, FK) y arma el formulario solo.
 * Aquí también están los valores permitidos de los CHECK (para los menús desplegables)
 * y cómo mostrar cada llave foránea con un nombre entendible.
 */
@Component
public class CatalogoTablas {

    /** Solo estas tablas se pueden usar en el CRUD (protege contra inyección SQL). */
    public static final Map<String, String> TITULOS = new LinkedHashMap<>();
    static {
        TITULOS.put("municipio", "Municipios");
        TITULOS.put("tipo_prestador", "Tipos de prestador");
        TITULOS.put("prestador", "Prestadores");
        TITULOS.put("cliente", "Clientes");
        TITULOS.put("perfil_cliente", "Perfiles de cliente");
        TITULOS.put("plan", "Planes");
        TITULOS.put("reserva", "Reservas");
        TITULOS.put("pago", "Pagos");
        TITULOS.put("notificacion", "Notificaciones");
        TITULOS.put("rol", "Roles");
        TITULOS.put("usuario_sistema", "Usuarios del sistema");
        TITULOS.put("nivel", "Niveles");
        TITULOS.put("membresia", "Membresías");
        TITULOS.put("prestador_aliado", "Prestadores aliados");
        TITULOS.put("beneficio", "Beneficios");
        TITULOS.put("movimiento_puntos", "Movimientos de puntos");
        TITULOS.put("redencion", "Redenciones");
    }

    public static final Set<String> FIDELIZACION = Set.of("nivel", "membresia", "prestador_aliado",
            "beneficio", "movimiento_puntos", "redencion");
    public static final Set<String> SOLO_ADMIN = Set.of("rol", "usuario_sistema");
    public static final Set<String> SOLO_LECTURA = Set.of("redencion");
    public static final Set<String> SIN_EDITAR = Set.of("movimiento_puntos");

    /** Valores permitidos, copiados de los CHECK de 01_ddl.sql (con tildes). */
    private static final Map<String, List<String>> OPCIONES = Map.ofEntries(
            Map.entry("prestador.estado", List.of("activo", "suspendido", "retirado")),
            Map.entry("cliente.tipo_documento", List.of("cédula de ciudadanía", "cédula de extranjería", "pasaporte", "tarjeta de identidad")),
            Map.entry("perfil_cliente.idioma", List.of("español", "inglés")),
            Map.entry("perfil_cliente.canal_preferido", List.of("correo", "SMS", "Whatsapp")),
            Map.entry("perfil_cliente.anticipacion_recordatorio", List.of("1", "3", "7")),
            Map.entry("perfil_cliente.acepta_promociones", List.of("0", "1")),
            Map.entry("plan.tipo", List.of("básico", "estándar", "premium", "todo incluido")),
            Map.entry("plan.estado", List.of("activo", "inactivo")),
            Map.entry("reserva.estado", List.of("pendiente", "confirmada", "cancelada", "completada")),
            Map.entry("reserva.canal", List.of("web", "aplicación móvil", "teléfono", "mostrador", "agencia intermediaria")),
            Map.entry("pago.metodo", List.of("tarjeta de crédito", "tarjeta débito", "PSE", "transferencia", "efectivo")),
            Map.entry("pago.estado", List.of("exitoso", "fallido", "pendiente", "reembolsado")),
            Map.entry("notificacion.tipo", List.of("recordatorio de reserva", "confirmación de reserva", "confirmación de pago",
                    "cambio de estado", "promoción", "subida de nivel", "puntos próximos a vencer")),
            Map.entry("notificacion.canal", List.of("correo", "SMS", "Whatsapp")),
            Map.entry("notificacion.estado", List.of("pendiente", "enviada", "fallida", "leída")),
            Map.entry("rol.nombre", List.of("administrador", "encargado de prestador", "operador")),
            Map.entry("usuario_sistema.estado", List.of("activo", "bloqueado")),
            Map.entry("nivel.nombre", List.of("bronce", "plata", "oro", "platino")),
            Map.entry("movimiento_puntos.tipo", List.of("acumulación", "redención", "vencimiento", "ajuste manual")));

    /** Consulta (id, etiqueta) para mostrar cada FK con un nombre y no solo con el número. */
    private static final Map<String, String> ETIQUETAS = Map.ofEntries(
            Map.entry("municipio", "SELECT id_municipio AS id, nombre AS etiqueta FROM municipio ORDER BY nombre"),
            Map.entry("tipo_prestador", "SELECT id_tipo_prestador AS id, nombre AS etiqueta FROM tipo_prestador ORDER BY nombre"),
            Map.entry("prestador", "SELECT id_prestador AS id, nombre_comercial AS etiqueta FROM prestador ORDER BY nombre_comercial"),
            Map.entry("cliente", "SELECT id_cliente AS id, CONCAT(nombres, ' ', apellidos, ' (', correo, ')') AS etiqueta FROM cliente ORDER BY nombres, apellidos"),
            Map.entry("plan", "SELECT pl.id_plan AS id, CONCAT(pl.nombre, ' - ', p.nombre_comercial) AS etiqueta FROM plan pl JOIN prestador p ON p.id_prestador = pl.id_prestador ORDER BY p.nombre_comercial, pl.nombre"),
            Map.entry("reserva", "SELECT r.id_reserva AS id, CONCAT(c.nombres, ' ', c.apellidos, ' - ', r.fecha_servicio, ' - ', r.estado) AS etiqueta FROM reserva r JOIN cliente c ON c.id_cliente = r.id_cliente ORDER BY r.id_reserva DESC"),
            Map.entry("rol", "SELECT id_rol AS id, nombre AS etiqueta FROM rol ORDER BY id_rol"),
            Map.entry("nivel", "SELECT id_nivel AS id, CONCAT(nombre, ' (desde ', umbral_minimo, ' pts)') AS etiqueta FROM nivel ORDER BY umbral_minimo"),
            Map.entry("membresia", "SELECT m.id_membresia AS id, CONCAT(c.nombres, ' ', c.apellidos) AS etiqueta FROM membresia m JOIN cliente c ON c.id_cliente = m.id_cliente ORDER BY c.nombres"),
            Map.entry("prestador_aliado", "SELECT pa.id_prestador AS id, p.nombre_comercial AS etiqueta FROM prestador_aliado pa JOIN prestador p ON p.id_prestador = pa.id_prestador ORDER BY p.nombre_comercial"),
            Map.entry("beneficio", "SELECT id_beneficio AS id, CONCAT(nombre, ' (', costo_puntos, ' pts)') AS etiqueta FROM beneficio ORDER BY nombre"),
            Map.entry("movimiento_puntos", "SELECT id_movimiento AS id, CONCAT(tipo, ' ', cantidad, ' - ', DATE(fecha)) AS etiqueta FROM movimiento_puntos ORDER BY id_movimiento DESC"));

    private final JdbcTemplate jdbc;
    private final Map<String, Tabla> cache = new ConcurrentHashMap<>();

    public CatalogoTablas(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** Estructura de una tabla (se lee una sola vez y queda guardada). */
    public Tabla info(String nombre) {
        if (!TITULOS.containsKey(nombre)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "La tabla " + nombre + " no existe");
        }
        return cache.computeIfAbsent(nombre, this::cargar);
    }

    private Tabla cargar(String nombre) {
        Map<String, String> fks = new HashMap<>();
        for (Map<String, Object> f : jdbc.queryForList("""
                SELECT column_name AS col, referenced_table_name AS ref
                FROM information_schema.key_column_usage
                WHERE table_schema = DATABASE() AND table_name = ? AND referenced_table_name IS NOT NULL
                """, nombre)) {
            fks.put(texto(f.get("col")), texto(f.get("ref")));
        }

        List<Columna> columnas = new ArrayList<>();
        for (Map<String, Object> c : jdbc.queryForList("""
                SELECT column_name AS col, data_type AS tipo, is_nullable AS nula, extra AS extra,
                       column_key AS llave, column_default AS defecto, column_comment AS comentario
                FROM information_schema.columns
                WHERE table_schema = DATABASE() AND table_name = ?
                ORDER BY ordinal_position
                """, nombre)) {
            String col = texto(c.get("col"));
            String extra = texto(c.get("extra"));
            extra = extra == null ? "" : extra.toLowerCase();
            String comentario = texto(c.get("comentario"));
            columnas.add(new Columna(col, texto(c.get("tipo")).toLowerCase(), "YES".equals(texto(c.get("nula"))),
                    extra.contains("auto_increment"), "PRI".equals(texto(c.get("llave"))),
                    texto(c.get("defecto")), extra.contains("default_generated"),
                    comentario == null || comentario.isBlank() ? null : comentario,
                    fks.get(col), OPCIONES.get(nombre + "." + col)));
        }

        String descripcion = jdbc.queryForObject("""
                SELECT table_comment FROM information_schema.tables
                WHERE table_schema = DATABASE() AND table_name = ?
                """, String.class, nombre);
        return new Tabla(nombre, TITULOS.get(nombre), descripcion, columnas);
    }

    /** Lista id -> etiqueta de una tabla referenciada (para los menús de las FK). */
    public Map<String, String> etiquetas(String tablaReferida) {
        String sql = ETIQUETAS.get(tablaReferida);
        Map<String, String> resultado = new LinkedHashMap<>();
        if (sql == null) return resultado;
        for (Map<String, Object> f : jdbc.queryForList(sql)) {
            resultado.put(String.valueOf(f.get("id")), String.valueOf(f.get("etiqueta")));
        }
        return resultado;
    }

    private static String texto(Object o) {
        if (o == null) return null;
        if (o instanceof byte[] b) return new String(b, java.nio.charset.StandardCharsets.UTF_8);
        return o.toString();
    }
}

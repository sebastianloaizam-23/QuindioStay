package co.edu.uniquindio.quindiostay.crud;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

/**
 * Las cuatro operaciones del CRUD con SQL escrito a mano:
 *   Create = INSERT, Read = SELECT, Update = UPDATE, Delete = DELETE.
 * Los valores siempre van con "?" (PreparedStatement), nunca pegados al texto del SQL.
 */
@Service
public class CrudService {

    public static final int TAMANO_PAGINA = 15;

    private final JdbcTemplate jdbc;
    private final PasswordEncoder encoder;

    public CrudService(JdbcTemplate jdbc, PasswordEncoder encoder) {
        this.jdbc = jdbc;
        this.encoder = encoder;
    }

    /** SELECT con búsqueda y paginación. Los más nuevos primero. */
    public List<Map<String, Object>> listar(Tabla t, String buscar, int pagina) {
        List<Object> params = new ArrayList<>();
        String where = filtro(t, buscar, params);
        params.add(TAMANO_PAGINA);
        params.add(Math.max(0, pagina - 1) * TAMANO_PAGINA);
        return jdbc.queryForList("SELECT * FROM `" + t.getNombre() + "`" + where
                + " ORDER BY `" + t.getPk().getNombre() + "` DESC LIMIT ? OFFSET ?", params.toArray());
    }

    public long contar(Tabla t, String buscar) {
        List<Object> params = new ArrayList<>();
        String where = filtro(t, buscar, params);
        Long n = jdbc.queryForObject("SELECT COUNT(*) FROM `" + t.getNombre() + "`" + where, Long.class, params.toArray());
        return n == null ? 0 : n;
    }

    /** Busca el texto en todas las columnas (menos las contraseñas). */
    private String filtro(Tabla t, String buscar, List<Object> params) {
        if (buscar == null || buscar.isBlank()) return "";
        String columnas = t.getColumnas().stream()
                .filter(c -> !c.isClave())
                .map(c -> "`" + c.getNombre() + "`")
                .collect(Collectors.joining(", "));
        params.add("%" + buscar.trim() + "%");
        return " WHERE CONCAT_WS(' ', " + columnas + ") LIKE ?";
    }

    public Map<String, Object> buscar(Tabla t, String id) {
        return jdbc.queryForMap("SELECT * FROM `" + t.getNombre() + "` WHERE `" + t.getPk().getNombre() + "` = ?", id);
    }

    /** INSERT (nuevo = true) o UPDATE (nuevo = false). */
    public void guardar(Tabla t, Map<String, String> datos, boolean nuevo, String id) {
        List<String> columnas = new ArrayList<>();
        List<Object> valores = new ArrayList<>();

        for (Columna c : t.getColumnas()) {
            if (c.isAutoIncremental()) continue;          // lo pone MySQL
            if (!nuevo && c.isPk()) continue;             // la llave no se cambia
            String v = datos.get(c.getNombre());
            v = v == null ? null : v.trim();

            if (c.isClave()) {
                // Contraseña: vacía = no cambiarla; si trae algo se cifra con BCrypt antes de guardar
                if (v == null || v.isEmpty()) continue;
                v = encoder.encode(v);
            } else if (v == null || v.isEmpty()) {
                if (nuevo && c.getPorDefecto() != null) continue;   // usa el DEFAULT de la tabla
                v = null;
            } else if ("datetime-local".equals(c.getTipoInput())) {
                v = v.replace('T', ' ');                            // 2026-11-13T10:00 -> 2026-11-13 10:00
            }
            columnas.add(c.getNombre());
            valores.add(v);
        }

        if (nuevo) {
            String cols = columnas.stream().map(c -> "`" + c + "`").collect(Collectors.joining(", "));
            String signos = columnas.stream().map(c -> "?").collect(Collectors.joining(", "));
            jdbc.update("INSERT INTO `" + t.getNombre() + "` (" + cols + ") VALUES (" + signos + ")", valores.toArray());
        } else {
            if (columnas.isEmpty()) return;
            String sets = columnas.stream().map(c -> "`" + c + "` = ?").collect(Collectors.joining(", "));
            valores.add(id);
            jdbc.update("UPDATE `" + t.getNombre() + "` SET " + sets + " WHERE `" + t.getPk().getNombre() + "` = ?",
                    valores.toArray());
        }
    }

    public void eliminar(Tabla t, String id) {
        jdbc.update("DELETE FROM `" + t.getNombre() + "` WHERE `" + t.getPk().getNombre() + "` = ?", id);
    }
}

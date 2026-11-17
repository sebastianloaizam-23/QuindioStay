package co.edu.uniquindio.quindiostay.seguridad;

import java.util.List;
import java.util.Map;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

/**
 * Busca a quien intenta entrar (RF-01).
 * 1) Primero en usuario_sistema por nombre_usuario (administrador, encargado, operador).
 * 2) Si no está, en cliente por correo (solo los que tienen contraseña: los de
 *    mostrador tienen contrasena_cifrada NULL y no pueden entrar).
 * Spring Security compara luego la clave escrita con el hash BCrypt de la base.
 */
@Service
public class UsuarioDetailsService implements UserDetailsService {

    private final JdbcTemplate jdbc;

    public UsuarioDetailsService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public UserDetails loadUserByUsername(String login) throws UsernameNotFoundException {
        String l = login == null ? "" : login.trim();

        List<Map<String, Object>> internos = jdbc.queryForList("""
                SELECT u.id_usuario, u.nombre_usuario, u.contrasena_cifrada, u.estado,
                       u.id_prestador, r.nombre AS rol
                FROM usuario_sistema u
                JOIN rol r ON r.id_rol = u.id_rol
                WHERE u.nombre_usuario = ?
                """, l);
        if (!internos.isEmpty()) {
            Map<String, Object> u = internos.get(0);
            String rol = (String) u.get("rol");
            String autoridad = switch (rol) {
                case "administrador" -> "ROLE_ADMIN";
                case "encargado de prestador" -> "ROLE_ENCARGADO";
                default -> "ROLE_OPERADOR";
            };
            // Un usuario bloqueado queda "deshabilitado": Spring lo rechaza aunque la clave sea correcta
            boolean activo = "activo".equals(u.get("estado"));
            return new UsuarioActual((String) u.get("nombre_usuario"), (String) u.get("contrasena_cifrada"),
                    activo, List.of(new SimpleGrantedAuthority(autoridad)), "INTERNO",
                    entero(u.get("id_usuario")), entero(u.get("id_prestador")),
                    (String) u.get("nombre_usuario"), rol);
        }

        List<Map<String, Object>> clientes = jdbc.queryForList("""
                SELECT id_cliente, correo, contrasena_cifrada, nombres, apellidos
                FROM cliente
                WHERE correo = ? AND contrasena_cifrada IS NOT NULL
                """, l);
        if (!clientes.isEmpty()) {
            Map<String, Object> c = clientes.get(0);
            return new UsuarioActual((String) c.get("correo"), (String) c.get("contrasena_cifrada"),
                    true, List.of(new SimpleGrantedAuthority("ROLE_CLIENTE")), "CLIENTE",
                    entero(c.get("id_cliente")), null,
                    c.get("nombres") + " " + c.get("apellidos"), "cliente");
        }

        throw new UsernameNotFoundException("No existe el usuario o correo " + l);
    }

    private static Integer entero(Object o) {
        return o == null ? null : ((Number) o).intValue();
    }
}

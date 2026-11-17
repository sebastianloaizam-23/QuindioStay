package co.edu.uniquindio.quindiostay.seguridad;

import java.util.Collection;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.User;

/**
 * La persona que inició sesión. Extiende el User de Spring Security y le agrega
 * los datos que la aplicación necesita: si es cliente o usuario interno, su id
 * y, para el encargado, el prestador que administra.
 */
public class UsuarioActual extends User {

    private final String tipo;          // "INTERNO" o "CLIENTE"
    private final Integer id;           // id_usuario o id_cliente
    private final Integer idPrestador;  // solo para el encargado de prestador
    private final String nombreMostrar;
    private final String rol;           // nombre del rol tal como está en la base

    public UsuarioActual(String login, String hash, boolean activo,
                         Collection<? extends GrantedAuthority> autoridades,
                         String tipo, Integer id, Integer idPrestador,
                         String nombreMostrar, String rol) {
        super(login, hash, activo, true, true, true, autoridades);
        this.tipo = tipo;
        this.id = id;
        this.idPrestador = idPrestador;
        this.nombreMostrar = nombreMostrar;
        this.rol = rol;
    }

    public boolean tieneRol(String rol) {
        return getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_" + rol));
    }

    public String getTipo() { return tipo; }
    public Integer getId() { return id; }
    public Integer getIdPrestador() { return idPrestador; }
    public String getNombreMostrar() { return nombreMostrar; }
    public String getRol() { return rol; }
}

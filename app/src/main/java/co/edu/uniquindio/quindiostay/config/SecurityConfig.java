package co.edu.uniquindio.quindiostay.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;

import co.edu.uniquindio.quindiostay.seguridad.UsuarioDetailsService;

/**
 * Reglas de acceso por rol (RF-01).
 *  - ADMIN: todo.
 *  - OPERADOR: CRUD (menos roles y usuarios), redenciones, documentos y reportes.
 *  - ENCARGADO: su panel (beneficios y movimientos de su prestador) y reportes.
 *  - CLIENTE: su cuenta (membresía, puntos, reservas, canjes).
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    /** Las contraseñas de la base están en BCrypt ($2b$...). */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public SecurityFilterChain cadenaSeguridad(HttpSecurity http, UsuarioDetailsService usuarios) throws Exception {
        http
            .authorizeHttpRequests(a -> a
                .requestMatchers("/login", "/css/**", "/img/**", "/error").permitAll()
                .requestMatchers("/crud/rol/**", "/crud/usuario_sistema/**").hasRole("ADMIN")
                .requestMatchers("/crud/**", "/redimir/**", "/documentos/**").hasAnyRole("ADMIN", "OPERADOR")
                .requestMatchers("/reportes/**").hasAnyRole("ADMIN", "OPERADOR", "ENCARGADO")
                .requestMatchers("/encargado/**").hasRole("ENCARGADO")
                .requestMatchers("/mi-cuenta/**").hasRole("CLIENTE")
                .anyRequest().authenticated())
            .formLogin(f -> f
                .loginPage("/login")
                .usernameParameter("usuario")
                .passwordParameter("clave")
                .defaultSuccessUrl("/", true)
                // Si el usuario está bloqueado mostramos un mensaje distinto al de clave incorrecta
                .failureHandler((req, res, ex) -> res.sendRedirect(req.getContextPath()
                        + (ex instanceof DisabledException ? "/login?bloqueado" : "/login?error")))
                .permitAll())
            // Casilla "Recordarme" del prototipo de login
            .rememberMe(r -> r
                .key("quindiostay-recordarme")
                .rememberMeParameter("recordarme")
                .userDetailsService(usuarios)
                .tokenValiditySeconds(7 * 24 * 3600))
            .logout(l -> l.logoutSuccessUrl("/login?salir").permitAll());
        return http.build();
    }
}

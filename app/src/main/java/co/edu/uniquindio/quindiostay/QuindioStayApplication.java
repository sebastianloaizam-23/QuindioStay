package co.edu.uniquindio.quindiostay;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Punto de entrada. Al ejecutar esta clase, Spring Boot arranca un servidor web
 * en http://localhost:8080 y se conecta a MySQL con los datos de application.properties.
 */
@SpringBootApplication
public class QuindioStayApplication {

    public static void main(String[] args) {
        SpringApplication.run(QuindioStayApplication.class, args);
    }
}

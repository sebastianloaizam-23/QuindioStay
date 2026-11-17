# QuindioStay · Manual de instalación

Proyecto final de Bases de Datos I, Universidad del Quindío (Entrega 3).
Aplicación web en Java 21 + Spring Boot 3, con Thymeleaf y Bootstrap, conectada a MySQL 8.4 por JDBC con SQL escrito a mano.

## Contenido del repositorio

```
sql/01_ddl.sql        crea la base quindiostay, las 17 tablas y 2 vistas
sql/02_triggers.sql   13 triggers, 1 función, 5 procedimientos y la vista v_nivel_membresia
sql/03_datos.sql      datos de ejemplo
sql/04_reportes.sql   las 10 consultas de los reportes
app/                  aplicación Spring Boot
```

## 1. Requisitos (macOS con Apple Silicon)

```
brew install mysql@8.4 openjdk@21 maven
echo 'export PATH="/opt/homebrew/opt/mysql@8.4/bin:$PATH"' >> ~/.zshrc
source ~/.zshrc
sudo ln -sfn /opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk /Library/Java/JavaVirtualMachines/openjdk-21.jdk
```

## 2. Base de datos

```
brew services run mysql@8.4
cd sql
mysql -u root -p --default-character-set=utf8mb4 < 01_ddl.sql
mysql -u root -p --default-character-set=utf8mb4 quindiostay < 02_triggers.sql
mysql -u root -p --default-character-set=utf8mb4 quindiostay < 03_datos.sql
```

Usuario de la aplicación (dentro de `mysql -u root -p`):

```
CREATE USER IF NOT EXISTS 'quindio'@'localhost' IDENTIFIED BY 'Quindio_App26!';
GRANT ALL PRIVILEGES ON quindiostay.* TO 'quindio'@'localhost';
FLUSH PRIVILEGES;
```

## 3. Ejecutar la aplicación

```
cd app
mvn spring-boot:run
```

La primera vez Maven descarga las librerías (necesita internet). Cuando aparezca `Started QuindioStayApplication`, abrir http://localhost:8080

Para detenerla: `Ctrl + C` en la Terminal.

## 4. Cuentas de prueba

| Rol | Usuario | Contraseña |
|---|---|---|
| Administrador | admin | Quindio2026! |
| Encargado de prestador | enc.hotelcocora | Quindio2026! |
| Operador | operador.armenia | Quindio2026! |
| Bloqueado (debe ser rechazado) | operador.salento | Quindio2026! |
| Cliente nivel oro | michael.johnson87@yahoo.com | Cliente2026! |
| Cliente nivel bronce | marta.gimenez@outlook.com | Cliente2026! |
| Cliente sin membresía | emily.carter@hotmail.com | Cliente2026! |

## 5. Qué hace la aplicación

- Login por rol con BCrypt: clientes por correo, usuarios internos por nombre de usuario; rechaza bloqueados y clientes sin cuenta.
- CRUD de las 17 tablas (formulario y tabla en la misma pantalla), con búsqueda y paginación. La estructura se lee de `information_schema`; los menús usan los valores de los CHECK; los errores de la base (triggers, FK, CHECK) se muestran en español.
- Redenciones con `CALL sp_redimir(...)`.
- 10 reportes en pantalla (gráficos en R6 barras, R7 torta, R8 líneas) y exportación a PDF.
- 5 plantillas en PDF: comprobante de reserva, comprobante de pago, factura, autorización de tratamiento de datos (versión de menor de edad automática) y certificado de puntos.

## 6. Foto del login (opcional)

Si se copia una foto como `app/src/main/resources/static/img/cocora.jpg`, se usa en el login y en la franja lateral. Si no existe, se muestra un dibujo de palmas incluido en el proyecto.

## 7. Problemas comunes

| Mensaje | Solución |
|---|---|
| `Communications link failure` al arrancar | MySQL está apagado: `brew services run mysql@8.4` |
| `Access denied for user 'quindio'` | Falta el paso del usuario de la aplicación (sección 2) |
| `Unknown database 'quindiostay'` | Correr los scripts 01, 02 y 03 |
| `Port 8080 was already in use` | Ya hay otra copia corriendo: cerrarla con `Ctrl + C` o `lsof -ti:8080 \| xargs kill` |
| `mvn: command not found` | `brew install maven` y abrir una Terminal nueva |

package co.edu.uniquindio.quindiostay.crud;

import java.util.List;

/** Descripción de una tabla para el CRUD genérico. */
public class Tabla {

    private final String nombre;
    private final String titulo;
    private final String descripcion;   // comentario de la tabla en 01_ddl.sql
    private final List<Columna> columnas;
    private final Columna pk;

    public Tabla(String nombre, String titulo, String descripcion, List<Columna> columnas) {
        this.nombre = nombre;
        this.titulo = titulo;
        this.descripcion = descripcion;
        this.columnas = columnas;
        this.pk = columnas.stream().filter(Columna::isPk).findFirst().orElse(columnas.get(0));
    }

    public String getNombre() { return nombre; }
    public String getTitulo() { return titulo; }
    public String getDescripcion() { return descripcion; }
    public List<Columna> getColumnas() { return columnas; }
    public Columna getPk() { return pk; }

    /** redencion: solo se crea con CALL sp_redimir (no se inserta a mano). */
    public boolean isSoloLectura() { return CatalogoTablas.SOLO_LECTURA.contains(nombre); }

    /** movimiento_puntos: es historial, no se modifica (lo impide un trigger). */
    public boolean isSinEditar() { return CatalogoTablas.SIN_EDITAR.contains(nombre); }
}

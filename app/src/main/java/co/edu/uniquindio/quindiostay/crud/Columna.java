package co.edu.uniquindio.quindiostay.crud;

import java.util.List;

/** Descripción de una columna, leída de information_schema.columns. */
public class Columna {

    private final String nombre;
    private final String tipoDato;        // int, varchar, date, datetime, decimal, text...
    private final boolean nula;           // admite NULL
    private final boolean autoIncremental;
    private final boolean pk;
    private final String porDefecto;      // valor DEFAULT (null si no tiene)
    private final boolean defectoExpresion; // DEFAULT CURRENT_TIMESTAMP / CURRENT_DATE
    private final String comentario;
    private final String fkTabla;         // tabla a la que apunta (null si no es FK)
    private final List<String> opciones;  // valores permitidos por el CHECK (null si es libre)

    public Columna(String nombre, String tipoDato, boolean nula, boolean autoIncremental, boolean pk,
                   String porDefecto, boolean defectoExpresion, String comentario,
                   String fkTabla, List<String> opciones) {
        this.nombre = nombre;
        this.tipoDato = tipoDato;
        this.nula = nula;
        this.autoIncremental = autoIncremental;
        this.pk = pk;
        this.porDefecto = porDefecto;
        this.defectoExpresion = defectoExpresion;
        this.comentario = comentario;
        this.fkTabla = fkTabla;
        this.opciones = opciones;
    }

    public String getNombre() { return nombre; }
    public String getTipoDato() { return tipoDato; }
    public boolean isNula() { return nula; }
    public boolean isAutoIncremental() { return autoIncremental; }
    public boolean isPk() { return pk; }
    public String getPorDefecto() { return porDefecto; }
    public boolean isDefectoExpresion() { return defectoExpresion; }
    public String getComentario() { return comentario; }
    public String getFkTabla() { return fkTabla; }
    public List<String> getOpciones() { return opciones; }

    public boolean isFk() { return fkTabla != null; }

    /** Columnas de contraseña: se cifran con BCrypt y nunca se muestran. */
    public boolean isClave() { return nombre.startsWith("contrasena"); }

    /** Nombre bonito para la etiqueta: fecha_servicio -> Fecha servicio */
    public String getEtiqueta() {
        String t = nombre.replace('_', ' ');
        return Character.toUpperCase(t.charAt(0)) + t.substring(1);
    }

    /** Obligatorio en el formulario: NOT NULL, sin valor por defecto y no automático. */
    public boolean isObligatorio() {
        return !nula && porDefecto == null && !autoIncremental;
    }

    /** Tipo de campo HTML según el tipo de dato de MySQL. */
    public String getTipoInput() {
        return switch (tipoDato) {
            case "date" -> "date";
            case "datetime", "timestamp" -> "datetime-local";
            case "int", "bigint", "smallint", "tinyint", "decimal" -> "number";
            case "text", "mediumtext", "longtext" -> "textarea";
            default -> "text";
        };
    }

    public String getPaso() {
        return "decimal".equals(tipoDato) ? "0.01" : "1";
    }
}

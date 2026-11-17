package co.edu.uniquindio.quindiostay.reportes;

import java.util.List;

/** Un reporte: su consulta (copiada de 04_reportes.sql) y cómo graficarlo. */
public class Reporte {

    private final String codigo;
    private final String titulo;
    private final String nivel;            // Simple, Intermedio, Avanzado
    private final String pregunta;
    private final String sql;
    private final String grafico;          // bar, pie, line o null
    private final String columnaEtiqueta;  // eje X / porciones
    private final List<String> columnasValor;

    public Reporte(String codigo, String titulo, String nivel, String pregunta, String sql,
                   String grafico, String columnaEtiqueta, List<String> columnasValor) {
        this.codigo = codigo;
        this.titulo = titulo;
        this.nivel = nivel;
        this.pregunta = pregunta;
        this.sql = sql;
        this.grafico = grafico;
        this.columnaEtiqueta = columnaEtiqueta;
        this.columnasValor = columnasValor;
    }

    public String getCodigo() { return codigo; }
    public String getTitulo() { return titulo; }
    public String getNivel() { return nivel; }
    public String getPregunta() { return pregunta; }
    public String getSql() { return sql; }
    public String getGrafico() { return grafico; }
    public String getColumnaEtiqueta() { return columnaEtiqueta; }
    public List<String> getColumnasValor() { return columnasValor; }
}

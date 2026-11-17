package co.edu.uniquindio.quindiostay.web;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

import org.springframework.stereotype.Component;

/**
 * Convierte los valores que llegan de MySQL a texto bonito para las páginas y los PDF.
 * En las plantillas se usa así: ${@fmt.v(valor)}
 */
@Component("fmt")
public class Formato {

    private static final DateTimeFormatter FECHA_HORA = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
    private static final DateTimeFormatter FECHA_HORA_INPUT = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss");

    /** Valor para mostrar en una tabla. */
    public String v(Object o) {
        if (o == null) return "";
        if (o instanceof BigDecimal || o instanceof Double || o instanceof Float) return numero(o);
        if (o instanceof LocalDateTime t) return t.format(FECHA_HORA);
        if (o instanceof Timestamp t) return t.toLocalDateTime().format(FECHA_HORA);
        if (o instanceof Boolean b) return b ? "sí" : "no";
        return o.toString();
    }

    /** Número con puntos de miles: 1250000 -> 1.250.000 */
    public String numero(Object o) {
        if (o == null) return "";
        DecimalFormatSymbols s = new DecimalFormatSymbols(Locale.ROOT);
        s.setGroupingSeparator('.');
        s.setDecimalSeparator(',');
        return new DecimalFormat("#,##0.##", s).format(o);
    }

    public String dinero(Object o) {
        return o == null ? "" : "$ " + numero(o);
    }

    /** Valor para poner dentro de un campo de formulario (input). */
    public String entrada(Object o) {
        if (o == null) return "";
        if (o instanceof LocalDateTime t) return t.format(FECHA_HORA_INPUT);
        if (o instanceof Timestamp t) return t.toLocalDateTime().format(FECHA_HORA_INPUT);
        if (o instanceof BigDecimal b) return b.toPlainString();
        if (o instanceof Boolean b) return b ? "1" : "0";   // TINYINT(1) llega como Boolean
        return o.toString();
    }
}

package co.edu.uniquindio.quindiostay.web;

import java.sql.SQLException;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Convierte los errores de MySQL en mensajes en español para el usuario.
 * La base hace cumplir las reglas (CHECK, FK, triggers); la aplicación solo muestra el mensaje.
 */
public final class ErroresBD {

    /** Mensajes amables para los CHECK de 01_ddl.sql */
    private static final Map<String, String> CHECKS = Map.ofEntries(
            Map.entry("ck_municipio_dane", "El código DANE debe tener exactamente 5 números."),
            Map.entry("ck_prestador_correo", "El correo del prestador no es válido."),
            Map.entry("ck_cliente_correo", "El correo del cliente no es válido."),
            Map.entry("ck_plan_precio", "El precio del plan debe ser mayor que cero."),
            Map.entry("ck_plan_fechas", "La fecha fin del plan debe ser posterior a la fecha de inicio."),
            Map.entry("ck_reserva_valor", "El valor total de la reserva debe ser mayor que cero."),
            Map.entry("ck_reserva_fechas", "La fecha del servicio no puede ser anterior a la fecha de creación."),
            Map.entry("ck_pago_monto", "El monto del pago debe ser mayor que cero."),
            Map.entry("ck_pago_referencia", "Los pagos con tarjeta o PSE necesitan la referencia de la transacción."),
            Map.entry("ck_notificacion_envio", "La fecha de envío no puede ser anterior a la fecha programada."),
            Map.entry("ck_nivel_umbral", "El umbral del nivel no puede ser negativo."),
            Map.entry("ck_aliado_puntos", "Los puntos por cada $1.000 deben ser mayores que cero."),
            Map.entry("ck_beneficio_costo", "El costo en puntos debe ser mayor que cero."),
            Map.entry("ck_movimiento_signo", "La cantidad no corresponde al tipo: acumulación positiva, redención y vencimiento negativos, ajuste distinto de cero."),
            Map.entry("ck_movimiento_reserva", "Solo las acumulaciones llevan reserva (y la deben llevar)."));

    private static final Pattern NOMBRE_CHECK = Pattern.compile("'([a-z_]+)'");

    private ErroresBD() { }

    public static String traducir(Throwable e) {
        SQLException sql = null;
        for (Throwable t = e; t != null; t = t.getCause()) {
            if (t instanceof SQLException s) { sql = s; break; }
        }
        if (sql == null) {
            return "No se pudo completar la operación: " + e.getMessage();
        }
        String msg = sql.getMessage();
        if ("45000".equals(sql.getSQLState())) {
            return msg;   // mensaje en español escrito en los triggers (02_triggers.sql)
        }
        return switch (sql.getErrorCode()) {
            case 1451 -> "No se puede eliminar: este registro tiene otros datos asociados (por ejemplo reservas, pagos o movimientos).";
            case 1452 -> "El valor escogido en una lista relacionada no existe.";
            case 1062 -> "Ya existe un registro con ese mismo valor único. Detalle: " + msg;
            case 1048, 1364 -> "Falta llenar un campo obligatorio. Detalle: " + msg;
            case 1406 -> "Un texto es demasiado largo para el campo. Detalle: " + msg;
            case 1292, 1366 -> "Hay un valor con formato incorrecto (fecha o número). Detalle: " + msg;
            case 3819 -> {
                Matcher m = NOMBRE_CHECK.matcher(msg);
                String nombre = m.find() ? m.group(1) : "";
                yield CHECKS.getOrDefault(nombre, "El dato no cumple una regla de la base (" + nombre + "). Revise los valores permitidos.");
            }
            default -> "Error de la base de datos: " + msg;
        };
    }
}

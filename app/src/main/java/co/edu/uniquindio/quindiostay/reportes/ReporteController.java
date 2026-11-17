package co.edu.uniquindio.quindiostay.reportes;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import co.edu.uniquindio.quindiostay.documentos.DocumentoService;
import co.edu.uniquindio.quindiostay.pdf.PdfService;

/** Reportes en pantalla (con gráfico en R6, R7 y R8) y exportados a PDF. */
@Controller
@RequestMapping("/reportes")
public class ReporteController {

    private static final List<String> ORDEN_NIVELES = List.of("platino", "oro", "plata", "bronce");

    private final ReporteService reportes;
    private final PdfService pdf;

    public ReporteController(ReporteService reportes, PdfService pdf) {
        this.reportes = reportes;
        this.pdf = pdf;
    }

    @GetMapping
    public String indice() {
        return "redirect:/reportes/R1";
    }

    @GetMapping("/{codigo}")
    public String ver(@PathVariable String codigo,
                      @RequestParam(required = false) String nivel,
                      @RequestParam(required = false) Integer min,
                      @RequestParam(required = false) Integer max,
                      Model model) {
        Reporte r = reportes.buscar(codigo);
        List<Map<String, Object>> filas = reportes.ejecutar(r);

        model.addAttribute("reporte", r);
        model.addAttribute("reportes", reportes.todos());
        model.addAttribute("filas", filas);

        if (r.getGrafico() != null) {
            List<String> etiquetas = new ArrayList<>();
            Map<String, List<Number>> series = new LinkedHashMap<>();
            r.getColumnasValor().forEach(c -> series.put(c, new ArrayList<>()));
            for (Map<String, Object> f : filas) {
                etiquetas.add(String.valueOf(f.get(r.getColumnaEtiqueta())));
                for (String c : r.getColumnasValor()) {
                    Object v = f.get(c);
                    series.get(c).add(v instanceof Number n ? n : 0);
                }
            }
            model.addAttribute("etiquetas", etiquetas);
            model.addAttribute("series", series);
        }

        if ("R7".equals(r.getCodigo())) {
            model.addAttribute("grupos", agrupar(reportes.detalleR7(nivel, min, max)));
            model.addAttribute("fNivel", nivel == null ? "" : nivel);
            model.addAttribute("fMin", min);
            model.addAttribute("fMax", max);
            model.addAttribute("niveles", ORDEN_NIVELES);
        }
        return "reportes/ver";
    }

    @GetMapping("/{codigo}/pdf")
    public ResponseEntity<byte[]> exportar(@PathVariable String codigo,
                                           @RequestParam(required = false) String nivel,
                                           @RequestParam(required = false) Integer min,
                                           @RequestParam(required = false) Integer max) {
        Reporte r = reportes.buscar(codigo);
        String titulo = r.getCodigo() + ": " + r.getTitulo();
        byte[] contenido;
        if ("R7".equals(r.getCodigo())) {
            contenido = pdf.reporte(titulo, r.getPregunta(), r.getNivel(), reportes.ejecutar(r),
                    "Detalle de clientes por nivel", reportes.detalleR7(nivel, min, max));
        } else {
            contenido = pdf.reporte(titulo, r.getPregunta(), r.getNivel(), reportes.ejecutar(r), null, null);
        }
        return DocumentoService.respuesta(contenido, "reporte-" + r.getCodigo() + ".pdf");
    }

    /** Agrupa el detalle del R7 por nivel (platino, oro, plata, bronce), como en el prototipo. */
    private Map<String, List<Map<String, Object>>> agrupar(List<Map<String, Object>> filas) {
        Map<String, List<Map<String, Object>>> grupos = new LinkedHashMap<>();
        for (String n : ORDEN_NIVELES) grupos.put(n, new ArrayList<>());
        for (Map<String, Object> f : filas) {
            grupos.computeIfAbsent(String.valueOf(f.get("nivel")), k -> new ArrayList<>()).add(f);
        }
        grupos.values().removeIf(List::isEmpty);
        return grupos;
    }
}

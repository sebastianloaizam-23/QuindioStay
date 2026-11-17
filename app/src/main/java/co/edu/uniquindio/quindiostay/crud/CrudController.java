package co.edu.uniquindio.quindiostay.crud;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import co.edu.uniquindio.quindiostay.seguridad.UsuarioActual;
import co.edu.uniquindio.quindiostay.web.ErroresBD;
import co.edu.uniquindio.quindiostay.web.Formato;

/**
 * Pantalla de CRUD como en el prototipo "Registro de clientes": formulario a la izquierda
 * y tabla con botones de editar (amarillo) y borrar (rojo) a la derecha.
 * Sirve para las 17 tablas: /crud/cliente, /crud/reserva, /crud/pago, ...
 */
@Controller
@RequestMapping("/crud")
public class CrudController {

    private final CatalogoTablas catalogo;
    private final CrudService crud;
    private final Formato fmt;

    public CrudController(CatalogoTablas catalogo, CrudService crud, Formato fmt) {
        this.catalogo = catalogo;
        this.crud = crud;
        this.fmt = fmt;
    }

    @GetMapping
    public String indice() {
        return "redirect:/";
    }

    /** Lista + formulario. Con ?editar=ID el formulario se llena con ese registro. */
    @GetMapping("/{tabla}")
    public String pagina(@PathVariable String tabla,
                         @RequestParam(defaultValue = "") String q,
                         @RequestParam(defaultValue = "1") int p,
                         @RequestParam(required = false) String editar,
                         @RequestParam Map<String, String> params,
                         @AuthenticationPrincipal UsuarioActual u, Model model) {
        Tabla t = catalogo.info(tabla);
        boolean nuevo = editar == null || editar.isBlank();
        Map<String, String> valores;
        if (nuevo) {
            valores = valoresIniciales(t, params);
        } else {
            try {
                valores = aTexto(t, crud.buscar(t, editar));
            } catch (EmptyResultDataAccessException e) {
                throw new ResponseStatusException(HttpStatus.NOT_FOUND, "No existe el registro " + editar);
            }
        }
        cargar(model, u, t, q, p, valores, nuevo, editar, null);
        return "crud/tabla";
    }

    @PostMapping("/{tabla}/guardar")
    public String guardar(@PathVariable String tabla, @RequestParam Map<String, String> datos,
                          @AuthenticationPrincipal UsuarioActual u,
                          RedirectAttributes ra, Model model) {
        Tabla t = catalogo.info(tabla);
        if (t.isSoloLectura()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Las redenciones se hacen desde Redimir (sp_redimir)");
        }
        String id = datos.get("_id");
        boolean nuevo = id == null || id.isBlank();
        try {
            crud.guardar(t, datos, nuevo, id);
            ra.addFlashAttribute("ok", nuevo ? "Registro creado correctamente." : "Cambios guardados correctamente.");
            return "redirect:/crud/" + tabla;
        } catch (RuntimeException e) {
            // Volvemos a mostrar el formulario con lo que escribió y el mensaje de la base
            Map<String, String> valores = new HashMap<>(datos);
            t.getColumnas().stream().filter(Columna::isClave).forEach(c -> valores.put(c.getNombre(), ""));
            cargar(model, u, t, "", 1, valores, nuevo, id, ErroresBD.traducir(e));
            return "crud/tabla";
        }
    }

    @PostMapping("/{tabla}/{id}/eliminar")
    public String eliminar(@PathVariable String tabla, @PathVariable String id, RedirectAttributes ra) {
        Tabla t = catalogo.info(tabla);
        try {
            crud.eliminar(t, id);
            ra.addFlashAttribute("ok", "Registro " + id + " eliminado.");
        } catch (RuntimeException e) {
            ra.addFlashAttribute("error", ErroresBD.traducir(e));
        }
        return "redirect:/crud/" + tabla;
    }

    // ------------------------------------------------------------------

    private void cargar(Model model, UsuarioActual u, Tabla t, String q, int p, Map<String, String> valores,
                        boolean nuevo, String id, String error) {
        long total = crud.contar(t, q);
        int totalPaginas = (int) Math.max(1, Math.ceil(total / (double) CrudService.TAMANO_PAGINA));
        int pagina = Math.min(Math.max(1, p), totalPaginas);

        // Etiquetas de las llaves foráneas (para la tabla y para los menús del formulario)
        Map<String, Map<String, String>> etiquetas = new HashMap<>();
        for (Columna c : t.getColumnas()) {
            if (c.isFk()) etiquetas.put(c.getNombre(), catalogo.etiquetas(c.getFkTabla()));
        }

        List<FilaVista> filas = new ArrayList<>();
        for (Map<String, Object> fila : crud.listar(t, q, pagina)) {
            List<String> celdas = new ArrayList<>();
            for (Columna c : t.getColumnas()) {
                Object v = fila.get(c.getNombre());
                String texto;
                if (c.isClave()) {
                    texto = v == null ? "sin cuenta" : "•••• (BCrypt)";
                } else if (c.isFk() && v != null) {
                    String e = etiquetas.get(c.getNombre()).get(String.valueOf(v));
                    texto = e == null ? fmt.v(v) : v + " · " + e;
                } else {
                    texto = fmt.v(v);
                }
                if (texto.length() > 60) texto = texto.substring(0, 57) + "...";
                celdas.add(texto);
            }
            filas.add(new FilaVista(String.valueOf(fila.get(t.getPk().getNombre())), celdas));
        }

        Map<String, String> menu = new LinkedHashMap<>();
        CatalogoTablas.TITULOS.forEach((k, v) -> {
            if (u.tieneRol("ADMIN") || !CatalogoTablas.SOLO_ADMIN.contains(k)) menu.put(k, v);
        });

        model.addAttribute("tabla", t);
        model.addAttribute("filas", filas);
        model.addAttribute("etiquetas", etiquetas);
        model.addAttribute("valores", valores);
        model.addAttribute("nuevo", nuevo);
        model.addAttribute("idEditar", id);
        model.addAttribute("error", error);
        model.addAttribute("q", q);
        model.addAttribute("pagina", pagina);
        model.addAttribute("totalPaginas", totalPaginas);
        model.addAttribute("total", total);
        model.addAttribute("desde", Math.max(1, pagina - 3));
        model.addAttribute("hasta", Math.min(totalPaginas, pagina + 3));
        model.addAttribute("menu", menu);
    }

    /** Valores del formulario vacío: los DEFAULT de la tabla o lo que venga en la URL (?id_reserva=5). */
    private Map<String, String> valoresIniciales(Tabla t, Map<String, String> params) {
        Map<String, String> v = new HashMap<>();
        for (Columna c : t.getColumnas()) {
            if (params.containsKey(c.getNombre())) v.put(c.getNombre(), params.get(c.getNombre()));
            else if (c.getPorDefecto() != null && !c.isDefectoExpresion()) v.put(c.getNombre(), c.getPorDefecto());
            else v.put(c.getNombre(), "");
        }
        return v;
    }

    private Map<String, String> aTexto(Tabla t, Map<String, Object> fila) {
        Map<String, String> v = new HashMap<>();
        for (Columna c : t.getColumnas()) {
            v.put(c.getNombre(), c.isClave() ? "" : fmt.entrada(fila.get(c.getNombre())));
        }
        return v;
    }

    /** Una fila ya lista para mostrar. */
    public static class FilaVista {
        private final String id;
        private final List<String> celdas;

        public FilaVista(String id, List<String> celdas) {
            this.id = id;
            this.celdas = celdas;
        }

        public String getId() { return id; }
        public List<String> getCeldas() { return celdas; }
    }
}

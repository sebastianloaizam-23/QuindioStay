package co.edu.uniquindio.quindiostay.documentos;

import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

/** Pantalla para generar las 5 plantillas de documentos en PDF. */
@Controller
@RequestMapping("/documentos")
public class DocumentoController {

    private final DocumentoService documentos;

    public DocumentoController(DocumentoService documentos) {
        this.documentos = documentos;
    }

    @GetMapping
    public String indice() {
        return "documentos/indice";
    }

    @GetMapping("/reserva")
    public ResponseEntity<byte[]> reserva(@RequestParam int id) {
        return DocumentoService.respuesta(documentos.comprobanteReserva(id), "comprobante-reserva-" + id + ".pdf");
    }

    @GetMapping("/pago")
    public ResponseEntity<byte[]> pago(@RequestParam int id) {
        return DocumentoService.respuesta(documentos.comprobantePago(id), "comprobante-pago-" + id + ".pdf");
    }

    @GetMapping("/factura")
    public ResponseEntity<byte[]> factura(@RequestParam int id) {
        return DocumentoService.respuesta(documentos.factura(id), "factura-FV-" + id + ".pdf");
    }

    @GetMapping("/autorizacion")
    public ResponseEntity<byte[]> autorizacion(@RequestParam int id) {
        return DocumentoService.respuesta(documentos.autorizacion(id), "autorizacion-datos-" + id + ".pdf");
    }

    @GetMapping("/certificado")
    public ResponseEntity<byte[]> certificado(@RequestParam int id) {
        return DocumentoService.respuesta(documentos.certificadoPuntos(id), "certificado-puntos-" + id + ".pdf");
    }
}

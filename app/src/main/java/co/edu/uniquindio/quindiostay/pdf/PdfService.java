package co.edu.uniquindio.quindiostay.pdf;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;

import com.lowagie.text.Chunk;
import com.lowagie.text.Document;
import com.lowagie.text.DocumentException;
import com.lowagie.text.Element;
import com.lowagie.text.Font;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Phrase;
import com.lowagie.text.Rectangle;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;

import co.edu.uniquindio.quindiostay.web.Formato;

/**
 * Piezas para armar PDF con OpenPDF, con los mismos colores de la aplicación
 * (verde del prototipo). Lo usan los reportes y las plantillas de documentos.
 */
@Service
public class PdfService {

    public static final Color VERDE = new Color(0x1d, 0x83, 0x42);
    public static final Color VERDE_OSCURO = new Color(0x12, 0x54, 0x2a);
    public static final Color VERDE_TABLA = new Color(0x13, 0x6b, 0x4a);
    public static final Color VERDE_CLARO = new Color(0xe9, 0xf5, 0xee);
    public static final Color GRIS = new Color(0x55, 0x5f, 0x59);

    public static final Font F_TITULO = new Font(Font.HELVETICA, 17, Font.BOLD, VERDE_OSCURO);
    public static final Font F_SUBTITULO = new Font(Font.HELVETICA, 10, Font.NORMAL, GRIS);
    public static final Font F_SECCION = new Font(Font.HELVETICA, 11, Font.BOLD, VERDE);
    public static final Font F_TEXTO = new Font(Font.HELVETICA, 9.5f, Font.NORMAL, Color.BLACK);
    public static final Font F_NEGRITA = new Font(Font.HELVETICA, 9.5f, Font.BOLD, Color.BLACK);
    public static final Font F_CABECERA = new Font(Font.HELVETICA, 8.5f, Font.BOLD, Color.WHITE);
    public static final Font F_CELDA = new Font(Font.HELVETICA, 8.5f, Font.NORMAL, Color.BLACK);
    public static final Font F_BANDA = new Font(Font.HELVETICA, 13, Font.BOLD, Color.WHITE);
    public static final Font F_BANDA_PEQ = new Font(Font.HELVETICA, 8.5f, Font.NORMAL, Color.WHITE);
    public static final Font F_PIE = new Font(Font.HELVETICA, 7.5f, Font.ITALIC, GRIS);

    private final Formato fmt;

    public PdfService(Formato fmt) {
        this.fmt = fmt;
    }

    /** Abre un documento nuevo (vertical u horizontal). */
    public Document abrir(ByteArrayOutputStream salida, boolean horizontal) {
        Document doc = new Document(horizontal ? PageSize.A4.rotate() : PageSize.A4, 40, 40, 36, 36);
        try {
            PdfWriter.getInstance(doc, salida);
        } catch (DocumentException e) {
            throw new IllegalStateException("No se pudo crear el PDF", e);
        }
        doc.open();
        return doc;
    }

    /** Banda verde con el nombre del sistema + título + subtítulo. */
    public void encabezado(Document doc, String titulo, String subtitulo) throws DocumentException {
        PdfPTable banda = new PdfPTable(new float[]{3, 2});
        banda.setWidthPercentage(100);
        PdfPCell izq = new PdfPCell();
        izq.addElement(new Paragraph("QuindioStay", F_BANDA));
        izq.addElement(new Paragraph("Fidelización de Clientes · Turismo en el Quindío", F_BANDA_PEQ));
        PdfPCell der = new PdfPCell();
        Paragraph p = new Paragraph("Universidad del Quindío\nBases de Datos I", F_BANDA_PEQ);
        p.setAlignment(Element.ALIGN_RIGHT);
        der.addElement(p);
        for (PdfPCell c : new PdfPCell[]{izq, der}) {
            c.setBackgroundColor(VERDE);
            c.setBorder(Rectangle.NO_BORDER);
            c.setPadding(10);
            banda.addCell(c);
        }
        doc.add(banda);

        Paragraph t = new Paragraph(titulo, F_TITULO);
        t.setSpacingBefore(14);
        doc.add(t);
        if (subtitulo != null) {
            Paragraph s = new Paragraph(subtitulo, F_SUBTITULO);
            s.setSpacingAfter(10);
            doc.add(s);
        }
    }

    public Paragraph seccion(String texto) {
        Paragraph p = new Paragraph(texto, F_SECCION);
        p.setSpacingBefore(12);
        p.setSpacingAfter(5);
        return p;
    }

    public Paragraph parrafo(String texto) {
        Paragraph p = new Paragraph(texto, F_TEXTO);
        p.setAlignment(Element.ALIGN_JUSTIFIED);
        p.setSpacingAfter(6);
        p.setLeading(13);
        return p;
    }

    /** Tabla de resultados con encabezado verde (igual a la tabla de la aplicación). */
    public PdfPTable tabla(List<Map<String, Object>> filas) {
        if (filas == null || filas.isEmpty()) {
            PdfPTable vacia = new PdfPTable(1);
            vacia.setWidthPercentage(100);
            PdfPCell c = new PdfPCell(new Phrase("Sin datos.", F_CELDA));
            c.setPadding(6);
            vacia.addCell(c);
            return vacia;
        }
        List<String> columnas = List.copyOf(filas.get(0).keySet());
        PdfPTable t = new PdfPTable(columnas.size());
        t.setWidthPercentage(100);
        t.setHeaderRows(1);
        for (String col : columnas) {
            PdfPCell c = new PdfPCell(new Phrase(col.replace('_', ' '), F_CABECERA));
            c.setBackgroundColor(VERDE_TABLA);
            c.setPadding(5);
            c.setBorderColor(Color.WHITE);
            t.addCell(c);
        }
        int i = 0;
        for (Map<String, Object> fila : filas) {
            for (String col : columnas) {
                PdfPCell c = new PdfPCell(new Phrase(fmt.v(fila.get(col)), F_CELDA));
                c.setPadding(4);
                c.setBorderColor(new Color(0xdd, 0xe5, 0xe0));
                if (i % 2 == 1) c.setBackgroundColor(new Color(0xf6, 0xf9, 0xf7));
                t.addCell(c);
            }
            i++;
        }
        return t;
    }

    /** Tabla de dos columnas "dato: valor" para los documentos. */
    public PdfPTable datos(String[][] pares) {
        PdfPTable t = new PdfPTable(new float[]{1.3f, 3});
        t.setWidthPercentage(100);
        for (String[] par : pares) {
            PdfPCell k = new PdfPCell(new Phrase(par[0], F_NEGRITA));
            k.setBackgroundColor(VERDE_CLARO);
            PdfPCell v = new PdfPCell(new Phrase(par[1] == null ? "" : par[1], F_TEXTO));
            for (PdfPCell c : new PdfPCell[]{k, v}) {
                c.setPadding(5);
                c.setBorderColor(new Color(0xd5, 0xe8, 0xdc));
                t.addCell(c);
            }
        }
        return t;
    }

    /** Líneas para firmar. */
    public PdfPTable firmas(String... quienes) {
        PdfPTable t = new PdfPTable(quienes.length);
        t.setWidthPercentage(100);
        t.setSpacingBefore(45);
        for (String q : quienes) {
            PdfPCell c = new PdfPCell();
            c.setBorder(Rectangle.NO_BORDER);
            Paragraph linea = new Paragraph("______________________________", F_TEXTO);
            linea.setAlignment(Element.ALIGN_CENTER);
            Paragraph nombre = new Paragraph(q, F_SUBTITULO);
            nombre.setAlignment(Element.ALIGN_CENTER);
            c.addElement(linea);
            c.addElement(nombre);
            t.addCell(c);
        }
        return t;
    }

    public void pie(Document doc) throws DocumentException {
        Paragraph p = new Paragraph("Documento generado por el sistema QuindioStay el "
                + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"))
                + " · Proyecto académico, Bases de Datos I, Universidad del Quindío.", F_PIE);
        p.setSpacingBefore(18);
        doc.add(new Chunk(" "));
        doc.add(p);
    }

    /** PDF completo de un reporte (y, si se da, una segunda tabla de detalle). */
    public byte[] reporte(String titulo, String pregunta, String nivel, List<Map<String, Object>> filas,
                          String tituloDetalle, List<Map<String, Object>> detalle) {
        ByteArrayOutputStream salida = new ByteArrayOutputStream();
        Document doc = abrir(salida, true);
        try {
            encabezado(doc, titulo, pregunta + "   ·   Nivel de la consulta: " + nivel);
            doc.add(tabla(filas));
            if (detalle != null) {
                doc.add(seccion(tituloDetalle));
                doc.add(tabla(detalle));
            }
            pie(doc);
        } catch (DocumentException e) {
            throw new IllegalStateException("No se pudo generar el PDF", e);
        } finally {
            doc.close();
        }
        return salida.toByteArray();
    }
}

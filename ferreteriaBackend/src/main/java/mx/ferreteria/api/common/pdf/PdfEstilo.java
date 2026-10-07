package mx.ferreteria.api.common.pdf;

import java.math.BigDecimal;
import java.text.NumberFormat;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

import java.awt.Color;
import com.lowagie.text.Chunk;
import com.lowagie.text.Document;
import com.lowagie.text.DocumentException;
import com.lowagie.text.Element;
import com.lowagie.text.Font;
import com.lowagie.text.FontFactory;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Phrase;
import com.lowagie.text.Rectangle;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfPageEventHelper;
import com.lowagie.text.pdf.PdfWriter;

/**
 * Estilo único de los PDF que viajan por correo (ticket, informe, nómina):
 * paleta de marca, tablas legibles, moneda {@code es-MX} y pie con paginación.
 * Clase estática sin estado: los servicios de PDF la usan, no la extienden.
 */
public final class PdfEstilo {

    public static final String MARCA = "El Tornillo Feliz";
    public static final java.awt.Color COLOR_MARCA = new java.awt.Color(0xC2, 0x41, 0x0C);
    public static final java.awt.Color COLOR_MARCA_OSCURO = new java.awt.Color(0x7C, 0x2D, 0x12);
    public static final java.awt.Color GRIS_TEXTO = new java.awt.Color(0x29, 0x25, 0x24);
    public static final java.awt.Color GRIS_SUAVE = new java.awt.Color(0x78, 0x71, 0x6C);
    public static final java.awt.Color FONDO_CLARO = new java.awt.Color(0xFF, 0xF7, 0xED);
    public static final java.awt.Color BORDE_CLARO = new java.awt.Color(0xFE, 0xD7, 0xAA);
    public static final java.awt.Color FONDO_ALERTA = new java.awt.Color(0xFE, 0xF2, 0xF2);
    public static final java.awt.Color BORDE_ALERTA = new java.awt.Color(0xFE, 0xCA, 0xCA);
    public static final java.awt.Color VERDE_OK = new java.awt.Color(0x16, 0x65, 0x2D);
    public static final java.awt.Color ROJO_ALERTA = new java.awt.Color(0xB9, 0x1C, 0x1C);
    public static final java.awt.Color FONDO_ENCABEZADO = new java.awt.Color(0x29, 0x25, 0x24);
    public static final java.awt.Color FONDO_FILA_ALT = new java.awt.Color(0xFA, 0xF7, 0xF1);

    private static final Locale LOCALE_MX = new Locale("es", "MX");
    private static final DateTimeFormatter FECHA_CORTE = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private PdfEstilo() {
    }

    // ── fuentes ──────────────────────────────────────────────────────

    public static Font fuenteMarca() {
        return FontFactory.getFont(FontFactory.HELVETICA_BOLD, 15, COLOR_MARCA);
    }

    public static Font fuenteTitulo() {
        return FontFactory.getFont(FontFactory.HELVETICA_BOLD, 13, GRIS_TEXTO);
    }

    public static Font fuenteSeccion() {
        return FontFactory.getFont(FontFactory.HELVETICA_BOLD, 11, COLOR_MARCA_OSCURO);
    }

    public static Font fuenteNormal() {
        return FontFactory.getFont(FontFactory.HELVETICA, 10, GRIS_TEXTO);
    }

    public static Font fuentePequena() {
        return FontFactory.getFont(FontFactory.HELVETICA, 8, GRIS_SUAVE);
    }

    public static Font fuentePequenaOscura() {
        return FontFactory.getFont(FontFactory.HELVETICA, 8, GRIS_TEXTO);
    }

    public static Font fuenteEncabezadoTabla() {
        return FontFactory.getFont(FontFactory.HELVETICA_BOLD, 9, java.awt.Color.WHITE);
    }

    public static Font fuenteTotal() {
        return FontFactory.getFont(FontFactory.HELVETICA_BOLD, 12, GRIS_TEXTO);
    }

    // ── formato ──────────────────────────────────────────────────────

    /** Moneda {@code es-MX} ($1,234.56). Nunca devuelve null ni "null". */
    public static String moneda(BigDecimal n) {
        if (n == null) {
            return NumberFormat.getCurrencyInstance(LOCALE_MX).format(BigDecimal.ZERO);
        }
        return NumberFormat.getCurrencyInstance(LOCALE_MX).format(n);
    }

    /** Cantidad sin ceros sobrantes (2, 1.5). Nunca "null". */
    public static String cantidad(BigDecimal n) {
        if (n == null) {
            return "0";
        }
        return n.stripTrailingZeros().toPlainString();
    }

    /** Conteo null-safe ("—" si es null, para distinguir "sin dato" de 0). */
    public static String conteo(Number n) {
        return n == null ? "—" : String.valueOf(n.longValue());
    }

    public static String fechaCorta(LocalDate fecha) {
        return fecha == null ? "—" : FECHA_CORTE.format(fecha);
    }

    public static String texto(String s, String defecto) {
        return s == null || s.isBlank() ? defecto : s;
    }

    // ── estructura ───────────────────────────────────────────────────

    /** Metadata + evento de pie. Llamar antes de {@code doc.open()}. */
    public static void preparar(Document doc, PdfWriter writer, String titulo) {
        doc.addTitle(titulo + " — " + MARCA);
        doc.addAuthor(MARCA);
        doc.addSubject(titulo);
        writer.setPageEvent(new PiePagina(titulo));
    }

    /** Encabezado de marca + título del documento + subtítulo (periodo, folio…). */
    public static void encabezado(Document doc, String tituloDoc, String subtitulo)
            throws DocumentException {
        Paragraph marca = new Paragraph(MARCA, fuenteMarca());
        marca.setAlignment(Element.ALIGN_CENTER);
        doc.add(marca);

        Paragraph titulo = new Paragraph(texto(tituloDoc, "Documento"), fuenteTitulo());
        titulo.setAlignment(Element.ALIGN_CENTER);
        doc.add(titulo);

        if (subtitulo != null && !subtitulo.isBlank()) {
            Paragraph sub = new Paragraph(subtitulo, fuentePequena());
            sub.setAlignment(Element.ALIGN_CENTER);
            doc.add(sub);
        }
        doc.add(lineaMarca());
    }

    /** Ficha etiqueta → valor en tabla de 2 columnas sin bordes. */
    public static void ficha(Document doc, String[][] pares) throws DocumentException {
        if (pares == null || pares.length == 0) {
            return;
        }
        PdfPTable tabla = new PdfPTable(new float[] { 34f, 66f });
        tabla.setWidthPercentage(100);
        for (String[] par : pares) {
            if (par == null || par.length < 2) {
                continue;
            }
            PdfPCell etiqueta = celda(par[0],
                    FontFactory.getFont(FontFactory.HELVETICA, 9, GRIS_SUAVE),
                    Element.ALIGN_LEFT, null, Rectangle.NO_BORDER, 4, 2);
            PdfPCell valor = celda(par[1],
                    FontFactory.getFont(FontFactory.HELVETICA_BOLD, 10, GRIS_TEXTO),
                    Element.ALIGN_LEFT, null, Rectangle.NO_BORDER, 4, 2);
            tabla.addCell(etiqueta);
            tabla.addCell(valor);
        }
        doc.add(tabla);
        doc.add(espacio());
    }

    public static void seccion(Document doc, String titulo) throws DocumentException {
        Paragraph p = new Paragraph(titulo, fuenteSeccion());
        p.setSpacingBefore(10);
        p.setSpacingAfter(4);
        doc.add(p);
    }

    public static Paragraph espacio() {
        return new Paragraph(" ", fuenteNormal());
    }

    public static Paragraph nota(String texto) {
        Paragraph p = new Paragraph(texto, fuentePequena());
        p.setAlignment(Element.ALIGN_CENTER);
        return p;
    }

    public static Paragraph totalDestacado(String etiqueta, BigDecimal monto) {
        Paragraph p = new Paragraph(etiqueta + ": " + moneda(monto), fuenteTotal());
        p.setAlignment(Element.ALIGN_RIGHT);
        p.setSpacingBefore(6);
        return p;
    }

    // ── tablas ───────────────────────────────────────────────────────

    public static PdfPTable tabla(float[] anchosRelativos, String[] encabezados)
            throws DocumentException {
        PdfPTable tabla = new PdfPTable(anchosRelativos);
        tabla.setWidthPercentage(100);
        tabla.setSpacingBefore(4);
        tabla.setSpacingAfter(6);
        tabla.getDefaultCell().setBorder(Rectangle.BOX);
        if (encabezados != null) {
            for (String h : encabezados) {
                tabla.addCell(celdaEncabezado(h));
            }
            tabla.setHeaderRows(1);
        }
        return tabla;
    }

    private static PdfPCell celdaEncabezado(String texto) {
        PdfPCell c = new PdfPCell(new Phrase(texto == null ? "" : texto, fuenteEncabezadoTabla()));
        c.setBackgroundColor(FONDO_ENCABEZADO);
        c.setHorizontalAlignment(Element.ALIGN_CENTER);
        c.setVerticalAlignment(Element.ALIGN_MIDDLE);
        c.setPadding(5);
        return c;
    }

    /**
     * Celda de datos. {@code fondo} null = alternancia automática por
     * {@code filaPar}; alineación y borde configurables.
     */
    public static PdfPCell celda(String texto, Font fuente, int alineacion, java.awt.Color fondo,
            int borde, float padding, float leading) {
        PdfPCell c = new PdfPCell(new Phrase(texto == null ? "" : texto,
                fuente == null ? fuenteNormal() : fuente));
        c.setHorizontalAlignment(alineacion);
        c.setVerticalAlignment(Element.ALIGN_MIDDLE);
        if (fondo != null) {
            c.setBackgroundColor(fondo);
        }
        c.setBorder(borde);
        c.setBorderColor(BORDE_CLARO);
        c.setPadding(padding);
        return c;
    }

    public static PdfPCell celdaDato(String texto, boolean filaPar) {
        return celda(texto, fuentePequenaOscura(), Element.ALIGN_LEFT,
                filaPar ? FONDO_FILA_ALT : java.awt.Color.WHITE, Rectangle.BOX, 4, 2);
    }

    public static PdfPCell celdaDatoCentrada(String texto, boolean filaPar) {
        return celda(texto, fuentePequenaOscura(), Element.ALIGN_CENTER,
                filaPar ? FONDO_FILA_ALT : java.awt.Color.WHITE, Rectangle.BOX, 4, 2);
    }

    public static PdfPCell celdaMoneda(BigDecimal monto, boolean filaPar) {
        return celda(moneda(monto), fuentePequenaOscura(), Element.ALIGN_RIGHT,
                filaPar ? FONDO_FILA_ALT : java.awt.Color.WHITE, Rectangle.BOX, 4, 2);
    }

    public static PdfPCell celdaSemaforo(String texto, boolean ok, boolean filaPar) {
        Font f = ok ? FontFactory.getFont(FontFactory.HELVETICA_BOLD, 8, VERDE_OK)
                : FontFactory.getFont(FontFactory.HELVETICA_BOLD, 8, ROJO_ALERTA);
        java.awt.Color fondo = !ok ? FONDO_ALERTA : (filaPar ? FONDO_FILA_ALT : java.awt.Color.WHITE);
        return celda(texto, f, Element.ALIGN_CENTER, fondo, Rectangle.BOX, 4, 2);
    }

    private static PdfPTable lineaMarca() throws DocumentException {
        PdfPTable linea = new PdfPTable(1);
        linea.setWidthPercentage(100);
        linea.setSpacingBefore(6);
        linea.setSpacingAfter(8);
        PdfPCell c = new PdfPCell(new Phrase(Chunk.NEWLINE));
        c.setFixedHeight(3);
        c.setBackgroundColor(COLOR_MARCA);
        c.setBorder(Rectangle.NO_BORDER);
        linea.addCell(c);
        return linea;
    }

    /** Pie con folio del documento y número de página en cada hoja. */
    static final class PiePagina extends PdfPageEventHelper {
        private final String titulo;

        PiePagina(String titulo) {
            this.titulo = titulo == null ? "" : titulo;
        }

        @Override
        public void onEndPage(PdfWriter writer, Document doc) {
            try {
                PdfPTable pie = new PdfPTable(2);
                pie.setTotalWidth(doc.right() - doc.left());
                pie.setWidths(new float[] { 70f, 30f });
                PdfPCell izq = new PdfPCell(new Phrase(titulo + " · " + MARCA,
                        FontFactory.getFont(FontFactory.HELVETICA, 7, GRIS_SUAVE)));
                izq.setBorder(Rectangle.TOP);
                izq.setBorderColor(BORDE_CLARO);
                izq.setPaddingTop(4);
                PdfPCell der = new PdfPCell(new Phrase("Página " + writer.getPageNumber(),
                        FontFactory.getFont(FontFactory.HELVETICA, 7, GRIS_SUAVE)));
                der.setHorizontalAlignment(Element.ALIGN_RIGHT);
                der.setBorder(Rectangle.TOP);
                der.setBorderColor(BORDE_CLARO);
                der.setPaddingTop(4);
                pie.addCell(izq);
                pie.addCell(der);
                pie.writeSelectedRows(0, -1, doc.left(), doc.bottom() - 10,
                        writer.getDirectContent());
            } catch (DocumentException e) {
                throw new IllegalStateException("No se pudo dibujar el pie del PDF", e);
            }
        }
    }
}

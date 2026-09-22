package mx.ferreteria.api.ven.pdf;

import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.lowagie.text.Document;
import com.lowagie.text.Element;
import com.lowagie.text.Font;
import com.lowagie.text.FontFactory;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.pdf.PdfWriter;

import lombok.RequiredArgsConstructor;
import mx.ferreteria.api.cat.entity.Cliente;
import mx.ferreteria.api.cat.repo.ClienteRepository;
import mx.ferreteria.api.cfg.entity.TicketConfig;
import mx.ferreteria.api.cfg.repo.TicketConfigRepository;
import mx.ferreteria.api.common.error.RecursoNoEncontradoException;
import mx.ferreteria.api.common.i18n.ErrorCode;
import mx.ferreteria.api.ven.entity.Venta;
import mx.ferreteria.api.ven.entity.VentaDetalle;
import mx.ferreteria.api.ven.repo.VentaDetalleRepository;
import mx.ferreteria.api.ven.repo.VentaRepository;

/**
 * Genera el PDF del ticket de venta (80 mm) con OpenPDF. Totales y folio
 * se leen solo de BD (triggers/columnas generadas), nunca del cliente.
 */
@Service
@RequiredArgsConstructor
public class TicketPdfService {

    private static final DateTimeFormatter FECHA = DateTimeFormatter
            .ofPattern("dd/MM/yyyy HH:mm")
            .withZone(ZoneId.of("America/Mexico_City"));

    private static final ZoneId ZONA = ZoneId.of("America/Mexico_City");

    private final VentaRepository ventaRepo;
    private final VentaDetalleRepository detalleRepo;
    private final ClienteRepository clienteRepo;
    private final TicketConfigRepository ticketConfigRepo;

    @Transactional(readOnly = true)
    public byte[] generarTicketPdf(Long ventaId) {
        Venta v = ventaRepo.findById(ventaId)
                .orElseThrow(() -> new RecursoNoEncontradoException(ErrorCode.RECURSO_NO_ENCONTRADO));
        List<VentaDetalle> detalles = detalleRepo.findByVentaId(ventaId);
        Cliente cliente = v.getClienteId() != null
                ? clienteRepo.findById(v.getClienteId()).orElse(null)
                : null;
        TicketConfig cfg = resolverConfig(v.getAlmacenId());

        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            // 80 mm ≈ 226.77 pt; alto suficiente para ticket POS.
            Document doc = new Document(PageSize.A4, 14, 14, 14, 14);
            PdfWriter.getInstance(doc, out);
            doc.open();

            Font fuenteTitulo = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 12);
            Font fuenteNormal = FontFactory.getFont(FontFactory.HELVETICA, 9);
            Font fuentePequena = FontFactory.getFont(FontFactory.HELVETICA, 8);

            Paragraph titulo = new Paragraph(cfg.getNombreNegocio(), fuenteTitulo);
            titulo.setAlignment(Element.ALIGN_CENTER);
            doc.add(titulo);

            if (cfg.getDireccion() != null && !cfg.getDireccion().isBlank()) {
                Paragraph dir = new Paragraph(cfg.getDireccion(), fuentePequena);
                dir.setAlignment(Element.ALIGN_CENTER);
                doc.add(dir);
            }
            if (cfg.getRfc() != null && !cfg.getRfc().isBlank()) {
                Paragraph rfc = new Paragraph("RFC: " + cfg.getRfc(), fuentePequena);
                rfc.setAlignment(Element.ALIGN_CENTER);
                doc.add(rfc);
            }
            doc.add(new Paragraph(" ", fuenteNormal));

            Paragraph docTitulo = new Paragraph(
                    Optional.ofNullable(cfg.getTituloDocumento()).orElse("Factura simplificada"),
                    FontFactory.getFont(FontFactory.HELVETICA_BOLD, 10));
            docTitulo.setAlignment(Element.ALIGN_CENTER);
            doc.add(docTitulo);

            doc.add(new Paragraph("Folio: " + v.getFolio(), fuenteNormal));
            if (v.getFecha() != null) {
                doc.add(new Paragraph("Fecha: " + FECHA.format(v.getFecha().atZone(ZONA)), fuenteNormal));
            }
            doc.add(new Paragraph("Estado: " + v.getEstado(), fuenteNormal));
            if (Boolean.TRUE.equals(cfg.getMostrarDatosCliente())) {
                if (cliente != null) {
                    doc.add(new Paragraph("Cliente: " + cliente.getRazonSocial(), fuenteNormal));
                    if (cliente.getRfc() != null && !cliente.getRfc().isBlank()) {
                        doc.add(new Paragraph("RFC: " + cliente.getRfc(), fuenteNormal));
                    }
                } else {
                    doc.add(new Paragraph("Cliente: Consumidor final", fuenteNormal));
                }
            }
            doc.add(new Paragraph(" ", fuenteNormal));

            for (VentaDetalle d : detalles) {
                String linea = String.format("%s  x%s  $%s",
                        truncar(String.valueOf(d.getProductoId()), 18),
                        d.getCantidad(),
                        money(d.getPrecioUnitario()));
                // nombre de producto no está en VentaDetalle; productoId es suficiente
                // para el ticket mínimo; el front ya renderiza nombre en preview.
                Paragraph p = new Paragraph(linea, fuentePequena);
                doc.add(p);
            }
            doc.add(new Paragraph(" ", fuenteNormal));

            if (cfg.getMostrarDesgloseIva() != null && cfg.getMostrarDesgloseIva()) {
                doc.add(new Paragraph("Subtotal: " + money(v.getSubtotal()), fuenteNormal));
                doc.add(new Paragraph("IVA (" + money(v.getIvaTasa()) + "%): " + money(v.getIva()), fuenteNormal));
            }
            if (cfg.getMostrarDescuento() != null && cfg.getMostrarDescuento()
                    && v.getDescuentoTotal() != null
                    && v.getDescuentoTotal().compareTo(BigDecimal.ZERO) > 0) {
                doc.add(new Paragraph("Descuento: -" + money(v.getDescuentoTotal()), fuenteNormal));
            }
            Paragraph total = new Paragraph("TOTAL: " + money(v.getTotal()),
                    FontFactory.getFont(FontFactory.HELVETICA_BOLD, 11));
            total.setAlignment(Element.ALIGN_RIGHT);
            doc.add(total);

            if (cfg.getMensajePie() != null && !cfg.getMensajePie().isBlank()) {
                doc.add(new Paragraph(" ", fuenteNormal));
                Paragraph pie = new Paragraph(cfg.getMensajePie(), fuentePequena);
                pie.setAlignment(Element.ALIGN_CENTER);
                doc.add(pie);
            }
            if (cfg.getPieSecundario() != null && !cfg.getPieSecundario().isBlank()) {
                Paragraph pie2 = new Paragraph(cfg.getPieSecundario(), fuentePequena);
                pie2.setAlignment(Element.ALIGN_CENTER);
                doc.add(pie2);
            }

            doc.close();
            return out.toByteArray();
        } catch (Exception e) {
            throw new IllegalStateException("No se pudo generar el PDF del ticket", e);
        }
    }

    private TicketConfig resolverConfig(Integer almacenId) {
        if (almacenId != null) {
            Optional<TicketConfig> porAlmacen = ticketConfigRepo.findByAlmacenId(almacenId);
            if (porAlmacen.isPresent()) {
                return porAlmacen.get();
            }
        }
        return ticketConfigRepo.findByAlmacenIdIsNull().orElseGet(() -> {
            TicketConfig def = new TicketConfig();
            def.setNombreNegocio("Ferretería El Tornillo Feliz");
            def.setTituloDocumento("Factura simplificada");
            def.setMostrarDatosCliente(true);
            def.setMostrarDesgloseIva(true);
            def.setMostrarDescuento(true);
            def.setMensajePie("30 DÍAS PARA DEVOLUCIONES O CAMBIOS");
            def.setAnchoPapelMm((short) 80);
            def.setFontSizePt((short) 9);
            return def;
        });
    }

    private static String money(BigDecimal n) {
        if (n == null) {
            return "0.00";
        }
        return String.format(java.util.Locale.US, "%.2f", n);
    }

    private static String truncar(String s, int max) {
        if (s == null) {
            return "";
        }
        return s.length() <= max ? s : s.substring(0, max);
    }
}

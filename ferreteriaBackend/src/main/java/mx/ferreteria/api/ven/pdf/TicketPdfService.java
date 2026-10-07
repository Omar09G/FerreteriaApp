package mx.ferreteria.api.ven.pdf;

import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.lowagie.text.Document;
import com.lowagie.text.Element;
import com.lowagie.text.PageSize;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;

import lombok.RequiredArgsConstructor;
import mx.ferreteria.api.cat.entity.Cliente;
import mx.ferreteria.api.cat.entity.Producto;
import mx.ferreteria.api.cat.repo.ClienteRepository;
import mx.ferreteria.api.cat.repo.ProductoRepository;
import mx.ferreteria.api.cfg.entity.TicketConfig;
import mx.ferreteria.api.cfg.repo.TicketConfigRepository;
import mx.ferreteria.api.common.error.RecursoNoEncontradoException;
import mx.ferreteria.api.common.i18n.ErrorCode;
import mx.ferreteria.api.common.pdf.PdfEstilo;
import mx.ferreteria.api.ven.entity.Venta;
import mx.ferreteria.api.ven.entity.VentaDetalle;
import mx.ferreteria.api.ven.repo.VentaDetalleRepository;
import mx.ferreteria.api.ven.repo.VentaRepository;

/**
 * Genera el PDF del ticket de venta con OpenPDF. Totales y folio
 * se leen solo de BD (triggers/columnas generadas), nunca del cliente.
 * Tabla de partidas con nombre de producto, importes por línea y
 * totales alineados para lectura rápida en correo.
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
    private final ProductoRepository productoRepo;

    @Transactional(readOnly = true)
    public byte[] generarTicketPdf(Long ventaId) {
        Venta v = ventaRepo.findById(ventaId)
                .orElseThrow(() -> new RecursoNoEncontradoException(ErrorCode.RECURSO_NO_ENCONTRADO));
        List<VentaDetalle> detalles = detalleRepo.findByVentaId(ventaId);
        Cliente cliente = v.getClienteId() != null
                ? clienteRepo.findById(v.getClienteId()).orElse(null)
                : null;
        TicketConfig cfg = resolverConfig(v.getAlmacenId());
        Map<Long, Producto> productos = resolverProductos(detalles);
        String tituloDoc = Optional.ofNullable(cfg.getTituloDocumento()).orElse("Factura simplificada");

        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Document doc = new Document(PageSize.A4, 36, 36, 40, 44);
            PdfWriter writer = PdfWriter.getInstance(doc, out);
            PdfEstilo.preparar(doc, writer, tituloDoc + " " + v.getFolio());
            doc.open();

            PdfEstilo.encabezado(doc, cfg.getNombreNegocio(), tituloDoc);
            if (cfg.getDireccion() != null && !cfg.getDireccion().isBlank()) {
                doc.add(PdfEstilo.nota(cfg.getDireccion()));
            }
            if (cfg.getRfc() != null && !cfg.getRfc().isBlank()) {
                doc.add(PdfEstilo.nota("RFC: " + cfg.getRfc()));
            }
            doc.add(PdfEstilo.espacio());

            PdfEstilo.ficha(doc, new String[][] {
                    { "Folio", PdfEstilo.texto(v.getFolio(), "—") },
                    { "Fecha", v.getFecha() != null ? FECHA.format(v.getFecha().atZone(ZONA)) : "—" },
                    { "Estado", PdfEstilo.texto(v.getEstado(), "—") },
                    { "Cliente", nombreCliente(cliente, cfg) },
            });

            PdfEstilo.seccion(doc, "Productos (" + detalles.size() + ")");
            PdfPTable tabla = PdfEstilo.tabla(
                    new float[] { 12f, 48f, 20f, 20f },
                    new String[] { "Cant.", "Producto", "P. unitario", "Importe" });
            boolean par = false;
            for (VentaDetalle d : detalles) {
                Objects.requireNonNull(d, "detalle de venta nulo");
                Producto p = d.getProductoId() != null ? productos.get(d.getProductoId()) : null;
                tabla.addCell(PdfEstilo.celdaDatoCentrada(PdfEstilo.cantidad(d.getCantidad()), par));
                tabla.addCell(PdfEstilo.celda(descripcionProducto(d, p),
                        PdfEstilo.fuentePequenaOscura(), Element.ALIGN_LEFT,
                        par ? PdfEstilo.FONDO_FILA_ALT : null,
                        com.lowagie.text.Rectangle.BOX, 4, 2));
                tabla.addCell(PdfEstilo.celdaMoneda(d.getPrecioUnitario(), par));
                tabla.addCell(PdfEstilo.celdaMoneda(importeLinea(d), par));
                par = !par;
            }
            if (detalles.isEmpty()) {
                tabla.addCell(PdfEstilo.celdaDato("Sin productos registrados en esta venta.", false));
                tabla.addCell(PdfEstilo.celdaDato("", false));
                tabla.addCell(PdfEstilo.celdaDato("", false));
                tabla.addCell(PdfEstilo.celdaDato("", false));
            }
            doc.add(tabla);

            PdfEstilo.seccion(doc, "Totales");
            PdfPTable totales = PdfEstilo.tabla(new float[] { 60f, 40f }, null);
            if (Boolean.TRUE.equals(cfg.getMostrarDesgloseIva())) {
                agregarTotal(totales, "Subtotal", v.getSubtotal(), false);
                agregarTotal(totales, "IVA (" + PdfEstilo.cantidad(v.getIvaTasa()) + "%)",
                        v.getIva(), false);
            }
            if (Boolean.TRUE.equals(cfg.getMostrarDescuento())
                    && v.getDescuentoTotal() != null
                    && v.getDescuentoTotal().compareTo(BigDecimal.ZERO) > 0) {
                agregarTotal(totales, "Descuento", v.getDescuentoTotal().negate(), false);
            }
            doc.add(totales);
            doc.add(PdfEstilo.totalDestacado("TOTAL", v.getTotal()));

            if (v.getNotas() != null && !v.getNotas().isBlank()) {
                PdfEstilo.seccion(doc, "Notas");
                doc.add(new com.lowagie.text.Paragraph(v.getNotas(), PdfEstilo.fuenteNormal()));
            }

            if (cfg.getMensajePie() != null && !cfg.getMensajePie().isBlank()) {
                doc.add(PdfEstilo.espacio());
                doc.add(PdfEstilo.nota(cfg.getMensajePie()));
            }
            if (cfg.getPieSecundario() != null && !cfg.getPieSecundario().isBlank()) {
                doc.add(PdfEstilo.nota(cfg.getPieSecundario()));
            }

            doc.close();
            return out.toByteArray();
        } catch (Exception e) {
            throw new IllegalStateException("No se pudo generar el PDF del ticket", e);
        }
    }

    private void agregarTotal(PdfPTable totales, String etiqueta, BigDecimal monto, boolean par) {
        totales.addCell(PdfEstilo.celda(etiqueta, PdfEstilo.fuenteNormal(),
                Element.ALIGN_RIGHT, null, com.lowagie.text.Rectangle.NO_BORDER, 3, 2));
        totales.addCell(PdfEstilo.celda(PdfEstilo.moneda(monto),
                com.lowagie.text.FontFactory.getFont(
                        com.lowagie.text.FontFactory.HELVETICA_BOLD, 10, PdfEstilo.GRIS_TEXTO),
                Element.ALIGN_RIGHT, null, com.lowagie.text.Rectangle.NO_BORDER, 3, 2));
    }

    private Map<Long, Producto> resolverProductos(List<VentaDetalle> detalles) {
        List<Long> ids = detalles == null ? List.of()
                : detalles.stream()
                        .filter(Objects::nonNull)
                        .map(VentaDetalle::getProductoId)
                        .filter(Objects::nonNull)
                        .distinct()
                        .toList();
        if (ids.isEmpty()) {
            return Map.of();
        }
        Map<Long, Producto> mapa = new HashMap<>();
        for (Producto p : productoRepo.findAllById(ids)) {
            if (p != null && p.getProductoId() != null) {
                mapa.put(p.getProductoId(), p);
            }
        }
        return mapa;
    }

    private static String descripcionProducto(VentaDetalle d, Producto p) {
        if (p != null && p.getNombre() != null && !p.getNombre().isBlank()) {
            String codigo = p.getCodigo() != null && !p.getCodigo().isBlank()
                    ? " · " + p.getCodigo()
                    : "";
            return p.getNombre() + codigo;
        }
        return "Producto #" + d.getProductoId();
    }

    private static BigDecimal importeLinea(VentaDetalle d) {
        BigDecimal cant = d.getCantidad() == null ? BigDecimal.ZERO : d.getCantidad();
        BigDecimal precio = d.getPrecioUnitario() == null ? BigDecimal.ZERO : d.getPrecioUnitario();
        BigDecimal desc = d.getDescuentoLinea() == null ? BigDecimal.ZERO : d.getDescuentoLinea();
        if (d.getTotalLinea() != null) {
            return d.getTotalLinea();
        }
        return cant.multiply(precio).subtract(desc);
    }

    private static String nombreCliente(Cliente cliente, TicketConfig cfg) {
        if (!Boolean.TRUE.equals(cfg.getMostrarDatosCliente())) {
            return "—";
        }
        if (cliente == null) {
            return "Consumidor final";
        }
        String razon = PdfEstilo.texto(cliente.getRazonSocial(), "Consumidor final");
        if (cliente.getRfc() != null && !cliente.getRfc().isBlank()) {
            return razon + " · RFC " + cliente.getRfc();
        }
        return razon;
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
}

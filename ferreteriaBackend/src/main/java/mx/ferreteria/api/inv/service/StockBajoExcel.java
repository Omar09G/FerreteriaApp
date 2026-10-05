package mx.ferreteria.api.inv.service;

import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.util.List;

import org.apache.poi.ss.usermodel.BorderStyle;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.HorizontalAlignment;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import mx.ferreteria.api.inv.dto.InvDtos.InventarioResponse;

/**
 * Excel del recordatorio de stock bajo: una fila por producto-almacén con
 * existencia, mínimo, faltante y estado. Sin estado Spring (función pura).
 */
public final class StockBajoExcel {

    /** Tope de filas del detalle (anti-archivos gigantes). */
    public static final int MAX_FILAS = 2000;

    private StockBajoExcel() {
    }

    public static byte[] generar(List<InventarioResponse> filas) {
        List<InventarioResponse> recorte = filas.size() > MAX_FILAS
                ? filas.subList(0, MAX_FILAS)
                : filas;
        try (Workbook libro = new XSSFWorkbook();
                ByteArrayOutputStream salida = new ByteArrayOutputStream()) {
            Sheet hoja = libro.createSheet("Bajo stock");
            String[] encabezados = { "Código", "Producto", "Almacén", "Existencia",
                    "Mínimo", "Máximo", "Faltante", "Estado" };

            CellStyle cabecera = libro.createCellStyle();
            Font negrita = libro.createFont();
            negrita.setBold(true);
            negrita.setColor(IndexedColors.WHITE.getIndex());
            cabecera.setFont(negrita);
            cabecera.setFillForegroundColor(IndexedColors.ORANGE.getIndex());
            cabecera.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            cabecera.setAlignment(HorizontalAlignment.CENTER);
            bordear(cabecera);

            CellStyle numero = libro.createCellStyle();
            numero.setDataFormat(libro.createDataFormat().getFormat("#,##0.##"));
            bordear(numero);

            CellStyle texto = libro.createCellStyle();
            bordear(texto);

            CellStyle alerta = libro.createCellStyle();
            Font roja = libro.createFont();
            roja.setBold(true);
            roja.setColor(IndexedColors.RED.getIndex());
            alerta.setFont(roja);
            alerta.setDataFormat(libro.createDataFormat().getFormat("#,##0.##"));
            bordear(alerta);

            Row fila0 = hoja.createRow(0);
            for (int c = 0; c < encabezados.length; c++) {
                Cell celda = fila0.createCell(c);
                celda.setCellValue(encabezados[c]);
                celda.setCellStyle(cabecera);
            }
            int r = 1;
            for (InventarioResponse f : recorte) {
                Row fila = hoja.createRow(r++);
                BigDecimal stock = nuloCero(f.stock());
                BigDecimal minimo = nuloCero(f.stockMinimo());
                BigDecimal faltante = minimo.subtract(stock);
                if (faltante.signum() < 0) {
                    faltante = BigDecimal.ZERO;
                }
                boolean agotado = stock.signum() <= 0;
                celdaTexto(fila, 0, f.productoCodigo(), texto);
                celdaTexto(fila, 1, f.productoNombre(), texto);
                celdaTexto(fila, 2, f.almacenNombre(), texto);
                celdaNumero(fila, 3, stock, agotado ? alerta : numero);
                celdaNumero(fila, 4, minimo, numero);
                celdaNumero(fila, 5, f.stockMaximo(), numero);
                celdaNumero(fila, 6, faltante, agotado ? alerta : numero);
                celdaTexto(fila, 7, agotado ? "AGOTADO" : "BAJO", texto);
            }
            hoja.createFreezePane(0, 1);
            hoja.setAutoFilter(new org.apache.poi.ss.util.CellRangeAddress(
                    0, 0, 0, encabezados.length - 1));
            for (int c = 0; c < encabezados.length; c++) {
                hoja.autoSizeColumn(c);
                // autoSize sin límite deforma con textos largos: tope razonable.
                if (hoja.getColumnWidth(c) > 40 * 256) {
                    hoja.setColumnWidth(c, 40 * 256);
                }
            }
            libro.write(salida);
            return salida.toByteArray();
        } catch (Exception e) {
            throw new IllegalStateException("No se pudo generar el Excel de stock bajo", e);
        }
    }

    private static BigDecimal nuloCero(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v;
    }

    private static void bordear(CellStyle estilo) {
        estilo.setBorderTop(BorderStyle.THIN);
        estilo.setBorderBottom(BorderStyle.THIN);
        estilo.setBorderLeft(BorderStyle.THIN);
        estilo.setBorderRight(BorderStyle.THIN);
    }

    private static void celdaTexto(Row fila, int col, String valor, CellStyle estilo) {
        Cell celda = fila.createCell(col);
        celda.setCellValue(valor == null ? "" : valor);
        celda.setCellStyle(estilo);
    }

    private static void celdaNumero(Row fila, int col, BigDecimal valor, CellStyle estilo) {
        Cell celda = fila.createCell(col);
        if (valor == null) {
            celda.setCellValue("");
        } else {
            celda.setCellValue(valor.doubleValue());
        }
        celda.setCellStyle(estilo);
    }
}

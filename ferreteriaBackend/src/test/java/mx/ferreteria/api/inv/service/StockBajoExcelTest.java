package mx.ferreteria.api.inv.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.util.List;

import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import mx.ferreteria.api.inv.dto.InvDtos.InventarioResponse;

class StockBajoExcelTest {

    private static InventarioResponse fila(String codigo, String stock, String minimo) {
        return new InventarioResponse(1L, "Tornillo 1/4", codigo, 1, "Central",
                new BigDecimal(stock), new BigDecimal(minimo),
                new BigDecimal("100"), BigDecimal.ZERO);
    }

    @SuppressWarnings("resource")
    private static Sheet hoja(byte[] xlsx) throws Exception {
        Workbook libro = new XSSFWorkbook(new ByteArrayInputStream(xlsx));
        assertThat(libro.getNumberOfSheets()).isEqualTo(1);
        return libro.getSheetAt(0);
    }

    @Test
    @DisplayName("genera hoja con encabezados, filas y estado AGOTADO/BAJO")
    void generar_estructura() throws Exception {
        Sheet hoja = hoja(StockBajoExcel.generar(List.of(
                fila("T-1", "5", "10"),
                fila("T-2", "0", "10"))));

        assertThat(hoja.getRow(0).getCell(0).getStringCellValue()).isEqualTo("Código");
        assertThat(hoja.getLastRowNum()).isEqualTo(2);
        assertThat(hoja.getRow(1).getCell(1).getStringCellValue()).contains("Tornillo");
        assertThat(hoja.getRow(1).getCell(3).getNumericCellValue()).isEqualTo(5.0);
        assertThat(hoja.getRow(1).getCell(6).getNumericCellValue()).isEqualTo(5.0);
        assertThat(hoja.getRow(1).getCell(7).getStringCellValue()).isEqualTo("BAJO");
        assertThat(hoja.getRow(2).getCell(7).getStringCellValue()).isEqualTo("AGOTADO");
    }

    @Test
    @DisplayName("respeta el tope de filas")
    void generar_tope() throws Exception {
        var filas = new java.util.ArrayList<InventarioResponse>();
        for (int i = 0; i < StockBajoExcel.MAX_FILAS + 10; i++) {
            filas.add(fila("T-" + i, "1", "10"));
        }
        Sheet hoja = hoja(StockBajoExcel.generar(filas));

        assertThat(hoja.getLastRowNum()).isEqualTo(StockBajoExcel.MAX_FILAS);
    }
}

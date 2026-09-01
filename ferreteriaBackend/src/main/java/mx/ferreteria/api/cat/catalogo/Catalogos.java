package mx.ferreteria.api.cat.catalogo;

import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Component;

/**
 * Registro de catálogos para el CRUD genérico de ADMINISTRACIÓN. Cada catálogo
 * se declara una sola vez; el engine (controller/service/repo) y el front
 * consumen estos descriptores y muestran el CRUD completo sin codeo por tabla.
 *
 * Solo se registran aquí las tablas que NO tienen endpoint/página dedicada hoy
 * (p. ej. categorías, marcas, unidades_medida, almacenes, cajas y roles ya
 * tienen CRUD propio y se enlazan desde el índice, no se duplican aquí).
 * Escritura: solo ADMINISTRADOR. Lectura: cualquier autenticado (POS/ventas).
 */
@Component
public class Catalogos {

    public static final List<Catalogo> TODOS = List.of(
            // ── cat ─────────────────────────────────────────────────
            c("estados", "cat.estados", "Estados", "estado_id",
                    List.of(Campo.textoUnico("clave_inegi", "Clave INEGI", true),
                            Campo.textoUnico("nombre", "Nombre", true)),
                    null, false),
            c("ciudades", "cat.ciudades", "Ciudades", "ciudad_id",
                    List.of(Campo.fk("estado_id", "Estado", true, "estados",
                                    List.of("nombre")),
                            Campo.texto("nombre", "Nombre", true)),
                    null, false),
            c("puestos", "cat.puestos", "Puestos", "puesto_id",
                    List.of(Campo.textoUnico("nombre", "Nombre", true),
                            Campo.decimal("sueldo_base", "Sueldo base", false), Campo.activo()),
                    null, true),
            c("motivos_movimiento", "cat.motivos_movimiento", "Motivos de movimiento", "motivo_id",
                    List.of(Campo.textoUnico("clave", "Clave", true), Campo.texto("nombre", "Nombre", true),
                            Campo.texto("tipo_default", "Tipo por defecto", true), Campo.activo()),
                    Map.of("tipo_default", List.of("ENTRADA", "SALIDA")), true),
            c("tipos_gasto", "cat.tipos_gasto", "Tipos de gasto", "tipo_gasto_id",
                    List.of(Campo.textoUnico("clave", "Clave", true), Campo.texto("nombre", "Nombre", true),
                            Campo.booleano("es_fijo", "Es fijo", false), Campo.activo()),
                    null, true),
            c("formas_pago", "cat.formas_pago", "Formas de pago", "forma_pago_id",
                    List.of(Campo.textoUnico("clave", "Clave", true), Campo.texto("nombre", "Nombre", true),
                            Campo.booleano("es_efectivo", "Es efectivo", false),
                            Campo.booleano("requiere_referencia", "Requiere referencia", false),
                            Campo.booleano("afecta_caja", "Afecta caja", false),
                            Campo.fkTexto("forma_pago_sat", "Forma pago SAT", false, "formas_pago_sat",
                                    List.of("descripcion")),
                            Campo.decimal("comision_pct", "Comisión %", false), Campo.activo()),
                    null, true),

            // ── fis ─────────────────────────────────────────────────
            c("impuestos", "fis.impuestos", "Impuestos", "impuesto_id",
                    List.of(Campo.textoUnico("clave_sat", "Clave SAT", true), Campo.texto("nombre", "Nombre", true),
                            Campo.texto("tipo", "Tipo", true), Campo.activo()),
                    Map.of("tipo", List.of("TRASLADADO", "RETENIDO", "LOCAL")), true),
            c("tasas_impuesto", "fis.tasas_impuesto", "Tasas de impuesto", "tasa_id",
                    List.of(Campo.fk("impuesto_id", "Impuesto", true, "impuestos",
                                    List.of("nombre")),
                            Campo.decimal("tasa", "Tasa", true),
                            Campo.texto("factor", "Factor", true),
                            Campo.texto("ambito", "Ámbito", true),
                            Campo.booleano("zona_frontera", "Zona frontera", false),
                            Campo.fecha("vigente_desde", "Vigente desde", true),
                            Campo.fecha("vigente_hasta", "Vigente hasta", false),
                            Campo.activo()),
                    Map.of("factor", List.of("TASA", "CUOTA", "EXENTO"),
                            "ambito", List.of("VENTA", "COMPRA", "NOMINA")), true),
            c("regimenes_fiscales", "fis.regimenes_fiscales", "Regímenes fiscales", "clave_sat",
                    List.of(Campo.textoUnico("clave_sat", "Clave SAT", true), Campo.texto("descripcion", "Descripción", true),
                            Campo.booleano("persona_fisica", "Persona física", false),
                            Campo.booleano("persona_moral", "Persona moral", false), Campo.activo()),
                    null, true),
            c("usos_cfdi", "fis.usos_cfdi", "Usos CFDI", "clave",
                    List.of(Campo.textoUnico("clave", "Clave", true), Campo.texto("descripcion", "Descripción", true),
                            Campo.booleano("aplica_fisica", "Aplica física", false),
                            Campo.booleano("aplica_moral", "Aplica moral", false), Campo.activo()),
                    null, true),
            c("formas_pago_sat", "fis.formas_pago_sat", "Formas de pago SAT", "clave",
                    List.of(Campo.textoUnico("clave", "Clave", true), Campo.texto("descripcion", "Descripción", true),
                            Campo.activo()),
                    null, true),
            c("metodos_pago_sat", "fis.metodos_pago_sat", "Métodos de pago SAT", "clave",
                    List.of(Campo.textoUnico("clave", "Clave", true), Campo.texto("descripcion", "Descripción", true),
                            Campo.activo()),
                    null, true),
            c("unidades_sat", "fis.unidades_sat", "Unidades SAT", "clave",
                    List.of(Campo.textoUnico("clave", "Clave", true), Campo.texto("descripcion", "Descripción", true),
                            Campo.activo()),
                    null, true),
            c("claves_prod_serv", "fis.claves_prod_serv", "Claves producto/servicio", "clave",
                    List.of(Campo.textoUnico("clave", "Clave", true), Campo.texto("descripcion", "Descripción", true),
                            Campo.booleano("incluye_iva", "Incluye IVA", false),
                            Campo.booleano("ejemplo", "Ejemplo", false)),
                    null, false),

            // ── cfg ─────────────────────────────────────────────────
            c("configuracion", "cfg.configuracion", "Configuración", "clave",
                    List.of(Campo.textoUnico("clave", "Clave", true), Campo.texto("valor", "Valor", true),
                            Campo.texto("descripcion", "Descripción", false)),
                    null, false),
            c("folios", "cfg.folios", "Folios", "tipo",
                    List.of(Campo.textoUnico("tipo", "Tipo", true), Campo.texto("prefijo", "Prefijo", true),
                            Campo.numero("consecutivo", "Consecutivo", true)),
                    null, false),

            // ── seg ─────────────────────────────────────────────────
            c("permisos", "seg.permisos", "Permisos", "permiso_id",
                    List.of(Campo.textoUnico("clave", "Clave", true), Campo.texto("descripcion", "Descripción", true)),
                    null, false));

    private static Catalogo c(String clave, String tabla, String nombre, String pk,
                              List<Campo> campos, Map<String, List<String>> listasValidas, boolean bajaLogica) {
        return new Catalogo(clave, tabla, nombre, pk, campos, listasValidas, bajaLogica);
    }

    public Catalogo porClave(String clave) {
        return TODOS.stream().filter(x -> x.clave().equals(clave)).findFirst().orElse(null);
    }
}

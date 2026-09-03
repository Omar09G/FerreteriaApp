export default {
	auditoria: {
		titulo: "Auditoría",
		subtitulo:
			"Cambios, altas y bajas registradas en el sistema. Filtros disponibles: esquema, tabla, acción, usuario, registro, fechas y texto libre.",
		sinResultados: "Sin movimientos",
		sinResultadosDesc:
			"No hay registros que coincidan con los filtros aplicados.",
		filtros: {
			esquema: "Esquema",
			tabla: "Tabla",
			accion: "Acción",
			usuario: "Usuario (ILIKE)",
			placeholderUsuario: "Ej. admin",
			registro: "Registro ID",
			texto: "Texto (JSONB / datos)",
			placeholderTexto: 'Ej. "estado":',
			rango: "Rango de fechas",
			limpiar: "Limpiar",
			todos: "Todos",
			todas: "Todas",
			todasAcciones: "Todas",
		},
		columnas: {
			fecha: "Fecha",
			usuario: "Usuario",
			origen: "Esquema/Tabla",
			accion: "Acción",
			cambios: "Cambios",
		},
		botones: {
			ver: "Ver",
			ocultar: "Ocultar",
		},
		campos: {
			anterior: "Anterior",
			nuevo: "Nuevo",
		},
	},
} as const;

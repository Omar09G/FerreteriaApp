export default {
  auditoria: {
    titulo: 'Audit log',
    subtitulo:
      'Inserts, updates and deletes recorded by the system. Available filters: schema, table, action, user, record id, dates and free text.',
    sinResultados: 'No entries',
    sinResultadosDesc: 'No records match the applied filters.',
    filtros: {
      esquema: 'Schema',
      tabla: 'Table',
      accion: 'Action',
      usuario: 'User (ILIKE)',
      placeholderUsuario: 'E.g. admin',
      registro: 'Record ID',
      texto: 'Text (JSONB / data)',
      placeholderTexto: 'E.g. "status":',
      rango: 'Date range',
      limpiar: 'Clear',
      todos: 'All',
      todas: 'All',
      todasAcciones: 'All',
    },
    columnas: {
      fecha: 'Date',
      usuario: 'User',
      origen: 'Schema/Table',
      accion: 'Action',
      cambios: 'Changes',
    },
    botones: {
      ver: 'Show',
      ocultar: 'Hide',
    },
    campos: {
      anterior: 'Previous',
      nuevo: 'New',
    },
  },
} as const
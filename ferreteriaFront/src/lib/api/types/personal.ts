// Dominio extraído de ../types.ts (barril re-exporta todo; imports intactos).
/* ── Empleados / usuarios ────────────────────────────────────────── */

export interface Empleado {
  empleadoId: number;
  puestoId: number;
  puestoNombre: string;
  nombre: string;
  apellidoPaterno: string;
  apellidoMaterno: string;
  curp: string | null;
  nss: string | null;
  telefono: string | null;
  email: string | null;
  calle: string | null;
  colonia: string | null;
  ciudadId: number | null;
  cp: string | null;
  fechaIngreso: string | null;
  fechaBaja: string | null;
  sueldoDiario: number;
  activo: boolean;
  /** URL pública de la foto (MinIO). Null cuando el empleado no tiene. */
  fotoUrl?: string | null;
}

export interface EmpleadoCreateRequest {
  puestoId: number;
  nombre: string;
  apellidoPaterno: string;
  apellidoMaterno?: string;
  curp?: string;
  nss?: string;
  telefono?: string;
  email?: string;
  sueldoDiario?: number;
  username?: string;
  password?: string;
  roles?: string[];
  fotoUrl?: string | null;
}

export interface Usuario {
  usuarioId: number;
  username: string;
  email: string;
  empleadoId: number | null;
  activo: boolean;
  roles: string[];
  empleado: {
    empleadoId: number;
    nombreCompleto: string;
    puestoNombre: string;
    email: string | null;
    telefono: string | null;
    activo: boolean;
    fotoUrl?: string | null;
  } | null;
  ultimoLogin: string | null;
  creadoEn: string;
}

export interface UsuarioCreateRequest {
  username: string;
  email: string;
  password: string;
  empleadoId?: number;
  roles?: string[];
}

export interface Rol {
  rolId: number;
  clave: string;
  nombre: string;
  descripcion: string | null;
  activo: boolean;
  permisos: string[];
}

export interface RolRequest {
  clave: string;
  nombre: string;
  descripcion?: string;
  activo?: boolean;
}

export interface Permiso {
  permisoId: number;
  clave: string;
  descripcion: string;
}

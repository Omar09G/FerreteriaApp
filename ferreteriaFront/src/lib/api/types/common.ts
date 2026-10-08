// Dominio extraído de ../types.ts (barril re-exporta todo; imports intactos).
export interface PageResult {
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
}

export interface PageEnvelope<T> {
  success: boolean;
  data: T[];
  meta: PageResult;
}

export interface Envelope<T> {
  success: boolean;
  data: T;
}

export interface FieldError {
  field: string;
  error: string;
}

export interface ApiErrorBody {
  success: boolean;
  data: null;
  errorCode: number;
  codigo: string;
  errorMessage: string;
  details?: FieldError[];
  requestId?: string;
  instance?: string;
}

export interface MeEmpleado {
  empleadoId: number;
  nombreCompleto: string;
  puestoNombre: string;
  email?: string;
  telefono?: string;
  activo: boolean;
}

export interface MeResponse {
  usuarioId: number;
  username: string;
  empleadoId?: number;
  roles: string[];
  ultimoLogin?: string;
  empleado?: MeEmpleado;
}

export interface TokenResponse {
  accessToken: string;
  // El refresh token ahora viaja SOLO en la cookie HttpOnly del backend.
  // El backend puede aún emitirlo en body para compatibilidad con clientes
  // no-browser; el front lo ignora (lo lee vía cookie).
  refreshToken?: string | null;
  expiresInSeconds: number;
  usuario: MeResponse;
}

export interface LoginRequest {
  username: string;
  password: string;
}

/** Segunda fase obligatoria: desafío OTP con canales y destinos enmascarados. */
export interface OtpChallenge {
  challengeId: string;
  canales: ("email" | "whatsapp")[];
  emailEnmascarado: string | null;
  whatsappEnmascarado: string | null;
  expiraEnSegundos: number;
}

export type OtpCanal = "email" | "whatsapp";

export interface OtpEnviarRequest {
  challengeId: string;
  canal: OtpCanal;
}

export interface OtpVerificarRequest {
  challengeId: string;
  codigo: string;
}

export interface GoogleInit {
  url: string;
}

export interface RefreshRequest {
  refreshToken?: string;
}

export interface ChangePasswordRequest {
  passwordActual: string;
  nuevaPassword: string;
}

export interface PasswordOk {
  cambiada: boolean;
}

export interface LogoutOk {
  revocado: boolean;
}

export interface OperacionOk {
  ok: boolean;
}

import http from "./client";
import type {
	ChangePasswordRequest,
	Envelope,
	GoogleInit,
	LoginRequest,
	LogoutOk,
	MeResponse,
	OperacionOk,
	OtpChallenge,
	OtpEnviarRequest,
	OtpVerificarRequest,
	PasswordOk,
	TokenResponse,
} from "./types";

/**
 * Primera fase del login (password): devuelve un desafío OTP, NO tokens.
 * La sesión se abre en apiVerificarOtp.
 */
export async function apiLogin(payload: LoginRequest): Promise<OtpChallenge> {
	const { data } = await http.post<Envelope<OtpChallenge>>(
		"/auth/login",
		payload,
	);
	return data.data;
}

/** Envía (o reenvía) el código de 6 dígitos por el canal elegido. */
export async function apiSolicitarOtp(payload: OtpEnviarRequest): Promise<void> {
	await http.post("/auth/otp/enviar", payload);
}

/** Segunda fase: verifica el código y abre la sesión (cookies HttpOnly). */
export async function apiVerificarOtp(
	payload: OtpVerificarRequest,
): Promise<TokenResponse> {
	const { data } = await http.post<Envelope<TokenResponse>>(
		"/auth/otp/verificar",
		payload,
	);
	return data.data;
}

/** URL de autorización de Google generada por el backend (redirect). */
export async function apiGoogleInit(state?: string): Promise<GoogleInit> {
	const { data } = await http.get<Envelope<GoogleInit>>("/auth/oauth2/google", {
		params: state ? { state } : {},
	});
	return data.data;
}

/**
 * Refresca el access token. El refresh vive en cookie HttpOnly, así que NO
 * enviamos refreshToken en el body: el browser lo adjunta solo gracias a
 * `withCredentials: true` en el cliente axios.
 */
export async function apiRefresh(): Promise<TokenResponse> {
	const { data } = await http.post<Envelope<TokenResponse>>(
		"/auth/refresh",
		{},
	);
	return data.data;
}

/**
 * Cierra la sesión. Igual que refresh: el browser envía la cookie `rt`
 * automáticamente; el body va vacío.
 */
export async function apiLogout(): Promise<LogoutOk> {
	const { data } = await http.post<Envelope<LogoutOk>>("/auth/logout", {});
	return data.data;
}

export async function apiCambiarPassword(
	body: ChangePasswordRequest,
): Promise<PasswordOk> {
	const { data } = await http.post<Envelope<PasswordOk>>(
		"/auth/change-password",
		body,
	);
	return data.data;
}

export async function apiMe(): Promise<MeResponse> {
	const { data } = await http.get<Envelope<MeResponse>>("/auth/me");
	return data.data;
}

export async function apiEliminar(_path: string): Promise<OperacionOk> {
	const { data } = await http.delete<Envelope<OperacionOk>>(_path);
	return data.data;
}

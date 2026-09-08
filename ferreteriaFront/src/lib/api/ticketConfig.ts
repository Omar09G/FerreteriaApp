import http from "./client";
import type { Envelope, TicketConfig, TicketConfigRequest } from "./types";

export async function apiGetTicketConfig(almacenId?: number | null): Promise<TicketConfig> {
  const params: Record<string, string | number> = {};
  if (almacenId != null) params.almacenId = almacenId;
  const { data } = await http.get<Envelope<TicketConfig>>("/configuracion/ticket", { params });
  return data.data;
}

export async function apiPutTicketConfig(body: TicketConfigRequest): Promise<TicketConfig> {
  const { data } = await http.put<Envelope<TicketConfig>>("/configuracion/ticket", body);
  return data.data;
}

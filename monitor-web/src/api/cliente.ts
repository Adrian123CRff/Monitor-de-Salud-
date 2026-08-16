import type { Alerta, Calibracion, Componente, Isbd, Muestra, ProblemDetail, Tablespace } from './tipos';

// ADR 0001: una sola instancia Oracle (Docker), coincide con monitor.instancia-id
// del backend (planificador). No hay RepositorioInstancias todavía -- ver ADR 0001.
export const INSTANCIA_ID = 1;

const BASE = '/api/v1';

/** No hay ningún Isbd calculado todavía (SaludNoDisponibleException -> 404). */
export class SinDatosAunError extends Error {}

async function obtener<T>(ruta: string): Promise<T> {
  const resp = await fetch(ruta);
  if (resp.status === 404) {
    const problema = (await resp.json()) as ProblemDetail;
    throw new SinDatosAunError(problema.detail);
  }
  if (!resp.ok) {
    const problema = await resp.json().catch(() => null as ProblemDetail | null);
    throw new Error(problema?.detail ?? `${resp.status} ${resp.statusText}`);
  }
  return resp.json() as Promise<T>;
}

export function obtenerSalud(instancia = INSTANCIA_ID): Promise<Isbd> {
  return obtener<Isbd>(`${BASE}/instancias/${instancia}/salud`);
}

export function obtenerHistorico(desde: Date, hasta: Date, instancia = INSTANCIA_ID): Promise<Isbd[]> {
  const parametros = new URLSearchParams({ desde: desde.toISOString(), hasta: hasta.toISOString() });
  return obtener<Isbd[]>(`${BASE}/instancias/${instancia}/salud/historico?${parametros}`);
}

export function obtenerTablespaces(instancia = INSTANCIA_ID): Promise<Tablespace[]> {
  return obtener<Tablespace[]>(`${BASE}/instancias/${instancia}/tablespaces`);
}

export function obtenerAlertas(instancia = INSTANCIA_ID): Promise<Alerta[]> {
  return obtener<Alerta[]>(`${BASE}/instancias/${instancia}/alertas`);
}

export async function forzarMuestreo(instancia = INSTANCIA_ID): Promise<Isbd> {
  const resp = await fetch(`${BASE}/instancias/${instancia}/muestrear`, { method: 'POST' });
  if (!resp.ok) {
    const problema = await resp.json().catch(() => null as ProblemDetail | null);
    throw new Error(problema?.detail ?? `${resp.status} ${resp.statusText}`);
  }
  return resp.json() as Promise<Isbd>;
}

/** Componente.PROCESOS trae "usuarios" y "fondo"; los demás, solo "actual" (ver ComponentesController). */
export function obtenerComponente(componente: Componente, instancia = INSTANCIA_ID): Promise<Record<string, Muestra>> {
  return obtener<Record<string, Muestra>>(`${BASE}/instancias/${instancia}/componentes/${componente}`);
}

export function obtenerCalibracion(): Promise<Calibracion> {
  return obtener<Calibracion>(`${BASE}/calibracion`);
}

export async function guardarCalibracion(nueva: Calibracion): Promise<Calibracion> {
  const resp = await fetch(`${BASE}/calibracion`, {
    method: 'PUT',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(nueva),
  });
  if (!resp.ok) {
    const problema = await resp.json().catch(() => null as ProblemDetail | null);
    throw new Error(problema?.detail ?? `${resp.status} ${resp.statusText}`);
  }
  return resp.json() as Promise<Calibracion>;
}

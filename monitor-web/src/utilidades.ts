import type { Estado, Nivel } from './api/tipos';

/** Colores por Estado (Estado.java: OPTIMO(90-100) ... CRITICO(0-40)). */
export const COLOR_ESTADO: Record<Estado, string> = {
  OPTIMO: 'var(--optimo)',
  SALUDABLE: 'var(--good)',
  ADVERTENCIA: 'var(--warning)',
  DEGRADADO: 'var(--serious)',
  CRITICO: 'var(--critical)',
};

export const ETIQUETA_ESTADO: Record<Estado, string> = {
  OPTIMO: 'Óptimo',
  SALUDABLE: 'Saludable',
  ADVERTENCIA: 'Advertencia',
  DEGRADADO: 'Degradado',
  CRITICO: 'Crítico',
};

export const COLOR_NIVEL: Record<Nivel, string> = {
  ADVERTENCIA: 'var(--warning)',
  ALTO: 'var(--serious)',
  CRITICO: 'var(--critical)',
};

/** Umbral de riesgo por tablespace (ver ADR de veto absoluto: >=98% fuerza CRITICO). */
export function colorTablespace(usedPercent: number): string {
  if (usedPercent >= 98) return 'var(--critical)';
  if (usedPercent >= 90) return 'var(--serious)';
  if (usedPercent >= 75) return 'var(--warning)';
  return 'var(--good)';
}

/** "hace 3 s" / "hace 12 min" / "hace 4 h" -- para la edad de la muestra y la duración de una alerta. */
export function hace(iso: string, ahora: Date = new Date()): string {
  const segundos = Math.max(0, Math.floor((ahora.getTime() - new Date(iso).getTime()) / 1000));
  if (segundos < 60) return `hace ${segundos} s`;
  const minutos = Math.floor(segundos / 60);
  if (minutos < 60) return `hace ${minutos} min`;
  const horas = Math.floor(minutos / 60);
  if (horas < 24) return `hace ${horas} h`;
  const dias = Math.floor(horas / 24);
  return `hace ${dias} d`;
}

export function formatoNumero(valor: number | null, decimales = 0): string {
  return valor === null ? '—' : valor.toFixed(decimales);
}

export function formatoBytes(bytes: number): string {
  const unidades = ['B', 'KB', 'MB', 'GB', 'TB'];
  let valor = bytes;
  let i = 0;
  while (valor >= 1024 && i < unidades.length - 1) {
    valor /= 1024;
    i++;
  }
  return `${valor.toFixed(valor < 10 && i > 0 ? 1 : 0)} ${unidades[i]}`;
}

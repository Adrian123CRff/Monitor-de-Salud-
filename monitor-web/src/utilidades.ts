import type { Estado, Nivel } from './api/tipos';

/** Compartido entre App/VistaInstancias -- ambas vistas se refrescan solas mientras están montadas. */
export const INTERVALO_REFRESCO_MS = 15_000;

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

/**
 * Gravedad de cada Estado, de sano (0) a crítico (4) -- el mismo orden en que
 * los declara Estado.java. Sirve para ordenar la vista general por riesgo sin
 * depender de la puntuación: una instancia sin datos todavía no tiene
 * puntuación, pero sí tiene que caer en algún lado del orden.
 */
export const GRAVEDAD_ESTADO: Record<Estado, number> = {
  OPTIMO: 0,
  SALUDABLE: 1,
  ADVERTENCIA: 2,
  DEGRADADO: 3,
  CRITICO: 4,
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

/**
 * Los cortes de la escala del §18 (ver Estado.java), de peor a mejor. El
 * gráfico dibuja sus líneas de referencia justo aquí y no en múltiplos
 * redondos: una línea en 80 no significa nada, una en 75 es la frontera entre
 * saludable y advertencia. Así cruzar una línea del gráfico ES cambiar de
 * estado.
 */
export const TRAMOS_ESTADO: { desde: number; hasta: number; estado: Estado }[] = [
  { desde: 0, hasta: 40, estado: 'CRITICO' },
  { desde: 40, hasta: 60, estado: 'DEGRADADO' },
  { desde: 60, hasta: 75, estado: 'ADVERTENCIA' },
  { desde: 75, hasta: 90, estado: 'SALUDABLE' },
  { desde: 90, hasta: 100, estado: 'OPTIMO' },
];

export interface Tendencia {
  direccion: 'MEJORA' | 'ESTABLE' | 'EMPEORA';
  /** Diferencia de puntos entre el final y el principio de la ventana. */
  delta: number;
}

/**
 * Responde la primera pregunta del §23: ¿la salud está empeorando?
 *
 * Compara el promedio del primer tercio contra el del último, no el primer
 * punto contra el último: un solo pico al principio o al final no debe
 * decidir el veredicto de toda la ventana.
 *
 * La banda muerta de 1 punto evita que el ruido normal del muestreo se lea
 * como una tendencia — sin ella, casi ninguna ventana saldría "estable".
 */
export function tendenciaDe(valores: number[], bandaMuerta = 1): Tendencia | null {
  if (valores.length < 6) return null; // muy pocos puntos para hablar de tendencia
  const n = Math.floor(valores.length / 3);
  const promedio = (xs: number[]) => xs.reduce((a, b) => a + b, 0) / xs.length;
  const delta = promedio(valores.slice(-n)) - promedio(valores.slice(0, n));
  if (Math.abs(delta) < bandaMuerta) return { direccion: 'ESTABLE', delta };
  return { direccion: delta > 0 ? 'MEJORA' : 'EMPEORA', delta };
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

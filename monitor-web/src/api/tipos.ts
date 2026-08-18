// Contrato con el backend -- refleja monitor-api/dto tal cual quedaron
// implementados (ver IsbdDto/MuestraDto/TablespaceDto/AlertaDto/CalibracionDto),
// no una suposición previa a la API real.

export type Estado = 'OPTIMO' | 'SALUDABLE' | 'ADVERTENCIA' | 'DEGRADADO' | 'CRITICO';
export type Componente = 'PROCESOS' | 'MEMORIA' | 'ARCHIVOS';
export type Nivel = 'ADVERTENCIA' | 'ALTO' | 'CRITICO';

/** GET/POST .../salud, .../salud/historico, .../muestrear. */
export interface Isbd {
  momento: string; // ISO-8601
  puntuacion: number; // 0-100, salud
  estado: Estado;
  // null cuando ese componente no se pudo recolectar este ciclo (ver Isbd.parcial
  // en el dominio) -- no es un 0, es "no sé", y la interfaz debe distinguirlo.
  ip: number | null;
  im: number | null;
  ia: number | null;
  estadoPorVeto: boolean;
  parcial: boolean;
  // true si "momento" ya es más viejo que ~3 ciclos de muestreo (ver
  // SaludController.MULTIPLICADOR_VETUSTEZ) -- distinto de "parcial", que
  // dice si ESE ciclo tuvo componentes ausentes. Solo lo calcula GET /salud;
  // /salud/historico y POST /muestrear siempre lo mandan en false.
  vetusto: boolean;
  causas: string[];
}

/** GET .../tablespaces. */
export interface Tablespace {
  nombre: string;
  usedPercent: number;
  usedBytes: number;
  maxBytes: number;
}

/** GET .../alertas -- solo abiertas, ya ordenadas por severidad y luego duración. */
export interface Alerta {
  id: number;
  componente: Componente;
  variable: string;
  entidad: string | null; // p. ej. 'USERS' para un tablespace concreto
  nivel: Nivel;
  valor: number;
  umbral: number;
  descripcion: string;
  abiertaEn: string; // ISO-8601
}

/** Forma de error uniforme del backend (ProblemDetail, RFC 9457). */
export interface ProblemDetail {
  title: string;
  status: number;
  detail: string;
  instance?: string;
}

/**
 * GET .../componentes/{c} -- detalle crudo de un componente. Para PROCESOS
 * trae "usuarios" y "fondo" por separado (ver ComponentesController); los
 * demás traen solo "actual".
 */
export interface Muestra {
  componente: Componente;
  momento: string; // ISO-8601
  valores: Record<string, number>;
}

/** GET/PUT .../calibracion. */
export interface Calibracion {
  pesos: Record<string, number>;
  vetoHabilitado: boolean;
  umbralVetoComponente: number;
}

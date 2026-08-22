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
  // El Estado que le corresponde a cada puntuación, resuelto en el backend con
  // la MISMA escala del §18 que usa el ISBD global (Estado.desdePuntuacion).
  // No se recalcula aquí a propósito: replicar los cortes 90/75/60/40 en el
  // cliente dejaría la escala definida en dos sitios. null cuando el
  // componente no se recolectó.
  estadoIp: Estado | null;
  estadoIm: Estado | null;
  estadoIa: Estado | null;
  estadoPorVeto: boolean;
  parcial: boolean;
  // true si "momento" ya es más viejo que ~3 ciclos de muestreo (ver
  // CalculadorVetustez en el backend) -- distinto de "parcial", que dice si
  // ESE ciclo tuvo componentes ausentes. Lo calculan GET /salud y GET
  // /instancias; /salud/historico y POST /muestrear siempre mandan false.
  vetusto: boolean;
  causas: string[];
}

/** GET .../instancias -- la vista general, un tile por base de datos monitoreada. */
export interface ResumenInstancia {
  id: number;
  alias: string;
  // null cuando la instancia todavía no tiene ningún Isbd calculado (recién
  // agregada al catálogo) -- el tile lo muestra como "sin datos", nunca como
  // un semáforo inventado.
  salud: Isbd | null;
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
  /** null mientras el episodio sigue abierto. */
  cerradaEn: string | null;
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
  /** El crudo completo, incluidas las variables de contexto que no puntúan. */
  valores: Record<string, number>;
  /** null cuando la muestra no se pudo puntuar (ver ConsultarComponente). */
  puntuacion: number | null;
  /** El Estado de esa puntuación, resuelto en el backend con la escala del §18. */
  estado: Estado | null;
  vetado: boolean | null;
  /** Solo las variables que SÍ puntúan, ya ordenadas por lo que le cuestan al componente. */
  variables: VariableEvaluada[];
}

/**
 * El desglose que responde "cuál variable está fuera de límites", que es como
 * el profesor describe el drill-down: entrar a un componente en rojo y ver la
 * variable específica, no solo el número del componente.
 */
export interface VariableEvaluada {
  variable: string;
  /** Crudo. null si la muestra no lo trae (p. ej. una delta sin historial). */
  valor: number | null;
  /** 0-100, convención de salud. */
  puntuacion: number;
  /** Resuelto en el backend, no recalculado aquí: la escala vive en un solo lugar. */
  estado: Estado;
  pesoEnComponente: number;
  /** (100 - puntuacion) * peso: cuántos puntos del componente se lleva esta variable. */
  aportePerdido: number;
  disparoVeto: boolean;
  /** Cómo se normaliza. Alimenta la ayuda contextual, no el render del número. */
  tipoUmbral: string;
  /**
   * Los límites vigentes HOY, para que la ayuda diga dónde está el corte sin
   * escribirlo a mano. null en los tipos que no tienen banda (una penalización
   * por evento no tiene un "sano hasta X").
   */
  valorOk: number | null;
  valorCritico: number | null;
}

/** GET/PUT .../calibracion. */
export interface Calibracion {
  pesos: Record<string, number>;
  vetoHabilitado: boolean;
  umbralVetoComponente: number;
}

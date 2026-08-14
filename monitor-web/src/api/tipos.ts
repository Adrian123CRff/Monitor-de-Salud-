// Contrato con el backend. Refleja los DTO de monitor-api/dto (aún por escribir);
// se actualiza a la par cuando la API deje de ser un esqueleto.

export type Estado = 'OPTIMO' | 'SALUDABLE' | 'ADVERTENCIA' | 'DEGRADADO' | 'CRITICO';
export type Componente = 'PROCESOS' | 'MEMORIA' | 'ARCHIVOS';
export type Nivel = 'ADVERTENCIA' | 'ALTO' | 'CRITICO';

export interface Indicador {
  componente: Componente;
  puntuacion: number; // 0-100, salud
  puntuacionesPorVariable: Record<string, number>;
}

export interface Salud {
  instanciaId: number;
  momento: string; // ISO-8601
  isbd: number;
  estado: Estado;
  // ADR 0003: cuando el estado viene del veto, la interfaz debe mostrar
  // estadoPorVeto + causas junto al número, nunca solo en un tooltip.
  estadoPorVeto: boolean;
  causas: string[];
  ip: Indicador;
  im: Indicador;
  ia: Indicador;
}

export interface Alerta {
  id: number;
  abiertaEn: string;
  cerradaEn: string | null;
  componente: Componente;
  variable: string;
  entidad: string | null; // p. ej. 'USERS' para un tablespace concreto
  valor: number;
  umbral: number;
  nivel: Nivel;
  descripcion: string;
}

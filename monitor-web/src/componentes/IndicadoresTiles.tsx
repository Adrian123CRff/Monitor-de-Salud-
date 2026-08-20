import type { Componente, Estado, Isbd } from '../api/tipos';
import { COLOR_ESTADO, ETIQUETA_ESTADO, formatoNumero } from '../utilidades';
import { Sparkline } from './Sparkline';

interface Fila {
  etiqueta: string;
  componente: Componente;
  valor: number | null;
  estado: Estado | null;
  serie: number[];
}

interface Props {
  actual: Isbd;
  historico: Isbd[];
  onSeleccionar?: (componente: Componente) => void;
}

/**
 * IP/IM/IA -- los tres usan la convención de salud (100 = sano); las
 * utilizaciones crudas ya vienen invertidas desde el backend
 * (CalculadorComponente), esta vista no hace ningún cómputo.
 *
 * El color de cada tile es SEMÁNTICO: sale del Estado que el backend
 * resolvió para esa puntuación (verde óptimo ... rojo crítico), no de una
 * paleta de series fija. Antes cada componente tenía un color decorativo
 * propio (naranja/verde/amarillo) que no cambiaba nunca -- un IP en 20 se
 * veía igual de naranja que un IP en 100, así que el color no informaba
 * nada y peor: sugería un riesgo que no existía. Un componente sin datos se
 * queda en gris, nunca en un color de la escala.
 */
export function IndicadoresTiles({ actual, historico, onSeleccionar }: Props) {
  const filas: Fila[] = [
    { etiqueta: 'Procesos · IP', componente: 'PROCESOS', valor: actual.ip, estado: actual.estadoIp, serie: serieDe(historico, 'ip') },
    { etiqueta: 'Memoria · IM', componente: 'MEMORIA', valor: actual.im, estado: actual.estadoIm, serie: serieDe(historico, 'im') },
    { etiqueta: 'Archivos · IA', componente: 'ARCHIVOS', valor: actual.ia, estado: actual.estadoIa, serie: serieDe(historico, 'ia') },
  ];

  return (
    <section className="card c7">
      <div className="grid" style={{ gap: 14 }}>
        {filas.map((f) => {
          const color = f.estado ? COLOR_ESTADO[f.estado] : 'var(--muted)';
          return (
          <div
            className={`c4 tile${onSeleccionar ? ' tile-clicable' : ''}`}
            key={f.etiqueta}
            onClick={onSeleccionar ? () => onSeleccionar(f.componente) : undefined}
            role={onSeleccionar ? 'button' : undefined}
            tabIndex={onSeleccionar ? 0 : undefined}
          >
            <div className="lab">
              <span className="swatch" style={{ background: color }} />
              {f.etiqueta}
            </div>
            <div className="row">
              <div className="val tnum" style={{ color }}>
                {formatoNumero(f.valor)}
              </div>
              {f.serie.length >= 2 ? (
                <Sparkline valores={f.serie} color={color} />
              ) : (
                <span className="muted" style={{ fontSize: 12 }}>
                  sin historial
                </span>
              )}
            </div>
            {f.estado && (
              <span className="chip" style={{ color, borderColor: color, marginTop: 8 }}>
                {ETIQUETA_ESTADO[f.estado]}
              </span>
            )}
          </div>
          );
        })}
      </div>
      <div className="note">
        Los tres indicadores usan la convención de salud: <b>100 = sano</b>. Cuando un valor falta (—) es porque ese
        componente no se pudo recolectar ese ciclo, no un cero. {onSeleccionar && 'Clic en un tile para ver el detalle crudo.'}
      </div>
    </section>
  );
}

function serieDe(historico: Isbd[], campo: 'ip' | 'im' | 'ia'): number[] {
  return historico.map((h) => h[campo]).filter((v): v is number => v !== null);
}

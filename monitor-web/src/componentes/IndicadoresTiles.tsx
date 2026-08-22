import type { Componente, Estado, Isbd } from '../api/tipos';
import { COLOR_ESTADO, ETIQUETA_ESTADO, formatoNumero } from '../utilidades';
import { FichaConcepto } from './Fichas';

interface Fila {
  etiqueta: string;
  /** Clave de su ficha en el catálogo de conceptos. */
  ayuda: 'ip' | 'im' | 'ia';
  componente: Componente;
  valor: number | null;
  estado: Estado | null;
}

interface Props {
  actual: Isbd;
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
 *
 * Estos tiles muestran SOLO el valor actual. Antes cada uno llevaba además
 * un sparkline alimentado por el mismo historico que dibuja HistoricoChart
 * un poco más abajo en la misma pantalla -- la misma serie de datos
 * representada dos veces, compitiendo por la atención sin agregar nada. El
 * eje del tiempo vive en un solo lugar: el gráfico de evolución.
 */
export function IndicadoresTiles({ actual, onSeleccionar }: Props) {
  const filas: Fila[] = [
    { etiqueta: 'Procesos · IP', ayuda: 'ip', componente: 'PROCESOS', valor: actual.ip, estado: actual.estadoIp },
    { etiqueta: 'Memoria · IM', ayuda: 'im', componente: 'MEMORIA', valor: actual.im, estado: actual.estadoIm },
    { etiqueta: 'Archivos · IA', ayuda: 'ia', componente: 'ARCHIVOS', valor: actual.ia, estado: actual.estadoIa },
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
              {/* El clic en la ficha no debe abrir el drill-down del tile. */}
              <span onClick={(e) => e.stopPropagation()}>
                <FichaConcepto clave={f.ayuda} />
              </span>
            </div>
            <div className="row">
              <div className="val tnum" style={{ color }}>
                {formatoNumero(f.valor)}
              </div>
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
        <b>100 = sano</b> en los tres. {onSeleccionar && 'Clic en un tile para ver qué lo está puntuando.'}
      </div>
    </section>
  );
}

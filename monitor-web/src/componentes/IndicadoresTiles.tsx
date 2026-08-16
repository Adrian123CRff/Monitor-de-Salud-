import type { Componente, Isbd } from '../api/tipos';
import { formatoNumero } from '../utilidades';
import { Sparkline } from './Sparkline';

interface Fila {
  etiqueta: string;
  componente: Componente;
  color: string;
  valor: number | null;
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
 */
export function IndicadoresTiles({ actual, historico, onSeleccionar }: Props) {
  const filas: Fila[] = [
    { etiqueta: 'Procesos · IP', componente: 'PROCESOS', color: 'var(--s2)', valor: actual.ip, serie: serieDe(historico, 'ip') },
    { etiqueta: 'Memoria · IM', componente: 'MEMORIA', color: 'var(--s3)', valor: actual.im, serie: serieDe(historico, 'im') },
    { etiqueta: 'Archivos · IA', componente: 'ARCHIVOS', color: 'var(--s4)', valor: actual.ia, serie: serieDe(historico, 'ia') },
  ];

  return (
    <section className="card c7">
      <div className="grid" style={{ gap: 14 }}>
        {filas.map((f) => (
          <div
            className={`c4 tile${onSeleccionar ? ' tile-clicable' : ''}`}
            key={f.etiqueta}
            onClick={onSeleccionar ? () => onSeleccionar(f.componente) : undefined}
            role={onSeleccionar ? 'button' : undefined}
            tabIndex={onSeleccionar ? 0 : undefined}
          >
            <div className="lab">
              <span className="swatch" style={{ background: f.color }} />
              {f.etiqueta}
            </div>
            <div className="row">
              <div className="val tnum">{formatoNumero(f.valor)}</div>
              {f.serie.length >= 2 ? (
                <Sparkline valores={f.serie} color={f.color} />
              ) : (
                <span className="muted" style={{ fontSize: 12 }}>
                  sin historial
                </span>
              )}
            </div>
          </div>
        ))}
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

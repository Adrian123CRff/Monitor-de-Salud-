import type { Isbd } from '../api/tipos';
import { formatoNumero } from '../utilidades';
import { Sparkline } from './Sparkline';

interface Fila {
  etiqueta: string;
  color: string;
  valor: number | null;
  serie: number[];
}

/**
 * IP/IM/IA -- los tres usan la convención de salud (100 = sano); las
 * utilizaciones crudas ya vienen invertidas desde el backend
 * (CalculadorComponente), esta vista no hace ningún cómputo.
 */
export function IndicadoresTiles({ actual, historico }: { actual: Isbd; historico: Isbd[] }) {
  const filas: Fila[] = [
    { etiqueta: 'Procesos · IP', color: 'var(--s2)', valor: actual.ip, serie: serieDe(historico, 'ip') },
    { etiqueta: 'Memoria · IM', color: 'var(--s3)', valor: actual.im, serie: serieDe(historico, 'im') },
    { etiqueta: 'Archivos · IA', color: 'var(--s4)', valor: actual.ia, serie: serieDe(historico, 'ia') },
  ];

  return (
    <section className="card c7">
      <div className="grid" style={{ gap: 14 }}>
        {filas.map((f) => (
          <div className="c4 tile" key={f.etiqueta}>
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
        componente no se pudo recolectar ese ciclo, no un cero.
      </div>
    </section>
  );
}

function serieDe(historico: Isbd[], campo: 'ip' | 'im' | 'ia'): number[] {
  return historico.map((h) => h[campo]).filter((v): v is number => v !== null);
}

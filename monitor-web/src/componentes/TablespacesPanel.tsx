import type { Tablespace } from '../api/tipos';
import { colorTablespace, formatoBytes } from '../utilidades';

export function TablespacesPanel({ tablespaces }: { tablespaces: Tablespace[] }) {
  const ordenados = [...tablespaces].sort((a, b) => b.usedPercent - a.usedPercent);

  return (
    <section className="card c6">
      <div style={{ display: 'flex', alignItems: 'baseline', gap: 12, flexWrap: 'wrap' }}>
        <h2 style={{ fontSize: 15 }}>Tablespaces</h2>
        <span className="muted" style={{ fontSize: 12 }}>
          ordenados por % usado, peor primero
        </span>
      </div>
      <div className="legend" style={{ margin: '9px 0 2px' }}>
        <span>
          <span className="swatch" style={{ background: 'var(--good)' }} />
          &lt;75 %
        </span>
        <span>
          <span className="swatch" style={{ background: 'var(--warning)' }} />
          75–90 %
        </span>
        <span>
          <span className="swatch" style={{ background: 'var(--serious)' }} />
          90–98 %
        </span>
        <span>
          <span className="swatch" style={{ background: 'var(--critical)' }} />
          &ge;98 % (veto)
        </span>
      </div>

      {ordenados.length === 0 ? (
        <div className="empty">Sin datos de tablespaces todavía.</div>
      ) : (
        <div style={{ marginTop: 6 }}>
          {ordenados.map((ts) => (
            <div className="ts-row" key={ts.nombre}>
              <span className="tnum">{ts.nombre}</span>
              <div className="ts-bar">
                <div
                  style={{
                    width: `${Math.min(100, ts.usedPercent)}%`,
                    background: colorTablespace(ts.usedPercent),
                  }}
                />
              </div>
              <span className="tnum" style={{ textAlign: 'right' }}>
                {ts.usedPercent.toFixed(1)} %
              </span>
            </div>
          ))}
        </div>
      )}
      <div className="note">
        El ISBD usa el <b>peor</b> tablespace, nunca el promedio: doce al 40 % y uno al 99 % promedian 45 % y suenan
        tranquilos. Un tablespace ≥98 % veta todo el componente ARCHIVOS, sin importar el resto.
      </div>
      {ordenados.length > 0 && (
        <div className="note" style={{ marginTop: 4 }}>
          Total ocupado: {formatoBytes(ordenados.reduce((s, t) => s + t.usedBytes, 0))}
        </div>
      )}
    </section>
  );
}

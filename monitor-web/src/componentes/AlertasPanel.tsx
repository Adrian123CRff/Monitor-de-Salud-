import type { Alerta } from '../api/tipos';
import { COLOR_NIVEL, hace } from '../utilidades';

interface Props {
  alertas: Alerta[];
  error?: string | null;
}

/** Solo alertas abiertas (ver GET /alertas) -- ya ordenadas por severidad y duración por el backend. */
export function AlertasPanel({ alertas, error }: Props) {
  return (
    <section className="card c12">
      <div style={{ display: 'flex', alignItems: 'baseline', gap: 12 }}>
        <h2 style={{ fontSize: 15 }}>Alertas abiertas</h2>
        <span className="muted" style={{ fontSize: 12 }}>
          {error ? 'error' : alertas.length === 0 ? 'ninguna' : `${alertas.length}`}
        </span>
      </div>
      <div style={{ marginTop: 6 }}>
        {error ? (
          <div className="panel-error">No se pudieron cargar las alertas: {error}</div>
        ) : alertas.length === 0 ? (
          <div className="empty">Sin alertas abiertas -- todo dentro de los umbrales.</div>
        ) : (
          alertas.map((a) => (
            <div className="alert" key={a.id}>
              <span className="dotnivel" style={{ background: COLOR_NIVEL[a.nivel] }} />
              <div style={{ flex: 1 }}>
                <div className="hd">
                  <span className="nv" style={{ color: COLOR_NIVEL[a.nivel] }}>
                    {a.nivel}
                  </span>
                  <span className="ink2">
                    {a.componente} · {a.variable}
                    {a.entidad ? ` · ${a.entidad}` : ''}
                  </span>
                  <span className="du">{hace(a.abiertaEn)}</span>
                </div>
                <div className="ds">{a.descripcion}</div>
              </div>
            </div>
          ))
        )}
      </div>
      <div className="note">
        Cada alerta es un <b>episodio</b> con apertura y cierre, no un evento por muestra. Una condición sostenida 6
        h es una fila, no cientos.
      </div>
    </section>
  );
}

import type { Componente, Muestra } from '../api/tipos';
import { hace } from '../utilidades';

interface Props {
  componente: Componente;
  detalle: Record<string, Muestra> | null;
  cargando: boolean;
  error: string | null;
  onCerrar: () => void;
}

const ETIQUETA_VISTA: Record<string, string> = {
  usuarios: 'Usuarios (V$SESSION)',
  fondo: 'Procesos de fondo (DBW0/LGWR/CKPT/PMON/SMON)',
  actual: 'Actual',
};

/** GET .../componentes/{c} -- el crudo detrás del tile de IP/IM/IA (ver IndicadoresTiles). */
export function ComponenteDetalle({ componente, detalle, cargando, error, onCerrar }: Props) {
  return (
    <section className="card c12">
      <div style={{ display: 'flex', alignItems: 'baseline', gap: 12 }}>
        <h2 style={{ fontSize: 15 }}>Detalle — {componente}</h2>
        <button onClick={onCerrar} style={{ marginLeft: 'auto' }}>
          Cerrar
        </button>
      </div>

      {cargando && <div className="empty">Cargando detalle…</div>}
      {error && !cargando && <p className="muted">No se pudo cargar el detalle: {error}</p>}

      {!cargando && !error && detalle && Object.keys(detalle).length === 0 && (
        <div className="empty">Sin datos todavía para este componente.</div>
      )}

      {!cargando && !error && detalle && Object.keys(detalle).length > 0 && (
        <div className="detalle-grid">
          {Object.entries(detalle).map(([vista, muestra]) => (
            <div className="detalle-vista" key={vista}>
              <div className="lab">{ETIQUETA_VISTA[vista] ?? vista}</div>
              <div className="muted" style={{ fontSize: 12, marginBottom: 6 }}>
                {hace(muestra.momento)}
              </div>
              <table className="detalle-tabla">
                <tbody>
                  {Object.entries(muestra.valores)
                    .sort(([a], [b]) => a.localeCompare(b))
                    .map(([variable, valor]) => (
                      <tr key={variable}>
                        <td className="muted">{variable}</td>
                        <td className="tnum detalle-valor">{valor}</td>
                      </tr>
                    ))}
                </tbody>
              </table>
            </div>
          ))}
        </div>
      )}
    </section>
  );
}

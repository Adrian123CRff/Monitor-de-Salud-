import type { Componente, Muestra, VariableEvaluada } from '../api/tipos';
import { COLOR_ESTADO, ETIQUETA_ESTADO, formatoNumero, hace } from '../utilidades';

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

/**
 * GET .../componentes/{c} -- el detalle detrás del tile de IP/IM/IA.
 *
 * Abre por el DESGLOSE (qué variable está tirando el indicador hacia abajo) y
 * deja el crudo completo debajo. Es el flujo que el profesor describe como el
 * centro del producto: entrar a un componente en rojo y ver cuál variable
 * específica está fuera de los límites normales. Antes esta pantalla solo
 * mostraba el crudo ordenado alfabéticamente -- para saber por qué ARCHIVOS
 * marcaba 90 había que conocer los umbrales de memoria.
 */
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
              <div className="muted" style={{ fontSize: 12, marginBottom: 10 }}>
                {hace(muestra.momento)}
                {muestra.puntuacion !== null && muestra.estado !== null && (
                  <>
                    {' · '}
                    <span style={{ color: COLOR_ESTADO[muestra.estado] }}>
                      {formatoNumero(muestra.puntuacion)} / 100 · {ETIQUETA_ESTADO[muestra.estado]}
                    </span>
                  </>
                )}
              </div>

              {muestra.variables.length > 0 && (
                <>
                  <div className="sublab">Qué está puntuando</div>
                  <table className="detalle-tabla desglose">
                    <thead>
                      <tr>
                        <th>Variable</th>
                        <th className="tnum">Valor</th>
                        <th className="tnum">Puntúa</th>
                        <th className="tnum">Le cuesta</th>
                      </tr>
                    </thead>
                    <tbody>
                      {muestra.variables.map((v) => (
                        <FilaVariable key={v.variable} v={v} />
                      ))}
                    </tbody>
                  </table>
                </>
              )}

              {muestra.puntuacion === null && (
                <p className="muted" style={{ fontSize: 12 }}>
                  Esta muestra no se pudo puntuar todavía — se muestra el dato crudo.
                </p>
              )}

              <div className="sublab">Dato crudo de Oracle</div>
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

/**
 * Una variable del desglose. El color sale de su propia puntuación con la
 * misma escala del resto del sistema, así que la que está fuera de límites se
 * ve roja sin tener que leer el número.
 */
function FilaVariable({ v }: { v: VariableEvaluada }) {
  const color = COLOR_ESTADO[v.estado];
  return (
    <tr>
      <td>
        {v.variable}
        {v.disparoVeto && (
          <span className="chip veto" title="Esta variable veta el componente entero">
            veto
          </span>
        )}
      </td>
      <td className="tnum detalle-valor">{v.valor === null ? '—' : v.valor}</td>
      <td className="tnum" style={{ color }}>
        {formatoNumero(v.puntuacion)}
      </td>
      {/* Cero puntos perdidos no merece tinta: lo que importa es lo que resta. */}
      <td className="tnum" style={{ color: v.aportePerdido > 0 ? color : 'var(--muted)' }}>
        {v.aportePerdido > 0 ? `−${formatoNumero(v.aportePerdido, 1)}` : '—'}
      </td>
    </tr>
  );
}

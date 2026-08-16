import { useCallback, useEffect, useState } from 'react';
import {
  forzarMuestreo,
  obtenerAlertas,
  obtenerHistorico,
  obtenerSalud,
  obtenerTablespaces,
  SinDatosAunError,
} from './api/cliente';
import type { Alerta, Isbd, Tablespace } from './api/tipos';
import { AlertasPanel } from './componentes/AlertasPanel';
import { HistoricoChart } from './componentes/HistoricoChart';
import { IndicadoresTiles } from './componentes/IndicadoresTiles';
import { IsbdHero } from './componentes/IsbdHero';
import { TablespacesPanel } from './componentes/TablespacesPanel';
import { hace } from './utilidades';

const INTERVALO_REFRESCO_MS = 15_000;
const VENTANA_HISTORICO_HORAS = 24;

interface Estado {
  isbd: Isbd | null;
  historico: Isbd[];
  tablespaces: Tablespace[];
  alertas: Alerta[];
  sinDatos: boolean;
  error: string | null;
  cargando: boolean;
}

export function App() {
  const [estado, setEstado] = useState<Estado>({
    isbd: null,
    historico: [],
    tablespaces: [],
    alertas: [],
    sinDatos: false,
    error: null,
    cargando: true,
  });
  const [muestreando, setMuestreando] = useState(false);

  const refrescar = useCallback(async () => {
    const hasta = new Date();
    const desde = new Date(hasta.getTime() - VENTANA_HISTORICO_HORAS * 60 * 60 * 1000);

    try {
      const isbd = await obtenerSalud();
      // El histórico y las otras vistas son complementarias -- si una falla,
      // no debe tumbar el hero del ISBD, que es lo primero que importa mostrar.
      const [historico, tablespaces, alertas] = await Promise.all([
        obtenerHistorico(desde, hasta).catch(() => []),
        obtenerTablespaces().catch(() => []),
        obtenerAlertas().catch(() => []),
      ]);
      setEstado({ isbd, historico, tablespaces, alertas, sinDatos: false, error: null, cargando: false });
    } catch (e) {
      if (e instanceof SinDatosAunError) {
        setEstado((s) => ({ ...s, sinDatos: true, error: null, cargando: false }));
      } else {
        setEstado((s) => ({ ...s, error: e instanceof Error ? e.message : String(e), cargando: false }));
      }
    }
  }, []);

  useEffect(() => {
    refrescar();
    const id = setInterval(refrescar, INTERVALO_REFRESCO_MS);
    return () => clearInterval(id);
  }, [refrescar]);

  async function manejarForzarMuestreo() {
    setMuestreando(true);
    try {
      await forzarMuestreo();
      await refrescar();
    } catch (e) {
      setEstado((s) => ({ ...s, error: e instanceof Error ? e.message : String(e) }));
    } finally {
      setMuestreando(false);
    }
  }

  return (
    <>
      <header>
        <h1>Monitor de Salud de Oracle</h1>
        <span className="badge">
          <span className={`dot pulse${estado.error ? ' critico' : ''}`} />
          {estado.error ? 'sin conexión' : 'conectado'}
        </span>
        {estado.isbd && <span className="badge">muestra: {hace(estado.isbd.momento)}</span>}
        <span className="spacer" />
        <button onClick={manejarForzarMuestreo} disabled={muestreando}>
          {muestreando ? 'Muestreando…' : 'Forzar muestreo'}
        </button>
      </header>

      {estado.cargando && <div className="empty">Cargando…</div>}

      {!estado.cargando && estado.sinDatos && (
        <div className="card error-panel">
          <p>Todavía no hay ningún ISBD calculado para esta instancia.</p>
          <p className="muted">
            El planificador corre automáticamente, o podés forzar un muestreo con el botón de arriba.
          </p>
          <button onClick={manejarForzarMuestreo} disabled={muestreando}>
            {muestreando ? 'Muestreando…' : 'Forzar muestreo ahora'}
          </button>
        </div>
      )}

      {!estado.cargando && estado.error && !estado.sinDatos && (
        <div className="card error-panel">
          <p>No se pudo conectar con el backend.</p>
          <p className="muted">{estado.error}</p>
        </div>
      )}

      {estado.isbd && (
        <div className="grid">
          <IsbdHero isbd={estado.isbd} />
          <IndicadoresTiles actual={estado.isbd} historico={estado.historico} />

          <section className="card c12">
            <div style={{ display: 'flex', alignItems: 'baseline', gap: 14, flexWrap: 'wrap', marginBottom: 6 }}>
              <h2 style={{ fontSize: 15 }}>Evolución — últimas {VENTANA_HISTORICO_HORAS} h</h2>
              <div className="legend" style={{ marginLeft: 'auto' }}>
                <span>
                  <span className="swatch" style={{ background: 'var(--s1)' }} />
                  ISBD
                </span>
                <span>
                  <span className="swatch" style={{ background: 'var(--s2)' }} />
                  Procesos
                </span>
                <span>
                  <span className="swatch" style={{ background: 'var(--s3)' }} />
                  Memoria
                </span>
                <span>
                  <span className="swatch" style={{ background: 'var(--s4)' }} />
                  Archivos
                </span>
              </div>
            </div>
            <HistoricoChart historico={estado.historico} />
          </section>

          <TablespacesPanel tablespaces={estado.tablespaces} />
          <AlertasPanel alertas={estado.alertas} />
        </div>
      )}
    </>
  );
}

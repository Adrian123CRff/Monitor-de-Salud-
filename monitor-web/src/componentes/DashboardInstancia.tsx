import { useCallback, useEffect, useState } from 'react';
import {
  forzarMuestreo,
  obtenerAlertas,
  obtenerComponente,
  obtenerHistorico,
  obtenerSalud,
  obtenerTablespaces,
  SinDatosAunError,
} from '../api/cliente';
import type { Alerta, Componente, Isbd, Muestra, Tablespace } from '../api/tipos';
import { INTERVALO_REFRESCO_MS, hace } from '../utilidades';
import { AlertasPanel } from './AlertasPanel';
import { CalibracionPanel } from './CalibracionPanel';
import { ComponenteDetalle } from './ComponenteDetalle';
import { HistoricoChart } from './HistoricoChart';
import { IndicadoresTiles } from './IndicadoresTiles';
import { IsbdHero } from './IsbdHero';
import { TablespacesPanel } from './TablespacesPanel';

const VENTANA_HISTORICO_HORAS = 24;

interface Props {
  instanciaId: number;
  alias: string;
  onVolver: () => void;
}

interface Estado {
  isbd: Isbd | null;
  historico: Isbd[];
  tablespaces: Tablespace[];
  alertas: Alerta[];
  sinDatos: boolean;
  error: string | null;
  cargando: boolean;
  errorHistorico: string | null;
  errorTablespaces: string | null;
  errorAlertas: string | null;
}

/**
 * El histórico y las otras vistas son complementarias -- si una falla, no
 * debe tumbar el hero del ISBD, que es lo primero que importa mostrar. Pero
 * tragarse el error en un array vacío (como antes) es indistinguible de
 * "de verdad no hay alertas": cada panel necesita saber si está vacío
 * porque no hay datos o porque su fetch falló, para mostrar cosas distintas.
 */
async function cargarSeguro<T>(promesa: Promise<T>, vacio: T): Promise<[T, string | null]> {
  try {
    return [await promesa, null];
  } catch (e) {
    return [vacio, e instanceof Error ? e.message : String(e)];
  }
}

/** El dashboard de detalle de UNA instancia -- ver App para la vista general que la selecciona. */
export function DashboardInstancia({ instanciaId, alias, onVolver }: Props) {
  const [estado, setEstado] = useState<Estado>({
    isbd: null,
    historico: [],
    tablespaces: [],
    alertas: [],
    sinDatos: false,
    error: null,
    cargando: true,
    errorHistorico: null,
    errorTablespaces: null,
    errorAlertas: null,
  });
  const [muestreando, setMuestreando] = useState(false);
  const [calibracionAbierta, setCalibracionAbierta] = useState(false);
  const [componenteSeleccionado, setComponenteSeleccionado] = useState<Componente | null>(null);
  const [detalleComponente, setDetalleComponente] = useState<Record<string, Muestra> | null>(null);
  const [detalleCargando, setDetalleCargando] = useState(false);
  const [detalleError, setDetalleError] = useState<string | null>(null);

  const refrescar = useCallback(async () => {
    const hasta = new Date();
    const desde = new Date(hasta.getTime() - VENTANA_HISTORICO_HORAS * 60 * 60 * 1000);

    try {
      const isbd = await obtenerSalud(instanciaId);
      const [
        [historico, errorHistorico],
        [tablespaces, errorTablespaces],
        [alertas, errorAlertas],
      ] = await Promise.all([
        cargarSeguro(obtenerHistorico(desde, hasta, instanciaId), [] as Isbd[]),
        cargarSeguro(obtenerTablespaces(instanciaId), [] as Tablespace[]),
        cargarSeguro(obtenerAlertas(instanciaId), [] as Alerta[]),
      ]);
      setEstado({
        isbd, historico, tablespaces, alertas, sinDatos: false, error: null, cargando: false,
        errorHistorico, errorTablespaces, errorAlertas,
      });
    } catch (e) {
      if (e instanceof SinDatosAunError) {
        setEstado((s) => ({ ...s, sinDatos: true, error: null, cargando: false }));
      } else {
        setEstado((s) => ({ ...s, error: e instanceof Error ? e.message : String(e), cargando: false }));
      }
    }
  }, [instanciaId]);

  useEffect(() => {
    // instanciaId cambia solo si el usuario vuelve a la vista general y entra
    // a otra instancia -- App desmonta/remonta este componente en ese caso
    // (key={instanciaId}), así que en la práctica este efecto corre una vez
    // por instancia seleccionada, igual que antes corría una vez por sesión.
    refrescar();
    const id = setInterval(refrescar, INTERVALO_REFRESCO_MS);
    return () => clearInterval(id);
  }, [refrescar]);

  useEffect(() => {
    if (!componenteSeleccionado) {
      return;
    }
    setDetalleCargando(true);
    setDetalleError(null);
    obtenerComponente(componenteSeleccionado, instanciaId)
      .then(setDetalleComponente)
      .catch((e) => setDetalleError(e instanceof Error ? e.message : String(e)))
      .finally(() => setDetalleCargando(false));
  }, [componenteSeleccionado, instanciaId]);

  function seleccionarComponente(componente: Componente) {
    setComponenteSeleccionado((actual) => (actual === componente ? null : componente));
    setDetalleComponente(null);
  }

  async function manejarForzarMuestreo() {
    setMuestreando(true);
    try {
      await forzarMuestreo(instanciaId);
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
        <button onClick={onVolver}>&larr; Volver</button>
        <h1>{alias}</h1>
        <span className="badge">
          <span className={`dot pulse${estado.error ? ' critico' : ''}`} />
          {estado.error ? 'sin conexión' : 'conectado'}
        </span>
        {estado.isbd && <span className="badge">muestra: {hace(estado.isbd.momento)}</span>}
        <span className="spacer" />
        <button onClick={() => setCalibracionAbierta((a) => !a)}>
          {calibracionAbierta ? 'Ocultar calibración' : 'Calibración'}
        </button>
        <button onClick={manejarForzarMuestreo} disabled={muestreando}>
          {muestreando ? 'Muestreando…' : 'Forzar muestreo'}
        </button>
      </header>

      {calibracionAbierta && (
        <div className="grid" style={{ marginBottom: 16 }}>
          <CalibracionPanel onCerrar={() => setCalibracionAbierta(false)} />
        </div>
      )}

      {estado.cargando && (
        <div className="grid" aria-label="Cargando">
          <div className="card c5 skeleton" style={{ height: 150 }} />
          <div className="card c7 skeleton" style={{ height: 150 }} />
          <div className="card c12 skeleton" style={{ height: 220 }} />
          <div className="card c6 skeleton" style={{ height: 190 }} />
          <div className="card c12 skeleton" style={{ height: 150 }} />
        </div>
      )}

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
          <IndicadoresTiles actual={estado.isbd} onSeleccionar={seleccionarComponente} />

          {componenteSeleccionado && (
            <ComponenteDetalle
              componente={componenteSeleccionado}
              detalle={detalleComponente}
              cargando={detalleCargando}
              error={detalleError}
              onCerrar={() => setComponenteSeleccionado(null)}
            />
          )}

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
            {estado.errorHistorico ? (
              <div className="panel-error">No se pudo cargar el histórico: {estado.errorHistorico}</div>
            ) : (
              <HistoricoChart historico={estado.historico} />
            )}
          </section>

          <TablespacesPanel tablespaces={estado.tablespaces} error={estado.errorTablespaces} />
          <AlertasPanel alertas={estado.alertas} error={estado.errorAlertas} />
        </div>
      )}
    </>
  );
}

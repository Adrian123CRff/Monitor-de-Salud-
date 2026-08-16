import { useEffect, useState } from 'react';
import { guardarCalibracion, obtenerCalibracion } from '../api/cliente';
import type { Calibracion } from '../api/tipos';

const COMPONENTES = ['PROCESOS', 'MEMORIA', 'ARCHIVOS'] as const;

/** GET/PUT .../calibracion (ADR 0003) -- antes solo se podía tocar con curl. */
export function CalibracionPanel({ onCerrar }: { onCerrar: () => void }) {
  const [calibracion, setCalibracion] = useState<Calibracion | null>(null);
  const [cargando, setCargando] = useState(true);
  const [guardando, setGuardando] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [guardado, setGuardado] = useState(false);

  useEffect(() => {
    obtenerCalibracion()
      .then(setCalibracion)
      .catch((e) => setError(e instanceof Error ? e.message : String(e)))
      .finally(() => setCargando(false));
  }, []);

  function actualizarPeso(componente: string, valor: string) {
    const num = Number(valor);
    setCalibracion((c) => (c ? { ...c, pesos: { ...c.pesos, [componente]: Number.isFinite(num) ? num : 0 } } : c));
    setGuardado(false);
  }

  async function guardar() {
    if (!calibracion) return;
    setGuardando(true);
    setError(null);
    try {
      setCalibracion(await guardarCalibracion(calibracion));
      setGuardado(true);
    } catch (e) {
      setError(e instanceof Error ? e.message : String(e));
      setGuardado(false);
    } finally {
      setGuardando(false);
    }
  }

  const suma = calibracion ? COMPONENTES.reduce((s, c) => s + (calibracion.pesos[c] ?? 0), 0) : 0;
  const sumaValida = Math.abs(suma - 1) < 0.001;

  return (
    <section className="card c12">
      <div style={{ display: 'flex', alignItems: 'baseline', gap: 12, flexWrap: 'wrap' }}>
        <h2 style={{ fontSize: 15 }}>Calibración</h2>
        <span className="muted" style={{ fontSize: 12 }}>
          pesos de procesos/memoria/archivos y umbral de veto (ADR 0003) -- valores de diseño, no calibrados con
          datos reales todavía
        </span>
        <button onClick={onCerrar} style={{ marginLeft: 'auto' }}>
          Cerrar
        </button>
      </div>

      {cargando && <div className="empty">Cargando calibración…</div>}
      {error && !calibracion && !cargando && <p className="muted">No se pudo cargar: {error}</p>}

      {calibracion && (
        <>
          <div className="calibracion-grid">
            {COMPONENTES.map((c) => (
              <label className="calibracion-campo" key={c}>
                <span>{c}</span>
                <input
                  type="number"
                  step="0.01"
                  min="0"
                  max="1"
                  value={calibracion.pesos[c] ?? 0}
                  onChange={(e) => actualizarPeso(c, e.target.value)}
                />
              </label>
            ))}
            <label className="calibracion-campo">
              <span>Umbral de veto (0-100)</span>
              <input
                type="number"
                step="1"
                min="0"
                max="100"
                value={calibracion.umbralVetoComponente}
                onChange={(e) => {
                  const num = Number(e.target.value);
                  setCalibracion((c) => (c ? { ...c, umbralVetoComponente: Number.isFinite(num) ? num : 0 } : c));
                  setGuardado(false);
                }}
              />
            </label>
            <label className="calibracion-campo calibracion-checkbox">
              <input
                type="checkbox"
                checked={calibracion.vetoHabilitado}
                onChange={(e) => {
                  setCalibracion((c) => (c ? { ...c, vetoHabilitado: e.target.checked } : c));
                  setGuardado(false);
                }}
              />
              <span>Veto absoluto habilitado</span>
            </label>
          </div>

          {!sumaValida && (
            <p className="calibracion-mensaje calibracion-error">
              Los pesos de procesos, memoria y archivos deben sumar 1.0 (suman {suma.toFixed(2)}).
            </p>
          )}
          {error && <p className="calibracion-mensaje calibracion-error">{error}</p>}
          {guardado && !error && <p className="calibracion-mensaje">Calibración guardada.</p>}

          <button onClick={guardar} disabled={!sumaValida || guardando} style={{ marginTop: 10 }}>
            {guardando ? 'Guardando…' : 'Guardar calibración'}
          </button>
        </>
      )}
    </section>
  );
}

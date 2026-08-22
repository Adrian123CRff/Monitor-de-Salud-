import { useEffect, useState } from 'react';
import { guardarCalibracion, obtenerCalibracion } from '../api/cliente';
import type { Calibracion } from '../api/tipos';
import { FichaControl } from './Fichas';

const COMPONENTES = ['PROCESOS', 'MEMORIA', 'ARCHIVOS'] as const;

/** GET/PUT .../calibracion (ADR 0003) -- antes solo se podía tocar con curl. */
export function CalibracionPanel({ onCerrar }: { onCerrar: () => void }) {
  const [calibracion, setCalibracion] = useState<Calibracion | null>(null);
  const [cargando, setCargando] = useState(true);
  const [guardando, setGuardando] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [guardado, setGuardado] = useState(false);
  /**
   * Se intentó guardar con la calibración inválida. Sirve para responder al
   * clic en vez de dejar el botón mudo: un botón deshabilitado no dispara
   * ningún evento, así que quien lo pulsa no recibe explicación de por qué no
   * pasa nada.
   */
  const [intentoInvalido, setIntentoInvalido] = useState(false);

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
    setIntentoInvalido(false);
  }

  async function guardar() {
    if (!calibracion) return;
    if (!valido) {
      setIntentoInvalido(true);
      return;
    }
    setIntentoInvalido(false);
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
  const diferencia = suma - 1;
  const sumaValida = Math.abs(diferencia) < 0.001;

  // El backend ya rechaza un peso en 0 (Calibracion exige que cada uno sea > 0:
  // un componente con peso 0 desaparece del índice y podría estar en llamas sin
  // que el ISBD se entere). Se comprueba también aquí para que el aviso llegue
  // mientras se escribe, y no como un error del servidor después de guardar.
  const pesosEnCero = calibracion
    ? COMPONENTES.filter((c) => (calibracion.pesos[c] ?? 0) <= 0)
    : [];
  const valido = sumaValida && pesosEnCero.length === 0;

  const motivoInvalido = !sumaValida
    ? diferencia > 0
      ? `Los pesos suman ${suma.toFixed(2)}: te sobra ${diferencia.toFixed(2)}. Bajá alguno hasta que sumen 1.00.`
      : `Los pesos suman ${suma.toFixed(2)}: te falta ${Math.abs(diferencia).toFixed(2)}. Subí alguno hasta que sumen 1.00.`
    : pesosEnCero.length > 0
      ? `${pesosEnCero.join(' y ')} en 0: un componente con peso 0 desaparece del índice. Todos deben ser mayores que 0.`
      : null;

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
              <div className="calibracion-celda" key={c}>
                {/* La ficha va FUERA del <label>: dentro, su texto se sumaria al
                    nombre accesible del campo ("PROCESOS i") y un clic en el
                    boton enfocaria el input en vez de abrir la ayuda.
                    Una sola para los tres: el concepto de "peso" es el mismo. */}
                {c === COMPONENTES[0] && <FichaControl clave="pesos" />}
                <label className={`calibracion-campo${valido ? '' : ' campo-invalido'}`}>
                  <span>{c}</span>
                  <input
                    type="number"
                    step="0.01"
                    min="0"
                    max="1"
                    value={calibracion.pesos[c] ?? 0}
                    onChange={(e) => actualizarPeso(c, e.target.value)}
                    aria-invalid={!valido}
                  />
                </label>
              </div>
            ))}
            <div className="calibracion-celda">
              <FichaControl clave="umbralVeto" />
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
            </div>
            <div className="calibracion-celda">
              <FichaControl clave="vetoHabilitado" />
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
          </div>

          {/* El total va junto a los campos, no al pie del panel: es donde está
              mirando quien acaba de escribir un número. Antes el aviso quedaba
              debajo de todo y el efecto era que el boton "no hacia nada". */}
          <p className={`calibracion-suma${valido ? ' ok' : ' mal'}`} aria-live="polite">
            <span>Suma de los pesos</span>
            <strong className="tnum">{suma.toFixed(2)}</strong>
            {valido ? (
              <span className="calibracion-pista">correcto, deben sumar 1.00</span>
            ) : (
              <span className="calibracion-pista">{motivoInvalido}</span>
            )}
          </p>

          {error && (
            <p className="calibracion-mensaje calibracion-error" role="alert">
              {error}
            </p>
          )}
          {guardado && !error && (
            <p className="calibracion-mensaje" role="status">
              Calibración guardada.
            </p>
          )}

          {/* aria-disabled en vez de disabled: un boton deshabilitado no recibe
              foco ni dispara clic, asi que no puede explicar por que no guarda.
              Asi sigue siendo alcanzable y responde con el motivo. */}
          <button
            onClick={guardar}
            aria-disabled={!valido || guardando}
            className={valido ? undefined : 'boton-bloqueado'}
            style={{ marginTop: 10 }}
          >
            {guardando ? 'Guardando…' : 'Guardar calibración'}
          </button>
          {intentoInvalido && motivoInvalido && (
            <p className="calibracion-mensaje calibracion-error" role="alert">
              No se guardó: {motivoInvalido}
            </p>
          )}
        </>
      )}
    </section>
  );
}

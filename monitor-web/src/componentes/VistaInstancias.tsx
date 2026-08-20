import { useCallback, useEffect, useState } from 'react';
import { obtenerInstancias } from '../api/cliente';
import type { ResumenInstancia } from '../api/tipos';
import { COLOR_ESTADO, ETIQUETA_ESTADO, INTERVALO_REFRESCO_MS, formatoNumero } from '../utilidades';

interface Props {
  onSeleccionar: (instanciaId: number, alias: string) => void;
}

/**
 * Pedido del profesor: "un dashboard principal donde aparezcan todas las
 * bases de datos", cada una con un semáforo -- clic en un tile entra al
 * dashboard de detalle que ya existe (App -> DashboardInstancia). Un tile
 * con salud null es una instancia recién agregada al catálogo, sin ningún
 * muestreo todavía -- se muestra "sin datos", nunca un semáforo inventado.
 */
export function VistaInstancias({ onSeleccionar }: Props) {
  const [instancias, setInstancias] = useState<ResumenInstancia[] | null>(null);
  const [error, setError] = useState<string | null>(null);

  const refrescar = useCallback(async () => {
    try {
      const datos = await obtenerInstancias();
      setInstancias(datos);
      setError(null);
    } catch (e) {
      setError(e instanceof Error ? e.message : String(e));
    }
  }, []);

  useEffect(() => {
    refrescar();
    const id = setInterval(refrescar, INTERVALO_REFRESCO_MS);
    return () => clearInterval(id);
  }, [refrescar]);

  return (
    <>
      <header>
        <h1>Monitor de Salud de Oracle</h1>
        <span className="badge">
          <span className={`dot pulse${error ? ' critico' : ''}`} />
          {error ? 'sin conexión' : 'conectado'}
        </span>
      </header>

      {instancias === null && !error && (
        <div className="grid" aria-label="Cargando">
          <div className="card c4 skeleton" style={{ height: 120 }} />
          <div className="card c4 skeleton" style={{ height: 120 }} />
          <div className="card c4 skeleton" style={{ height: 120 }} />
        </div>
      )}

      {error && instancias === null && (
        <div className="card error-panel">
          <p>No se pudo conectar con el backend.</p>
          <p className="muted">{error}</p>
        </div>
      )}

      {instancias !== null && instancias.length === 0 && (
        <div className="card error-panel">
          <p>Todavía no hay ninguna instancia en el catálogo.</p>
        </div>
      )}

      {instancias !== null && instancias.length > 0 && (
        <div className="grid">
          {instancias.map((i) => (
            <div
              key={i.id}
              className="c4 tile tile-clicable"
              role="button"
              tabIndex={0}
              onClick={() => onSeleccionar(i.id, i.alias)}
            >
              <div className="lab">{i.alias}</div>
              {i.salud ? (
                <>
                  <div className="row" style={{ marginTop: 8 }}>
                    <span className="chip" style={{ color: COLOR_ESTADO[i.salud.estado], borderColor: COLOR_ESTADO[i.salud.estado] }}>
                      {ETIQUETA_ESTADO[i.salud.estado]}
                    </span>
                    <div className="val tnum" style={{ fontSize: 22 }}>
                      {formatoNumero(i.salud.puntuacion, 1)}
                    </div>
                  </div>
                  <div className="ts-bar" style={{ marginTop: 10 }}>
                    <div
                      style={{
                        width: `${Math.min(100, Math.max(0, i.salud.puntuacion))}%`,
                        background: COLOR_ESTADO[i.salud.estado],
                      }}
                    />
                  </div>
                  {i.salud.vetusto && (
                    <div className="muted" style={{ fontSize: 12, marginTop: 8 }}>
                      Dato desactualizado
                    </div>
                  )}
                </>
              ) : (
                <div className="muted" style={{ fontSize: 13, marginTop: 8 }}>
                  Sin datos todavía
                </div>
              )}
            </div>
          ))}
        </div>
      )}
    </>
  );
}

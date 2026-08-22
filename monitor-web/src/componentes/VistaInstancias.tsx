import { useCallback, useEffect, useMemo, useState } from 'react';
import { obtenerInstancias } from '../api/cliente';
import type { ResumenInstancia } from '../api/tipos';
import {
  COLOR_ESTADO,
  ETIQUETA_ESTADO,
  GRAVEDAD_ESTADO,
  INTERVALO_REFRESCO_MS,
  formatoNumero,
} from '../utilidades';
import { FichaConcepto } from './Fichas';

/** Peor primero es el defecto: en una pantalla de vigilancia, lo que está mal va arriba. */
type Orden = 'PEOR_PRIMERO' | 'MEJOR_PRIMERO';

interface Props {
  onSeleccionar: (instanciaId: number, alias: string) => void;
}

/**
 * Pedido del profesor: "un dashboard principal donde aparezcan todas las
 * bases de datos", cada una con un semáforo -- clic en un tile entra al
 * dashboard de detalle que ya existe (App -> DashboardInstancia). Un tile
 * con salud null es una instancia recién agregada al catálogo, sin ningún
 * muestreo todavía -- se muestra "sin datos", nunca un semáforo inventado.
 *
 * Búsqueda y orden se resuelven en el cliente, no en el backend: la lista
 * completa ya viaja entera en cada refresco (una fila por base monitoreada,
 * no es un volumen que justifique paginar), así que filtrar aquí es
 * instantáneo y no agrega un viaje por cada tecla. Si algún día el catálogo
 * creciera a cientos de bases, esto pasa a ser un parámetro del endpoint.
 */
export function VistaInstancias({ onSeleccionar }: Props) {
  const [instancias, setInstancias] = useState<ResumenInstancia[] | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [busqueda, setBusqueda] = useState('');
  const [orden, setOrden] = useState<Orden>('PEOR_PRIMERO');

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

  const visibles = useMemo(() => {
    if (instancias === null) return null;
    const termino = busqueda.trim().toLowerCase();
    const filtradas = termino
      ? instancias.filter((i) => i.alias.toLowerCase().includes(termino))
      : instancias;

    // Una instancia sin muestreo todavía no tiene estado. No se le inventa
    // uno: se manda al final en los dos sentidos del orden, porque "no sé"
    // no es ni lo mejor ni lo peor de la lista.
    return [...filtradas].sort((a, b) => {
      if (a.salud === null || b.salud === null) {
        if (a.salud === b.salud) return a.alias.localeCompare(b.alias);
        return a.salud === null ? 1 : -1;
      }
      const ga = GRAVEDAD_ESTADO[a.salud.estado];
      const gb = GRAVEDAD_ESTADO[b.salud.estado];
      if (ga !== gb) return orden === 'PEOR_PRIMERO' ? gb - ga : ga - gb;
      // Mismo estado: desempata la puntuación, en el mismo sentido.
      return orden === 'PEOR_PRIMERO'
        ? a.salud.puntuacion - b.salud.puntuacion
        : b.salud.puntuacion - a.salud.puntuacion;
    });
  }, [instancias, busqueda, orden]);

  return (
    <>
      <header>
        <h1>
          Monitor de Salud de Oracle
          <FichaConcepto clave="vistaGeneral" />
        </h1>
        <span className="badge">
          <span className={`dot pulse${error ? ' critico' : ''}`} />
          {error ? 'sin conexión' : 'conectado'}
        </span>
      </header>

      {instancias !== null && instancias.length > 0 && (
        <div className="controles">
          <input
            type="search"
            className="buscador"
            placeholder="Buscar base de datos por nombre…"
            aria-label="Buscar base de datos por nombre"
            value={busqueda}
            onChange={(e) => setBusqueda(e.target.value)}
          />
          <label className="orden">
            <span className="muted">Orden</span>
            <select
              value={orden}
              aria-label="Ordenar por estado"
              onChange={(e) => setOrden(e.target.value as Orden)}
            >
              <option value="PEOR_PRIMERO">Estado: peor primero</option>
              <option value="MEJOR_PRIMERO">Estado: mejor primero</option>
            </select>
          </label>
        </div>
      )}

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

      {visibles !== null && instancias !== null && instancias.length > 0 && visibles.length === 0 && (
        <div className="card error-panel">
          <p>Ninguna base de datos coincide con «{busqueda}».</p>
        </div>
      )}

      {visibles !== null && visibles.length > 0 && (
        <div className="grid">
          {visibles.map((i) => (
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
                    <div
                      className="val tnum"
                      style={{ fontSize: 22, color: COLOR_ESTADO[i.salud.estado] }}
                    >
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

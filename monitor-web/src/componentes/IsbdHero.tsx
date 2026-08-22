import type { Isbd } from '../api/tipos';
import { COLOR_ESTADO, ETIQUETA_ESTADO, formatoNumero } from '../utilidades';
import { FichaConcepto } from './Fichas';

export function IsbdHero({ isbd }: { isbd: Isbd }) {
  const color = COLOR_ESTADO[isbd.estado];

  return (
    <section className="card c5">
      <div className="muted" style={{ fontSize: 11.5, letterSpacing: '.06em', textTransform: 'uppercase' }}>
        Índice de salud (ISBD)
        <FichaConcepto clave="isbd" />
        <FichaConcepto clave="escala" />
      </div>
      {isbd.vetusto && (
        <div className="muted" style={{ fontSize: 12, marginTop: 6, color: '#b45309' }}>
          Este dato no se ha actualizado recientemente -- el monitor podría haber dejado de muestrear.
          <FichaConcepto clave="vetusto" />
        </div>
      )}
      <div className="hero" style={{ marginTop: 10 }}>
        {/* El numero lleva el color de su estado: el profesor pidio poder leer
            la gravedad sin tener que interpretar la cifra. El chip de al lado
            sigue nombrando el estado, para no depender solo del color. */}
        <div className="fig tnum" style={{ color }}>
          {formatoNumero(isbd.puntuacion, 1)}
        </div>
        <div>
          <span className="chip" style={{ color, borderColor: color }}>
            {ETIQUETA_ESTADO[isbd.estado]}
          </span>
          {isbd.estadoPorVeto && (
            <div className="muted" style={{ fontSize: 12, marginTop: 7 }}>
              Estado forzado por veto -- un componente cayó por debajo del umbral, sin importar el promedio.
              <FichaConcepto clave="veto" />
            </div>
          )}
          {isbd.parcial && (
            <div className="muted" style={{ fontSize: 12, marginTop: 7 }}>
              Cálculo parcial: al menos un componente no se pudo recolectar este ciclo.
              <FichaConcepto clave="parcial" />
            </div>
          )}
        </div>
      </div>
      {isbd.causas.length > 0 && (
        <ul className="causas">
          {isbd.causas.map((causa) => (
            <li key={causa}>{causa}</li>
          ))}
        </ul>
      )}
    </section>
  );
}

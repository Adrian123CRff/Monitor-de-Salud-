import type { Alerta, Isbd } from '../api/tipos';
import { COLOR_ESTADO, ETIQUETA_ESTADO, TRAMOS_ESTADO, tendenciaDe } from '../utilidades';

const ANCHO = 1180;
const ALTO = 300;
const MARGEN_IZQ = 32;
const MARGEN_DER = 76; // sitio para el nombre del tramo, a la derecha
const MARGEN_INF = 24;

/**
 * El eje empieza en 35, no en 0: los datos de una base sana viven entre 80 y
 * 100, y una escala completa los aplastaría contra el techo sin que se viera
 * ningún movimiento. El tramo CRITICO queda como una franja delgada al fondo
 * -- presente como referencia, no a escala real. Es un compromiso consciente
 * entre honestidad de la escala y legibilidad.
 */
const MIN = 35;
const MAX = 101;

interface Serie {
  etiqueta: string;
  valores: (number | null)[];
  /** El ISBD es la línea protagonista; los componentes son contexto. */
  principal?: boolean;
  guion?: string;
}

/**
 * Cómo se dibuja cada línea. Exportado a propósito: la leyenda lo consume para
 * pintar una muestra del trazo REAL en vez de describirlo por su cuenta.
 *
 * Antes la leyenda tenía cuadritos de colores propios y el gráfico dibujaba
 * gris punteado -- decían cosas distintas. Con una sola definición no pueden
 * volver a separarse.
 */
export const TRAZOS = [
  { etiqueta: 'ISBD', principal: true, guion: undefined },
  { etiqueta: 'Procesos', principal: false, guion: '5 4' },
  { etiqueta: 'Memoria', principal: false, guion: '1 3' },
  { etiqueta: 'Archivos', principal: false, guion: '6 3 1 3' },
] as const;

/** Muestra del trazo para la leyenda: el mismo patrón que dibuja el svg. */
export function MuestraDeTrazo({ etiqueta }: { etiqueta: string }) {
  const trazo = TRAZOS.find((t) => t.etiqueta === etiqueta);
  if (!trazo) return null;
  return (
    <svg width="20" height="8" aria-hidden="true" style={{ verticalAlign: 'middle', marginRight: 6 }}>
      <line
        x1="0"
        y1="4"
        x2="20"
        y2="4"
        stroke={trazo.principal ? 'var(--s1)' : 'var(--muted)'}
        strokeWidth={trazo.principal ? 2.5 : 1.5}
        strokeDasharray={trazo.guion}
      />
    </svg>
  );
}

/**
 * Evolución del ISBD y sus componentes (§23 del enunciado).
 *
 * El §23 no pide "un gráfico": pide responder preguntas concretas -- ¿la salud
 * está empeorando?, ¿cuándo aparecieron los problemas?, ¿se repiten a cierta
 * hora? Un dibujo de cuatro líneas planas no responde ninguna, y por eso el
 * profesor lo señaló como información duplicada de los tiles.
 *
 * Cuatro decisiones que lo convierten en respuesta:
 *
 * 1. VEREDICTO ARRIBA. "Estable, +0.7 en 24 h" contesta la primera pregunta sin
 *    obligar a interpretar la pendiente a ojo (ver tendenciaDe).
 * 2. BANDAS DE ESTADO detrás de la línea, con su nombre. La altura de un punto
 *    pasa a significar algo sin ir a leer el eje.
 * 3. LÍNEAS DE REFERENCIA en los cortes reales (40/60/75/90/100), no en
 *    múltiplos redondos: cruzar una línea ES cambiar de estado. Una línea en 80
 *    no significaba nada.
 * 4. FRANJAS DE ALERTA donde hubo un episodio abierto. Esto es lo que convierte
 *    el gráfico en la línea de tiempo de los incidentes en vez de un adorno.
 *
 * Los componentes van en gris y con trazo discontinuo, no con un color cada
 * uno: ya están en los tiles, aquí son contexto del ISBD. El patrón de trazo
 * además los distingue sin depender del color.
 */
export function HistoricoChart({ historico, alertas = [] }: { historico: Isbd[]; alertas?: Alerta[] }) {
  if (historico.length < 2) {
    return (
      <div className="empty">Todavía no hay suficiente historial para graficar (hace falta más de un ciclo).</div>
    );
  }

  const valoresDe: Record<string, (number | null)[]> = {
    ISBD: historico.map((h) => h.puntuacion),
    Procesos: historico.map((h) => h.ip),
    Memoria: historico.map((h) => h.im),
    Archivos: historico.map((h) => h.ia),
  };
  // El ISBD se dibuja al final para que quede por encima de los componentes.
  const series: Serie[] = [...TRAZOS]
    .sort((a, b) => Number(a.principal) - Number(b.principal))
    .map((t) => ({ etiqueta: t.etiqueta, valores: valoresDe[t.etiqueta], principal: t.principal, guion: t.guion }));

  const alturaGrafico = ALTO - MARGEN_INF;
  const anchoGrafico = ANCHO - MARGEN_IZQ - MARGEN_DER;
  const paso = anchoGrafico / (historico.length - 1);
  const y = (valor: number) =>
    alturaGrafico - ((Math.min(MAX, Math.max(MIN, valor)) - MIN) / (MAX - MIN)) * alturaGrafico;
  const x = (i: number) => MARGEN_IZQ + i * paso;

  const inicio = new Date(historico[0].momento).getTime();
  const fin = new Date(historico[historico.length - 1].momento).getTime();
  const xDeFecha = (iso: string) => {
    const t = new Date(iso).getTime();
    const fraccion = fin === inicio ? 0 : (t - inicio) / (fin - inicio);
    return MARGEN_IZQ + Math.min(1, Math.max(0, fraccion)) * anchoGrafico;
  };

  const tendencia = tendenciaDe(historico.map((h) => h.puntuacion));
  const peor = historico.reduce((p, h) => (h.puntuacion < p.puntuacion ? h : p), historico[0]);

  const hora = (iso: string) =>
    new Date(iso).toLocaleTimeString('es-CR', { hour: '2-digit', minute: '2-digit' });

  return (
    <>
      {tendencia && (
        <p className="historico-veredicto">
          <span className={`veredicto-${tendencia.direccion.toLowerCase()}`}>
            {tendencia.direccion === 'MEJORA' && 'Mejorando'}
            {tendencia.direccion === 'ESTABLE' && 'Estable'}
            {tendencia.direccion === 'EMPEORA' && 'Empeorando'}
          </span>
          <span className="muted">
            {tendencia.delta >= 0 ? '+' : '−'}
            {Math.abs(tendencia.delta).toFixed(1)} puntos en la ventana · mínimo {peor.puntuacion.toFixed(1)} a las{' '}
            {hora(peor.momento)}
          </span>
        </p>
      )}

      <svg
        viewBox={`0 0 ${ANCHO} ${ALTO}`}
        width="100%"
        style={{ height: 'auto', display: 'block' }}
        role="img"
        aria-label={`Evolución del índice de salud. ${
          tendencia ? `Tendencia ${tendencia.direccion.toLowerCase()}.` : ''
        } Mínimo ${peor.puntuacion.toFixed(1)} a las ${hora(peor.momento)}.`}
      >
        {TRAMOS_ESTADO.map((t) => {
          const arriba = y(t.hasta);
          const abajo = y(t.desde);
          const alto = abajo - arriba;
          if (alto <= 0) return null;
          return (
            <g key={t.estado}>
              <rect
                x={MARGEN_IZQ}
                y={arriba}
                width={anchoGrafico}
                height={alto}
                fill={COLOR_ESTADO[t.estado]}
                opacity={0.07}
              />
              {/* El nombre solo cabe si la banda es visible de verdad. */}
              {alto >= 16 && (
                <text x={ANCHO - MARGEN_DER + 8} y={arriba + 12} fontSize={11} fill="var(--muted)">
                  {ETIQUETA_ESTADO[t.estado]}
                </text>
              )}
            </g>
          );
        })}

        {/* Franjas de los episodios de alerta: cuándo dolió. */}
        {alertas.map((a) => {
          const x1 = xDeFecha(a.abiertaEn);
          const x2 = a.cerradaEn ? xDeFecha(a.cerradaEn) : MARGEN_IZQ + anchoGrafico;
          return (
            <rect
              key={a.id}
              x={x1}
              // Un episodio instantáneo seria invisible: 3px de ancho minimo.
              width={Math.max(3, x2 - x1)}
              y={0}
              height={alturaGrafico}
              fill="var(--critical)"
              opacity={0.22}
            >
              <title>
                {a.variable} · {a.nivel} · desde {hora(a.abiertaEn)}
                {a.cerradaEn ? ` hasta ${hora(a.cerradaEn)}` : ' (sigue abierta)'}
              </title>
            </rect>
          );
        })}

        {/* Referencias en los cortes de la escala, no en multiplos redondos. */}
        {[40, 60, 75, 90, 100].map((corte) => (
          <g key={corte}>
            <line
              x1={MARGEN_IZQ}
              x2={ANCHO - MARGEN_DER}
              y1={y(corte)}
              y2={y(corte)}
              stroke="var(--grid)"
              strokeWidth={1}
            />
            <text x={0} y={y(corte) + 4} fontSize={11} fill="var(--muted)">
              {corte}
            </text>
          </g>
        ))}

        {series.map((serie) => {
          const puntos = serie.valores
            .map((v, i) => (v === null ? null : `${x(i).toFixed(1)},${y(v).toFixed(1)}`))
            .filter((p): p is string => p !== null);
          if (puntos.length < 2) return null;
          return (
            <polyline
              key={serie.etiqueta}
              points={puntos.join(' ')}
              fill="none"
              stroke={serie.principal ? 'var(--s1)' : 'var(--muted)'}
              strokeWidth={serie.principal ? 2.25 : 1.25}
              strokeDasharray={serie.guion}
              strokeLinejoin="round"
              strokeLinecap="round"
            />
          );
        })}

        <text x={MARGEN_IZQ} y={ALTO - 5} fontSize={11} fill="var(--muted)">
          {hora(historico[0].momento)}
        </text>
        <text x={ANCHO - MARGEN_DER} y={ALTO - 5} fontSize={11} fill="var(--muted)" textAnchor="end">
          {hora(historico[historico.length - 1].momento)}
        </text>
      </svg>
    </>
  );
}

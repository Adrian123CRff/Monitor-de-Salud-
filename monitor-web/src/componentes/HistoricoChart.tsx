import type { Isbd } from '../api/tipos';

const ANCHO = 1180;
const ALTO = 260;
const MARGEN_IZQ = 34;
const MARGEN_INF = 22;

interface Serie {
  etiqueta: string;
  color: string;
  valores: (number | null)[];
}

/** Serie temporal de ISBD/IP/IM/IA -- sin librería externa, un <svg> con polylines. */
export function HistoricoChart({ historico }: { historico: Isbd[] }) {
  if (historico.length < 2) {
    return (
      <div className="empty">Todavía no hay suficiente historial para graficar (hace falta más de un ciclo).</div>
    );
  }

  const series: Serie[] = [
    { etiqueta: 'ISBD', color: 'var(--s1)', valores: historico.map((h) => h.puntuacion) },
    { etiqueta: 'Procesos', color: 'var(--s2)', valores: historico.map((h) => h.ip) },
    { etiqueta: 'Memoria', color: 'var(--s3)', valores: historico.map((h) => h.im) },
    { etiqueta: 'Archivos', color: 'var(--s4)', valores: historico.map((h) => h.ia) },
  ];

  const alturaGrafico = ALTO - MARGEN_INF;
  const anchoGrafico = ANCHO - MARGEN_IZQ;
  const paso = anchoGrafico / (historico.length - 1);
  const y = (valor: number) => alturaGrafico - (valor / 100) * alturaGrafico;

  const primeraHora = new Date(historico[0].momento);
  const ultimaHora = new Date(historico[historico.length - 1].momento);

  return (
    <svg width="100%" height={ALTO} viewBox={`0 0 ${ANCHO} ${ALTO}`} preserveAspectRatio="none">
      {/* líneas de referencia 0/40/75/100 */}
      {[0, 40, 75, 100].map((linea) => (
        <g key={linea}>
          <line
            x1={MARGEN_IZQ}
            x2={ANCHO}
            y1={y(linea)}
            y2={y(linea)}
            stroke="var(--grid)"
            strokeWidth={1}
          />
          <text x={0} y={y(linea) + 4} fontSize={10} fill="var(--muted)">
            {linea}
          </text>
        </g>
      ))}

      {series.map((serie) => {
        const puntos = serie.valores
          .map((v, i) => (v === null ? null : `${(MARGEN_IZQ + i * paso).toFixed(1)},${y(v).toFixed(1)}`))
          .filter((p): p is string => p !== null);
        if (puntos.length < 2) return null;
        return (
          <polyline
            key={serie.etiqueta}
            points={puntos.join(' ')}
            fill="none"
            stroke={serie.color}
            strokeWidth={serie.etiqueta === 'ISBD' ? 2.25 : 1.5}
            strokeLinejoin="round"
            strokeLinecap="round"
          />
        );
      })}

      <text x={MARGEN_IZQ} y={ALTO - 4} fontSize={11} fill="var(--muted)">
        {primeraHora.toLocaleTimeString('es-CR', { hour: '2-digit', minute: '2-digit' })}
      </text>
      <text x={ANCHO} y={ALTO - 4} fontSize={11} fill="var(--muted)" textAnchor="end">
        {ultimaHora.toLocaleTimeString('es-CR', { hour: '2-digit', minute: '2-digit' })}
      </text>
    </svg>
  );
}

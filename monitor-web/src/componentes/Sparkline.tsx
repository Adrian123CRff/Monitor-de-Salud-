interface Props {
  valores: number[];
  color: string;
  ancho?: number;
  alto?: number;
}

/** Mini gráfico de línea sin librería externa -- solo necesita un polyline. */
export function Sparkline({ valores, color, ancho = 118, alto = 34 }: Props) {
  if (valores.length < 2) {
    return <svg width={ancho} height={alto} />;
  }

  const min = Math.min(...valores);
  const max = Math.max(...valores);
  const rango = max - min || 1;
  const paso = ancho / (valores.length - 1);

  const puntos = valores
    .map((v, i) => {
      const x = i * paso;
      const y = alto - ((v - min) / rango) * (alto - 4) - 2;
      return `${x.toFixed(1)},${y.toFixed(1)}`;
    })
    .join(' ');

  return (
    <svg width={ancho} height={alto} viewBox={`0 0 ${ancho} ${alto}`}>
      <polyline points={puntos} fill="none" stroke={color} strokeWidth={1.75} strokeLinejoin="round" strokeLinecap="round" />
    </svg>
  );
}

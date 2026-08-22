import { describe, expect, it } from 'vitest';
import { colorTablespace, formatoBytes, formatoNumero, hace, tendenciaDe } from './utilidades';

describe('colorTablespace', () => {
  it.each([
    [50, 'var(--good)'],
    [74.9, 'var(--good)'],
    [75, 'var(--warning)'],
    [89.9, 'var(--warning)'],
    [90, 'var(--serious)'],
    [97.9, 'var(--serious)'],
    [98, 'var(--critical)'],
    [100, 'var(--critical)'],
  ])('usedPercent=%s -> %s', (usedPercent, esperado) => {
    expect(colorTablespace(usedPercent)).toBe(esperado);
  });
});

describe('hace', () => {
  const ahora = new Date('2026-08-16T12:00:00.000Z');

  it('segundos', () => {
    expect(hace('2026-08-16T11:59:55.000Z', ahora)).toBe('hace 5 s');
  });

  it('minutos', () => {
    expect(hace('2026-08-16T11:58:30.000Z', ahora)).toBe('hace 1 min');
  });

  it('horas', () => {
    expect(hace('2026-08-16T10:59:00.000Z', ahora)).toBe('hace 1 h');
  });

  it('dias', () => {
    expect(hace('2026-08-14T12:00:00.000Z', ahora)).toBe('hace 2 d');
  });

  it('nunca da segundos negativos si el reloj del cliente va detrás', () => {
    expect(hace('2026-08-16T12:00:05.000Z', ahora)).toBe('hace 0 s');
  });
});

describe('formatoNumero', () => {
  it('null se muestra como raya, no como 0 ni NaN (ver Isbd.parcial: "no sé" != 0)', () => {
    expect(formatoNumero(null)).toBe('—');
  });

  it('redondea a los decimales pedidos', () => {
    expect(formatoNumero(82.456, 1)).toBe('82.5');
  });

  it('sin decimales pedidos, redondea a entero', () => {
    expect(formatoNumero(82.6)).toBe('83');
  });
});

describe('formatoBytes', () => {
  it('se queda en bytes bajo 1024', () => {
    expect(formatoBytes(500)).toBe('500 B');
  });

  it('sube a KB con un decimal cuando el valor queda bajo 10', () => {
    expect(formatoBytes(1536)).toBe('1.5 KB');
  });

  it('sube a MB y sigue con un decimal bajo 10', () => {
    expect(formatoBytes(1_500_000)).toBe('1.4 MB');
  });

  it('sin decimal cuando el valor en la unidad ya es >=10', () => {
    expect(formatoBytes(500 * 1024 * 1024)).toBe('500 MB');
  });
});

describe('tendenciaDe -- responde "¿la salud esta empeorando?" (§23)', () => {
  const serie = (...v: number[]) => v;

  it('con muy pocos puntos no inventa una tendencia', () => {
    expect(tendenciaDe(serie(90, 80, 70))).toBeNull();
  });

  it('detecta que empeora', () => {
    const t = tendenciaDe(serie(95, 96, 95, 90, 88, 85, 80, 78, 75));
    expect(t?.direccion).toBe('EMPEORA');
    expect(t?.delta).toBeLessThan(0);
  });

  it('detecta que mejora', () => {
    const t = tendenciaDe(serie(60, 62, 61, 70, 75, 78, 88, 90, 92));
    expect(t?.direccion).toBe('MEJORA');
    expect(t?.delta).toBeGreaterThan(0);
  });

  it('el ruido normal del muestreo se lee como estable, no como tendencia', () => {
    const t = tendenciaDe(serie(95, 94.6, 95.2, 94.8, 95.1, 94.9, 95.3, 94.7, 95));
    expect(t?.direccion).toBe('ESTABLE');
  });

  /**
   * La razon de comparar promedios de tercios en vez del primer punto contra el
   * ultimo: un solo pico no debe decidir el veredicto de toda la ventana.
   */
  it('un pico aislado no cambia el veredicto', () => {
    const t = tendenciaDe(serie(95, 95, 95, 95, 40, 95, 95, 95, 95));
    expect(t?.direccion).toBe('ESTABLE');
  });
});

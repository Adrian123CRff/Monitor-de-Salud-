import { describe, expect, it } from 'vitest';
import { colorTablespace, formatoBytes, formatoNumero, hace } from './utilidades';

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

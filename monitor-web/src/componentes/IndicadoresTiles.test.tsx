import { fireEvent, render, screen } from '@testing-library/react';
import { describe, expect, it, vi } from 'vitest';
import { IndicadoresTiles } from './IndicadoresTiles';
import type { Isbd } from '../api/tipos';

const isbd: Isbd = {
  momento: '2026-08-16T10:00:00Z',
  puntuacion: 82.35,
  estado: 'SALUDABLE',
  ip: 82,
  im: 74,
  ia: 91,
  estadoIp: 'SALUDABLE',
  estadoIm: 'ADVERTENCIA',
  estadoIa: 'OPTIMO',
  estadoPorVeto: false,
  parcial: false,
  vetusto: false,
  causas: [],
};

describe('IndicadoresTiles', () => {
  it('sin onSeleccionar, los tiles no son clicables', () => {
    const { container } = render(<IndicadoresTiles actual={isbd} />);

    // Los tiles llevan botones de ayuda dentro, asi que la pregunta no es "hay
    // algun boton" sino "es el tile en si un boton".
    expect(container.querySelector('.tile[role="button"]')).toBeNull();
    expect(container.querySelector('.tile-clicable')).toBeNull();
  });

  it('con onSeleccionar, un clic en un tile pasa el componente correcto', () => {
    const onSeleccionar = vi.fn();
    render(<IndicadoresTiles actual={isbd} onSeleccionar={onSeleccionar} />);

    fireEvent.click(screen.getByText('Memoria · IM'));

    expect(onSeleccionar).toHaveBeenCalledWith('MEMORIA');
  });
});

describe('IndicadoresTiles -- color semantico (pedido del profesor)', () => {
  it('cada tile toma el color del Estado que mando el backend, no una paleta fija', () => {
    const mixto: Isbd = {
      ...isbd,
      ip: 20,
      im: 80,
      ia: 95,
      estadoIp: 'CRITICO',
      estadoIm: 'SALUDABLE',
      estadoIa: 'OPTIMO',
    };

    render(<IndicadoresTiles actual={mixto} />);

    // El chip nombra el estado, asi el color no es la unica senal (accesibilidad).
    expect(screen.getByText('Crítico')).toBeInTheDocument();
    expect(screen.getByText('Saludable')).toBeInTheDocument();
    expect(screen.getByText('Óptimo')).toBeInTheDocument();

    const critico = screen.getByText('20').closest('.tile');
    expect(critico?.querySelector('.swatch')).toHaveStyle({ background: 'var(--critical)' });
  });

  it('un componente sin datos queda en gris, no en un color de la escala', () => {
    const sinMemoria: Isbd = { ...isbd, im: null, estadoIm: null };

    render(<IndicadoresTiles actual={sinMemoria} />);

    const tileMemoria = screen.getByText('—').closest('.tile');
    expect(tileMemoria?.querySelector('.swatch')).toHaveStyle({ background: 'var(--muted)' });
    // Sin estado no hay chip: no se le inventa una etiqueta.
    expect(tileMemoria?.querySelector('.chip')).toBeNull();
  });
});

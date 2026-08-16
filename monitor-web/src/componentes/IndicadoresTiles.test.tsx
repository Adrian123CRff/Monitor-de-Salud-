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
  estadoPorVeto: false,
  parcial: false,
  causas: [],
};

describe('IndicadoresTiles', () => {
  it('sin onSeleccionar, los tiles no son clicables', () => {
    render(<IndicadoresTiles actual={isbd} historico={[]} />);

    expect(screen.queryByRole('button')).not.toBeInTheDocument();
  });

  it('con onSeleccionar, un clic en un tile pasa el componente correcto', () => {
    const onSeleccionar = vi.fn();
    render(<IndicadoresTiles actual={isbd} historico={[]} onSeleccionar={onSeleccionar} />);

    fireEvent.click(screen.getByText('Memoria · IM'));

    expect(onSeleccionar).toHaveBeenCalledWith('MEMORIA');
  });
});

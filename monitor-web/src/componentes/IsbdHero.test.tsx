import { render, screen } from '@testing-library/react';
import { describe, expect, it } from 'vitest';
import { IsbdHero } from './IsbdHero';
import type { Isbd } from '../api/tipos';

const base: Isbd = {
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

describe('IsbdHero', () => {
  it('muestra la puntuacion y la etiqueta del estado', () => {
    render(<IsbdHero isbd={base} />);

    expect(screen.getByText('82.3')).toBeInTheDocument();
    expect(screen.getByText('Saludable')).toBeInTheDocument();
  });

  it('no muestra los avisos de veto/parcial cuando ninguno aplica', () => {
    render(<IsbdHero isbd={base} />);

    expect(screen.queryByText(/Estado forzado por veto/)).not.toBeInTheDocument();
    expect(screen.queryByText(/Cálculo parcial/)).not.toBeInTheDocument();
  });

  it('avisa cuando el estado fue forzado por veto', () => {
    render(<IsbdHero isbd={{ ...base, estadoPorVeto: true, estado: 'CRITICO' }} />);

    expect(screen.getByText(/Estado forzado por veto/)).toBeInTheDocument();
  });

  it('avisa cuando el calculo es parcial (un componente no se pudo recolectar)', () => {
    render(<IsbdHero isbd={{ ...base, parcial: true, ia: null }} />);

    expect(screen.getByText(/Cálculo parcial/)).toBeInTheDocument();
  });

  it('lista las causas cuando hay alguna', () => {
    render(<IsbdHero isbd={{ ...base, causas: ['Tablespace SYSTEM al 98.2%'] }} />);

    expect(screen.getByText('Tablespace SYSTEM al 98.2%')).toBeInTheDocument();
  });
});

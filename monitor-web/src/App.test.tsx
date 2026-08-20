import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { App } from './App';
import type { Isbd, ResumenInstancia } from './api/tipos';

vi.mock('./api/cliente', async (importOriginal) => {
  const real = await importOriginal<typeof import('./api/cliente')>();
  return {
    ...real,
    obtenerInstancias: vi.fn(),
    obtenerSalud: vi.fn(),
    obtenerHistorico: vi.fn(),
    obtenerTablespaces: vi.fn(),
    obtenerAlertas: vi.fn(),
  };
});

import { obtenerAlertas, obtenerHistorico, obtenerInstancias, obtenerSalud, obtenerTablespaces } from './api/cliente';

const isbdEjemplo: Isbd = {
  momento: '2026-08-16T10:00:00Z',
  puntuacion: 82.35,
  estado: 'SALUDABLE',
  ip: 82,
  im: 74,
  ia: 91,
  estadoPorVeto: false,
  parcial: false,
  vetusto: false,
  causas: [],
};

const instanciasEjemplo: ResumenInstancia[] = [{ id: 1, alias: 'principal', salud: isbdEjemplo }];

beforeEach(() => {
  vi.mocked(obtenerHistorico).mockResolvedValue([]);
  vi.mocked(obtenerTablespaces).mockResolvedValue([]);
  vi.mocked(obtenerAlertas).mockResolvedValue([]);
  vi.mocked(obtenerSalud).mockResolvedValue(isbdEjemplo);
});

describe('App -- navegación entre la vista general y el dashboard de detalle', () => {
  it('arranca en la vista general, no directo en el dashboard de una instancia', async () => {
    vi.mocked(obtenerInstancias).mockResolvedValue(instanciasEjemplo);

    render(<App />);

    await waitFor(() => expect(screen.getByText('principal')).toBeInTheDocument());
    expect(screen.queryByRole('button', { name: /Volver/ })).not.toBeInTheDocument();
  });

  it('clic en un tile entra al dashboard de esa instancia', async () => {
    vi.mocked(obtenerInstancias).mockResolvedValue(instanciasEjemplo);

    render(<App />);

    await waitFor(() => expect(screen.getByText('principal')).toBeInTheDocument());
    fireEvent.click(screen.getByText('principal'));

    await waitFor(() => expect(obtenerSalud).toHaveBeenCalledWith(1));
    expect(screen.getByRole('heading', { name: 'principal' })).toBeInTheDocument();
  });

  it('el botón Volver del dashboard regresa a la vista general', async () => {
    vi.mocked(obtenerInstancias).mockResolvedValue(instanciasEjemplo);

    render(<App />);

    await waitFor(() => expect(screen.getByText('principal')).toBeInTheDocument());
    fireEvent.click(screen.getByText('principal'));
    await waitFor(() => expect(screen.getByRole('button', { name: /Volver/ })).toBeInTheDocument());

    fireEvent.click(screen.getByRole('button', { name: /Volver/ }));

    expect(screen.queryByRole('button', { name: /Volver/ })).not.toBeInTheDocument();
    await waitFor(() => expect(screen.getByText('principal')).toBeInTheDocument());
  });
});

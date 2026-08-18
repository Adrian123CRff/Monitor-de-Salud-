import { render, screen, waitFor } from '@testing-library/react';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { App } from './App';
import type { Isbd } from './api/tipos';

vi.mock('./api/cliente', async (importOriginal) => {
  const real = await importOriginal<typeof import('./api/cliente')>();
  return {
    ...real,
    obtenerSalud: vi.fn(),
    obtenerHistorico: vi.fn(),
    obtenerTablespaces: vi.fn(),
    obtenerAlertas: vi.fn(),
    forzarMuestreo: vi.fn(),
    obtenerComponente: vi.fn(),
  };
});

import { obtenerAlertas, obtenerHistorico, obtenerSalud, obtenerTablespaces } from './api/cliente';

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

beforeEach(() => {
  vi.mocked(obtenerHistorico).mockResolvedValue([]);
  vi.mocked(obtenerTablespaces).mockResolvedValue([]);
  vi.mocked(obtenerAlertas).mockResolvedValue([]);
});

describe('App', () => {
  it('muestra un esqueleto de carga mientras el primer fetch está pendiente, no "Cargando..." plano', () => {
    vi.mocked(obtenerSalud).mockReturnValue(new Promise(() => {})); // nunca resuelve, para inspeccionar el estado de carga

    const { container } = render(<App />);

    expect(container.querySelectorAll('.skeleton').length).toBeGreaterThan(0);
  });

  it('un fallo en /alertas no tumba el resto del dashboard, y se distingue de "sin alertas"', async () => {
    vi.mocked(obtenerSalud).mockResolvedValue(isbdEjemplo);
    vi.mocked(obtenerAlertas).mockRejectedValue(new Error('503 Service Unavailable'));

    render(<App />);

    await waitFor(() => expect(screen.getByText('82.3')).toBeInTheDocument());
    expect(screen.getByText(/No se pudieron cargar las alertas/)).toBeInTheDocument();
    // Tablespaces sí cargó bien (array vacío real, no un error) -- su mensaje es el de "sin datos", no el de error.
    expect(screen.getByText(/Sin datos de tablespaces todavía/)).toBeInTheDocument();
  });
});

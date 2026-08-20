import { render, screen, waitFor } from '@testing-library/react';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { DashboardInstancia } from './DashboardInstancia';
import type { Isbd } from '../api/tipos';

vi.mock('../api/cliente', async (importOriginal) => {
  const real = await importOriginal<typeof import('../api/cliente')>();
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

import { obtenerAlertas, obtenerHistorico, obtenerSalud, obtenerTablespaces } from '../api/cliente';

const isbdEjemplo: Isbd = {
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

beforeEach(() => {
  vi.mocked(obtenerHistorico).mockResolvedValue([]);
  vi.mocked(obtenerTablespaces).mockResolvedValue([]);
  vi.mocked(obtenerAlertas).mockResolvedValue([]);
});

describe('DashboardInstancia', () => {
  it('muestra un esqueleto de carga mientras el primer fetch está pendiente, no "Cargando..." plano', () => {
    vi.mocked(obtenerSalud).mockReturnValue(new Promise(() => {})); // nunca resuelve, para inspeccionar el estado de carga

    const { container } = render(<DashboardInstancia instanciaId={1} alias="principal" onVolver={() => {}} />);

    expect(container.querySelectorAll('.skeleton').length).toBeGreaterThan(0);
  });

  it('un fallo en /alertas no tumba el resto del dashboard, y se distingue de "sin alertas"', async () => {
    vi.mocked(obtenerSalud).mockResolvedValue(isbdEjemplo);
    vi.mocked(obtenerAlertas).mockRejectedValue(new Error('503 Service Unavailable'));

    render(<DashboardInstancia instanciaId={1} alias="principal" onVolver={() => {}} />);

    await waitFor(() => expect(screen.getByText('82.3')).toBeInTheDocument());
    expect(screen.getByText(/No se pudieron cargar las alertas/)).toBeInTheDocument();
    // Tablespaces sí cargó bien (array vacío real, no un error) -- su mensaje es el de "sin datos", no el de error.
    expect(screen.getByText(/Sin datos de tablespaces todavía/)).toBeInTheDocument();
  });

  it('pide los datos de la instancia indicada, no de la instancia 1 fija', async () => {
    vi.mocked(obtenerSalud).mockResolvedValue(isbdEjemplo);

    render(<DashboardInstancia instanciaId={7} alias="cliente-2" onVolver={() => {}} />);

    await waitFor(() => expect(obtenerSalud).toHaveBeenCalledWith(7));
    expect(obtenerHistorico).toHaveBeenCalledWith(expect.any(Date), expect.any(Date), 7);
    expect(obtenerTablespaces).toHaveBeenCalledWith(7);
    expect(obtenerAlertas).toHaveBeenCalledWith(7);
  });

  it('muestra el alias como título y el botón Volver dispara onVolver', async () => {
    vi.mocked(obtenerSalud).mockResolvedValue(isbdEjemplo);
    const onVolver = vi.fn();

    render(<DashboardInstancia instanciaId={1} alias="principal" onVolver={onVolver} />);

    expect(screen.getByRole('heading', { name: 'principal' })).toBeInTheDocument();
    screen.getByRole('button', { name: /Volver/ }).click();
    expect(onVolver).toHaveBeenCalled();
  });
});

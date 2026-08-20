import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { VistaInstancias } from './VistaInstancias';
import type { Isbd, ResumenInstancia } from '../api/tipos';

vi.mock('../api/cliente', async (importOriginal) => {
  const real = await importOriginal<typeof import('../api/cliente')>();
  return { ...real, obtenerInstancias: vi.fn() };
});

import { obtenerInstancias } from '../api/cliente';

const isbdSano: Isbd = {
  momento: '2026-08-16T10:00:00Z',
  puntuacion: 94.5,
  estado: 'OPTIMO',
  ip: 100,
  im: 90,
  ia: 93.5,
  estadoPorVeto: false,
  parcial: false,
  vetusto: false,
  causas: [],
};

afterEach(() => {
  vi.restoreAllMocks();
});

describe('VistaInstancias', () => {
  it('muestra un tile por cada instancia, con su alias y puntuación', async () => {
    const instancias: ResumenInstancia[] = [{ id: 1, alias: 'principal', salud: isbdSano }];
    vi.mocked(obtenerInstancias).mockResolvedValue(instancias);

    render(<VistaInstancias onSeleccionar={() => {}} />);

    await waitFor(() => expect(screen.getByText('principal')).toBeInTheDocument());
    expect(screen.getByText('94.5')).toBeInTheDocument();
    expect(screen.getByText('Óptimo')).toBeInTheDocument();
  });

  it('una instancia sin ningún muestreo todavía muestra "sin datos", no un semáforo inventado', async () => {
    const instancias: ResumenInstancia[] = [{ id: 2, alias: 'recien-agregada', salud: null }];
    vi.mocked(obtenerInstancias).mockResolvedValue(instancias);

    render(<VistaInstancias onSeleccionar={() => {}} />);

    await waitFor(() => expect(screen.getByText('recien-agregada')).toBeInTheDocument());
    expect(screen.getByText(/Sin datos todavía/)).toBeInTheDocument();
  });

  it('clic en un tile llama a onSeleccionar con el id y el alias de esa instancia', async () => {
    const instancias: ResumenInstancia[] = [{ id: 1, alias: 'principal', salud: isbdSano }];
    vi.mocked(obtenerInstancias).mockResolvedValue(instancias);
    const onSeleccionar = vi.fn();

    render(<VistaInstancias onSeleccionar={onSeleccionar} />);

    await waitFor(() => expect(screen.getByText('principal')).toBeInTheDocument());
    fireEvent.click(screen.getByText('principal'));

    expect(onSeleccionar).toHaveBeenCalledWith(1, 'principal');
  });

  it('un fallo al cargar la lista muestra un error, no una grilla vacía silenciosa', async () => {
    vi.mocked(obtenerInstancias).mockRejectedValue(new Error('503 Service Unavailable'));

    render(<VistaInstancias onSeleccionar={() => {}} />);

    await waitFor(() => expect(screen.getByText(/No se pudo conectar con el backend/)).toBeInTheDocument());
  });
});

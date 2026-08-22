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
  estadoIp: 'OPTIMO',
  estadoIm: 'OPTIMO',
  estadoIa: 'OPTIMO',
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

describe('VistaInstancias -- busqueda y orden (pedido del profesor)', () => {
  const conSalud = (id: number, alias: string, puntuacion: number, estado: Isbd['estado']): ResumenInstancia => ({
    id,
    alias,
    salud: { ...isbdSano, puntuacion, estado },
  });

  const tres: ResumenInstancia[] = [
    conSalud(1, 'ventas-produccion', 95, 'OPTIMO'),
    conSalud(2, 'nomina-critica', 22, 'CRITICO'),
    conSalud(3, 'bodega-pruebas', 68, 'ADVERTENCIA'),
  ];

  // Acotado a los tiles: desde que hay botones de ayuda, getAllByRole('button')
  // tambien devuelve los "i" del encabezado.
  const aliasEnPantalla = () =>
    [...document.querySelectorAll('.tile')].map((t) => t.querySelector('.lab')?.textContent);

  it('ordena peor primero por defecto, sin importar el orden que devuelva la API', async () => {
    vi.mocked(obtenerInstancias).mockResolvedValue(tres);

    render(<VistaInstancias onSeleccionar={() => {}} />);
    await waitFor(() => expect(screen.getByText('nomina-critica')).toBeInTheDocument());

    expect(aliasEnPantalla()).toEqual(['nomina-critica', 'bodega-pruebas', 'ventas-produccion']);
  });

  it('invierte el orden al elegir "mejor primero"', async () => {
    vi.mocked(obtenerInstancias).mockResolvedValue(tres);

    render(<VistaInstancias onSeleccionar={() => {}} />);
    await waitFor(() => expect(screen.getByText('nomina-critica')).toBeInTheDocument());

    fireEvent.change(screen.getByLabelText('Ordenar por estado'), {
      target: { value: 'MEJOR_PRIMERO' },
    });

    expect(aliasEnPantalla()).toEqual(['ventas-produccion', 'bodega-pruebas', 'nomina-critica']);
  });

  it('una instancia sin datos va al final, no se le inventa un estado', async () => {
    vi.mocked(obtenerInstancias).mockResolvedValue([
      { id: 9, alias: 'recien-agregada', salud: null },
      ...tres,
    ]);

    render(<VistaInstancias onSeleccionar={() => {}} />);
    await waitFor(() => expect(screen.getByText('recien-agregada')).toBeInTheDocument());

    expect(aliasEnPantalla().at(-1)).toBe('recien-agregada');
  });

  it('la busqueda filtra por nombre, sin distinguir mayusculas', async () => {
    vi.mocked(obtenerInstancias).mockResolvedValue(tres);

    render(<VistaInstancias onSeleccionar={() => {}} />);
    await waitFor(() => expect(screen.getByText('nomina-critica')).toBeInTheDocument());

    fireEvent.change(screen.getByLabelText('Buscar base de datos por nombre'), {
      target: { value: 'NOMINA' },
    });

    expect(screen.getByText('nomina-critica')).toBeInTheDocument();
    expect(screen.queryByText('ventas-produccion')).not.toBeInTheDocument();
  });

  it('si la busqueda no encuentra nada lo dice, en vez de mostrar la lista vacia', async () => {
    vi.mocked(obtenerInstancias).mockResolvedValue(tres);

    render(<VistaInstancias onSeleccionar={() => {}} />);
    await waitFor(() => expect(screen.getByText('nomina-critica')).toBeInTheDocument());

    fireEvent.change(screen.getByLabelText('Buscar base de datos por nombre'), {
      target: { value: 'no-existe' },
    });

    expect(screen.getByText(/Ninguna base de datos coincide/)).toBeInTheDocument();
  });
});

import { render, screen } from '@testing-library/react';
import { describe, expect, it } from 'vitest';
import { AlertasPanel } from './AlertasPanel';
import type { Alerta } from '../api/tipos';

function alerta(sobrescribe: Partial<Alerta>): Alerta {
  return {
    id: 1,
    componente: 'ARCHIVOS',
    variable: 'peor_tablespace_pct',
    entidad: null,
    nivel: 'CRITICO',
    valor: 98.2,
    umbral: 98,
    descripcion: 'Tablespace SYSTEM al 98.2%',
    abiertaEn: new Date().toISOString(),
    cerradaEn: null,
    ...sobrescribe,
  };
}

describe('AlertasPanel', () => {
  it('sin alertas muestra el mensaje de "todo dentro de los umbrales"', () => {
    render(<AlertasPanel alertas={[]} />);

    expect(screen.getByText(/todo dentro de los umbrales/)).toBeInTheDocument();
    expect(screen.getByText('ninguna')).toBeInTheDocument();
  });

  it('cada alerta muestra nivel, componente, variable y descripcion', () => {
    render(<AlertasPanel alertas={[alerta({})]} />);

    expect(screen.getByText('CRITICO')).toBeInTheDocument();
    expect(screen.getByText(/ARCHIVOS · peor_tablespace_pct/)).toBeInTheDocument();
    expect(screen.getByText('Tablespace SYSTEM al 98.2%')).toBeInTheDocument();
  });

  it('cuando hay entidad (p. ej. un tablespace concreto) la agrega al renglon', () => {
    render(<AlertasPanel alertas={[alerta({ entidad: 'SYSTEM' })]} />);

    expect(screen.getByText(/ARCHIVOS · peor_tablespace_pct · SYSTEM/)).toBeInTheDocument();
  });

  it('el contador refleja la cantidad de alertas abiertas', () => {
    render(<AlertasPanel alertas={[alerta({ id: 1 }), alerta({ id: 2, variable: 'a2_datafiles_offline' })]} />);

    expect(screen.getByText('2')).toBeInTheDocument();
  });

  it('un error de carga se distingue de "sin alertas abiertas" -- no debe parecer que todo está bien', () => {
    render(<AlertasPanel alertas={[]} error="503 Service Unavailable" />);

    expect(screen.getByText(/No se pudieron cargar las alertas/)).toBeInTheDocument();
    expect(screen.getByText('error')).toBeInTheDocument();
    expect(screen.queryByText(/todo dentro de los umbrales/)).not.toBeInTheDocument();
  });
});

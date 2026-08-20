import { render, screen } from '@testing-library/react';
import { describe, expect, it, vi } from 'vitest';
import { ComponenteDetalle } from './ComponenteDetalle';
import type { Muestra } from '../api/tipos';

describe('ComponenteDetalle', () => {
  it('muestra el estado de carga', () => {
    render(
      <ComponenteDetalle componente="MEMORIA" detalle={null} cargando error={null} onCerrar={vi.fn()} />,
    );

    expect(screen.getByText(/Cargando detalle/)).toBeInTheDocument();
  });

  it('muestra el error cuando la carga falla', () => {
    render(
      <ComponenteDetalle
        componente="MEMORIA"
        detalle={null}
        cargando={false}
        error="500 Internal Server Error"
        onCerrar={vi.fn()}
      />,
    );

    expect(screen.getByText(/No se pudo cargar el detalle/)).toBeInTheDocument();
  });

  it('para PROCESOS separa usuarios y fondo en vistas distintas', () => {
    const detalle: Record<string, Muestra> = {
      usuarios: {
        componente: 'PROCESOS', momento: '2026-08-16T10:00:00Z',
        valores: { p1_procesos_actuales: 84 },
        puntuacion: 100, estado: 'OPTIMO', vetado: false, variables: [],
      },
      fondo: {
        componente: 'PROCESOS', momento: '2026-08-16T10:00:00Z',
        valores: { b1_procesos_caidos: 0 },
        puntuacion: 100, estado: 'OPTIMO', vetado: false, variables: [],
      },
    };

    render(<ComponenteDetalle componente="PROCESOS" detalle={detalle} cargando={false} error={null} onCerrar={vi.fn()} />);

    expect(screen.getByText(/Usuarios/)).toBeInTheDocument();
    expect(screen.getByText(/Procesos de fondo/)).toBeInTheDocument();
    expect(screen.getByText('p1_procesos_actuales')).toBeInTheDocument();
    expect(screen.getByText('84')).toBeInTheDocument();
  });

  it('sin datos todavia muestra el mensaje vacio, no una tabla vacia', () => {
    render(<ComponenteDetalle componente="ARCHIVOS" detalle={{}} cargando={false} error={null} onCerrar={vi.fn()} />);

    expect(screen.getByText(/Sin datos todavía/)).toBeInTheDocument();
  });
});

describe('ComponenteDetalle -- desglose por variable (pedido del profesor)', () => {
  const archivosCon90: Record<string, Muestra> = {
    actual: {
      componente: 'ARCHIVOS',
      momento: '2026-08-16T10:00:00Z',
      valores: { a6_min_miembros_grupo: 1, peor_tablespace_pct: 40 },
      puntuacion: 90,
      estado: 'OPTIMO',
      vetado: false,
      variables: [
        {
          variable: 'redundancia_redo', valor: 1, puntuacion: 0, estado: 'CRITICO',
          pesoEnComponente: 0.1, aportePerdido: 10, disparoVeto: false,
        },
        {
          variable: 'peor_tablespace_pct', valor: 40, puntuacion: 100, estado: 'OPTIMO',
          pesoEnComponente: 0.4, aportePerdido: 0, disparoVeto: false,
        },
      ],
    },
  };

  it('muestra cuanto puntua cada variable y cuanto le cuesta al componente', () => {
    render(<ComponenteDetalle componente="ARCHIVOS" detalle={archivosCon90} cargando={false} error={null} onCerrar={vi.fn()} />);

    expect(screen.getByText('redundancia_redo')).toBeInTheDocument();
    // El aporte perdido se muestra en negativo: son puntos que RESTA.
    expect(screen.getByText('−10.0')).toBeInTheDocument();
    // Una variable que no cuesta nada no muestra un 0, muestra un guion.
    expect(screen.getAllByText('—').length).toBeGreaterThan(0);
  });

  it('marca la variable que veta el componente entero', () => {
    const conVeto: Record<string, Muestra> = {
      actual: {
        ...archivosCon90.actual,
        puntuacion: 0,
        estado: 'CRITICO',
        vetado: true,
        variables: [
          {
            variable: 'a2_datafiles_offline', valor: 1, puntuacion: 0, estado: 'CRITICO',
            pesoEnComponente: 0.2, aportePerdido: 20, disparoVeto: true,
          },
        ],
      },
    };

    render(<ComponenteDetalle componente="ARCHIVOS" detalle={conVeto} cargando={false} error={null} onCerrar={vi.fn()} />);

    expect(screen.getByText('veto')).toBeInTheDocument();
  });

  it('una muestra sin puntuar sigue mostrando el crudo y lo dice', () => {
    const sinPuntuar: Record<string, Muestra> = {
      actual: {
        componente: 'MEMORIA', momento: '2026-08-16T10:00:00Z',
        valores: { m5_pga_asignada_bytes: 1024 },
        puntuacion: null, estado: null, vetado: null, variables: [],
      },
    };

    render(<ComponenteDetalle componente="MEMORIA" detalle={sinPuntuar} cargando={false} error={null} onCerrar={vi.fn()} />);

    expect(screen.getByText(/no se pudo puntuar/i)).toBeInTheDocument();
    expect(screen.getByText('m5_pga_asignada_bytes')).toBeInTheDocument();
  });
});

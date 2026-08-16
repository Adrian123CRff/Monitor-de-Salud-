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
      usuarios: { componente: 'PROCESOS', momento: '2026-08-16T10:00:00Z', valores: { p1_procesos_actuales: 84 } },
      fondo: { componente: 'PROCESOS', momento: '2026-08-16T10:00:00Z', valores: { b1_procesos_caidos: 0 } },
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

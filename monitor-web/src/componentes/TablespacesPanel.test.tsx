import { render, screen } from '@testing-library/react';
import { describe, expect, it } from 'vitest';
import { TablespacesPanel } from './TablespacesPanel';
import type { Tablespace } from '../api/tipos';

describe('TablespacesPanel', () => {
  it('sin tablespaces muestra el mensaje de "sin datos todavia"', () => {
    render(<TablespacesPanel tablespaces={[]} />);

    expect(screen.getByText(/Sin datos de tablespaces todavía/)).toBeInTheDocument();
  });

  it('ordena por % usado, el peor primero -- nunca por el orden en que llegan', () => {
    const desordenados: Tablespace[] = [
      { nombre: 'USERS', usedPercent: 40, usedBytes: 400, maxBytes: 1000 },
      { nombre: 'SYSTEM', usedPercent: 98.2, usedBytes: 982, maxBytes: 1000 },
      { nombre: 'TEMP', usedPercent: 62, usedBytes: 620, maxBytes: 1000 },
    ];

    render(<TablespacesPanel tablespaces={desordenados} />);

    const nombres = screen.getAllByText(/USERS|SYSTEM|TEMP/).map((el) => el.textContent);
    expect(nombres).toEqual(['SYSTEM', 'TEMP', 'USERS']);
  });

  it('suma el total ocupado en bytes cuando hay datos', () => {
    render(
      <TablespacesPanel
        tablespaces={[
          { nombre: 'USERS', usedPercent: 40, usedBytes: 500 * 1024 * 1024, maxBytes: 1000 },
          { nombre: 'SYSTEM', usedPercent: 60, usedBytes: 500 * 1024 * 1024, maxBytes: 1000 },
        ]}
      />,
    );

    expect(screen.getByText(/Total ocupado: 1000 MB/)).toBeInTheDocument();
  });

  it('un error de carga se distingue de "sin datos" -- no debe parecer que todo está en orden', () => {
    render(<TablespacesPanel tablespaces={[]} error="500 Internal Server Error" />);

    expect(screen.getByText(/No se pudo cargar tablespaces/)).toBeInTheDocument();
    expect(screen.queryByText(/Sin datos de tablespaces todavía/)).not.toBeInTheDocument();
  });
});

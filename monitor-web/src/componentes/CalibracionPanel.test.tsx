import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { CalibracionPanel } from './CalibracionPanel';
import * as cliente from '../api/cliente';
import type { Calibracion } from '../api/tipos';

vi.mock('../api/cliente');

const inicial: Calibracion = {
  pesos: { PROCESOS: 0.3, MEMORIA: 0.35, ARCHIVOS: 0.35 },
  vetoHabilitado: true,
  umbralVetoComponente: 40,
};

beforeEach(() => {
  vi.mocked(cliente.obtenerCalibracion).mockResolvedValue(inicial);
});

describe('CalibracionPanel', () => {
  it('carga y muestra los pesos vigentes', async () => {
    render(<CalibracionPanel onCerrar={vi.fn()} />);

    expect(await screen.findByLabelText('PROCESOS')).toHaveValue(0.3);
    expect(screen.getByLabelText('MEMORIA')).toHaveValue(0.35);
    expect(screen.getByLabelText('ARCHIVOS')).toHaveValue(0.35);
  });

  it('avisa cuando los pesos editados no suman 1 y deshabilita guardar', async () => {
    render(<CalibracionPanel onCerrar={vi.fn()} />);
    const procesos = await screen.findByLabelText('PROCESOS');

    fireEvent.change(procesos, { target: { value: '0.5' } });

    expect(await screen.findByText(/deben sumar 1.0/)).toBeInTheDocument();
    expect(screen.getByRole('button', { name: /Guardar calibración/ })).toBeDisabled();
  });

  it('guarda la calibración editada cuando los pesos suman 1', async () => {
    const guardada: Calibracion = { ...inicial, pesos: { PROCESOS: 0.4, MEMORIA: 0.3, ARCHIVOS: 0.3 } };
    vi.mocked(cliente.guardarCalibracion).mockResolvedValue(guardada);

    render(<CalibracionPanel onCerrar={vi.fn()} />);
    fireEvent.change(await screen.findByLabelText('PROCESOS'), { target: { value: '0.4' } });
    fireEvent.change(screen.getByLabelText('MEMORIA'), { target: { value: '0.3' } });
    fireEvent.change(screen.getByLabelText('ARCHIVOS'), { target: { value: '0.3' } });

    fireEvent.click(screen.getByRole('button', { name: /Guardar calibración/ }));

    await waitFor(() => expect(cliente.guardarCalibracion).toHaveBeenCalled());
    expect(await screen.findByText('Calibración guardada.')).toBeInTheDocument();
  });

  it('muestra el error del backend si el guardado falla', async () => {
    vi.mocked(cliente.guardarCalibracion).mockRejectedValue(new Error('suman 0.9'));

    render(<CalibracionPanel onCerrar={vi.fn()} />);
    await screen.findByLabelText('PROCESOS');

    fireEvent.click(screen.getByRole('button', { name: /Guardar calibración/ }));

    expect(await screen.findByText('suman 0.9')).toBeInTheDocument();
  });
});

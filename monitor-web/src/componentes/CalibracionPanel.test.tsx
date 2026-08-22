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

  it('avisa cuanto SOBRA, no solo que la suma esta mal', async () => {
    render(<CalibracionPanel onCerrar={vi.fn()} />);
    const procesos = await screen.findByLabelText('PROCESOS');

    // 0.5 + 0.35 + 0.35 = 1.20
    fireEvent.change(procesos, { target: { value: '0.5' } });

    expect(await screen.findByText(/te sobra 0.20/)).toBeInTheDocument();
    expect(screen.getByRole('button', { name: /Guardar calibración/ }))
      .toHaveAttribute('aria-disabled', 'true');
  });

  it('avisa cuanto FALTA cuando la suma se queda corta', async () => {
    render(<CalibracionPanel onCerrar={vi.fn()} />);
    const procesos = await screen.findByLabelText('PROCESOS');

    // 0.1 + 0.35 + 0.35 = 0.80
    fireEvent.change(procesos, { target: { value: '0.1' } });

    expect(await screen.findByText(/te falta 0.20/)).toBeInTheDocument();
  });

  /**
   * El motivo del bug reportado: el boton se deshabilitaba y no disparaba nada,
   * asi que quien lo pulsaba no recibia explicacion. Ahora responde.
   */
  it('al pulsar Guardar con la suma mal, explica en vez de quedarse mudo', async () => {
    render(<CalibracionPanel onCerrar={vi.fn()} />);
    fireEvent.change(await screen.findByLabelText('PROCESOS'), { target: { value: '0.5' } });

    fireEvent.click(screen.getByRole('button', { name: /Guardar calibración/ }));

    expect(await screen.findByText(/No se guardó/)).toBeInTheDocument();
    // Y no llama al backend con datos invalidos.
    expect(cliente.guardarCalibracion).not.toHaveBeenCalled();
  });

  it('rechaza un peso en 0 sin esperar al error del backend', async () => {
    render(<CalibracionPanel onCerrar={vi.fn()} />);

    // 0 + 0.65 + 0.35 = 1.00: la suma cierra, pero MEMORIA quedaria fuera del indice.
    fireEvent.change(await screen.findByLabelText('PROCESOS'), { target: { value: '0' } });
    fireEvent.change(screen.getByLabelText('MEMORIA'), { target: { value: '0.65' } });

    expect(await screen.findByText(/desaparece del índice/)).toBeInTheDocument();
    expect(screen.getByRole('button', { name: /Guardar calibración/ }))
      .toHaveAttribute('aria-disabled', 'true');
  });

  it('muestra la suma en vivo mientras se edita', async () => {
    render(<CalibracionPanel onCerrar={vi.fn()} />);
    await screen.findByLabelText('PROCESOS');

    expect(screen.getByText('1.00')).toBeInTheDocument();

    fireEvent.change(screen.getByLabelText('PROCESOS'), { target: { value: '0.4' } });

    expect(await screen.findByText('1.10')).toBeInTheDocument();
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

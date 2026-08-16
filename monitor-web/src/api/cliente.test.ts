import { afterEach, describe, expect, it, vi } from 'vitest';
import { forzarMuestreo, obtenerAlertas, obtenerSalud, obtenerTablespaces, SinDatosAunError } from './cliente';
import type { Isbd } from './tipos';

function respuestaJson(status: number, cuerpo: unknown): Response {
  return {
    ok: status >= 200 && status < 300,
    status,
    statusText: status === 404 ? 'Not Found' : 'Internal Server Error',
    json: () => Promise.resolve(cuerpo),
  } as Response;
}

const isbdEjemplo: Isbd = {
  momento: '2026-08-16T10:00:00Z',
  puntuacion: 82.35,
  estado: 'SALUDABLE',
  ip: 82,
  im: 74,
  ia: 91,
  estadoPorVeto: false,
  parcial: false,
  causas: [],
};

afterEach(() => {
  vi.unstubAllGlobals();
});

describe('obtenerSalud', () => {
  it('devuelve el Isbd cuando la respuesta es ok', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(respuestaJson(200, isbdEjemplo)));

    await expect(obtenerSalud()).resolves.toEqual(isbdEjemplo);
  });

  it('un 404 (SaludNoDisponibleException) se traduce a SinDatosAunError con el detail del ProblemDetail', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn().mockResolvedValue(
        respuestaJson(404, { title: 'No encontrado', status: 404, detail: 'No hay datos todavía' }),
      ),
    );

    await expect(obtenerSalud()).rejects.toBeInstanceOf(SinDatosAunError);
    await expect(obtenerSalud()).rejects.toThrow('No hay datos todavía');
  });

  it('pide la ruta de la instancia indicada', async () => {
    const fetchMock = vi.fn().mockResolvedValue(respuestaJson(200, isbdEjemplo));
    vi.stubGlobal('fetch', fetchMock);

    await obtenerSalud(7);

    expect(fetchMock).toHaveBeenCalledWith('/api/v1/instancias/7/salud');
  });
});

describe('errores que no son 404', () => {
  it('un 500 sin body parseable cae al status/statusText', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn().mockResolvedValue({
        ok: false,
        status: 500,
        statusText: 'Internal Server Error',
        json: () => Promise.reject(new Error('sin cuerpo')),
      } as unknown as Response),
    );

    await expect(obtenerTablespaces()).rejects.toThrow('500 Internal Server Error');
  });

  it('un 503 (IllegalStateException) usa el detail del ProblemDetail', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn().mockResolvedValue(
        respuestaJson(503, { title: 'Servicio no disponible', status: 503, detail: 'fallaron los tres componentes' }),
      ),
    );

    await expect(obtenerAlertas()).rejects.toThrow('fallaron los tres componentes');
  });
});

describe('forzarMuestreo', () => {
  it('hace POST y devuelve el Isbd resultante', async () => {
    const fetchMock = vi.fn().mockResolvedValue(respuestaJson(200, isbdEjemplo));
    vi.stubGlobal('fetch', fetchMock);

    await expect(forzarMuestreo()).resolves.toEqual(isbdEjemplo);
    expect(fetchMock).toHaveBeenCalledWith('/api/v1/instancias/1/muestrear', { method: 'POST' });
  });
});

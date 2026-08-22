import { render, screen } from '@testing-library/react';
import { describe, expect, it } from 'vitest';
import { HistoricoChart } from './HistoricoChart';
import type { Alerta, Isbd } from '../api/tipos';

function punto(puntuacion: number, minutos: number): Isbd {
  return {
    momento: new Date(Date.UTC(2026, 7, 20, 10, minutos)).toISOString(),
    puntuacion,
    estado: puntuacion >= 90 ? 'OPTIMO' : 'SALUDABLE',
    ip: puntuacion,
    im: 100,
    ia: 90,
    estadoIp: 'OPTIMO',
    estadoIm: 'OPTIMO',
    estadoIa: 'OPTIMO',
    estadoPorVeto: false,
    parcial: false,
    vetusto: false,
    causas: [],
  };
}

const sana: Isbd[] = [95, 94.5, 95.2, 94.8, 95, 94.9, 95.1, 95, 94.7].map((v, i) => punto(v, i * 5));

describe('HistoricoChart', () => {
  it('sin historial suficiente lo dice, en vez de dibujar una linea de un punto', () => {
    render(<HistoricoChart historico={[punto(95, 0)]} />);

    expect(screen.getByText(/no hay suficiente historial/i)).toBeInTheDocument();
  });

  /** La primera pregunta del §23, respondida sin obligar a interpretar la pendiente. */
  it('muestra el veredicto de tendencia', () => {
    render(<HistoricoChart historico={sana} />);

    expect(screen.getByText('Estable')).toBeInTheDocument();
  });

  it('un descenso sostenido se reporta como empeorando', () => {
    const bajando = [96, 95, 94, 90, 87, 84, 80, 77, 74].map((v, i) => punto(v, i * 5));

    render(<HistoricoChart historico={bajando} />);

    expect(screen.getByText('Empeorando')).toBeInTheDocument();
  });

  it('senala el minimo de la ventana y a que hora fue', () => {
    const conCaida = [95, 95, 95, 82.5, 95, 95, 95, 95, 95].map((v, i) => punto(v, i * 5));

    render(<HistoricoChart historico={conCaida} />);

    expect(screen.getByText(/mínimo 82.5/)).toBeInTheDocument();
  });

  /**
   * Las lineas del eje son los cortes de la escala (§18), no multiplos redondos:
   * cruzar una linea del grafico tiene que ser cambiar de estado. Un 80 no
   * significaba nada.
   */
  it('las referencias del eje son los cortes de la escala, sin el 80', () => {
    const { container } = render(<HistoricoChart historico={sana} />);
    const textos = [...container.querySelectorAll('text')].map((t) => t.textContent);

    ['40', '60', '75', '90', '100'].forEach((corte) => expect(textos).toContain(corte));
    expect(textos).not.toContain('80');
  });

  it('nombra los tramos de estado sobre el grafico', () => {
    const { container } = render(<HistoricoChart historico={sana} />);
    const textos = [...container.querySelectorAll('text')].map((t) => t.textContent);

    expect(textos).toContain('Óptimo');
    expect(textos).toContain('Advertencia');
  });

  it('dibuja una franja por cada episodio de alerta de la ventana', () => {
    const alertas: Alerta[] = [
      {
        id: 7,
        componente: 'MEMORIA',
        variable: 'm8_over_alloc_delta',
        entidad: null,
        nivel: 'ADVERTENCIA',
        valor: 3,
        umbral: 1,
        descripcion: 'Presión de PGA',
        abiertaEn: new Date(Date.UTC(2026, 7, 20, 10, 10)).toISOString(),
        cerradaEn: new Date(Date.UTC(2026, 7, 20, 10, 20)).toISOString(),
      },
    ];

    const { container } = render(<HistoricoChart historico={sana} alertas={alertas} />);

    const titulos = [...container.querySelectorAll('title')].map((t) => t.textContent);
    expect(titulos.some((t) => t?.includes('m8_over_alloc_delta'))).toBe(true);
  });

  it('un episodio sin cerrar se dibuja hasta el borde, no desaparece', () => {
    const abierta: Alerta[] = [
      {
        id: 8,
        componente: 'PROCESOS',
        variable: 'p6_sesiones_bloqueadas',
        entidad: null,
        nivel: 'CRITICO',
        valor: 5,
        umbral: 1,
        descripcion: 'Sesiones bloqueadas',
        abiertaEn: new Date(Date.UTC(2026, 7, 20, 10, 25)).toISOString(),
        cerradaEn: null,
      },
    ];

    const { container } = render(<HistoricoChart historico={sana} alertas={abierta} />);

    const titulos = [...container.querySelectorAll('title')].map((t) => t.textContent);
    expect(titulos.some((t) => t?.includes('sigue abierta'))).toBe(true);
  });
});

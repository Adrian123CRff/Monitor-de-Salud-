import { fireEvent, render, screen } from '@testing-library/react';
import { describe, expect, it } from 'vitest';
import { Ayuda } from './Ayuda';
import { FichaConcepto, FichaControl, FichaVariable } from './Fichas';

describe('Ayuda -- el boton y su ficha', () => {
  it('la ficha empieza cerrada: no debe competir con los numeros', () => {
    render(<Ayuda titulo="Prueba">contenido de prueba</Ayuda>);

    expect(screen.queryByText('contenido de prueba')).not.toBeInTheDocument();
    expect(screen.getByRole('button')).toHaveAttribute('aria-expanded', 'false');
  });

  it('abre y cierra con clic, y lo anuncia con aria-expanded', () => {
    render(<Ayuda titulo="Prueba">contenido de prueba</Ayuda>);
    const boton = screen.getByRole('button');

    fireEvent.click(boton);
    expect(screen.getByText('contenido de prueba')).toBeInTheDocument();
    expect(boton).toHaveAttribute('aria-expanded', 'true');

    fireEvent.click(boton);
    expect(screen.queryByText('contenido de prueba')).not.toBeInTheDocument();
  });

  it('Escape cierra y devuelve el foco al boton', () => {
    render(<Ayuda titulo="Prueba">contenido de prueba</Ayuda>);
    const boton = screen.getByRole('button');

    fireEvent.click(boton);
    fireEvent.keyDown(document, { key: 'Escape' });

    expect(screen.queryByText('contenido de prueba')).not.toBeInTheDocument();
    // Sin esto, quien navega con teclado queda perdido al fondo del documento.
    expect(boton).toHaveFocus();
  });

  it('un clic fuera cierra la ficha', () => {
    render(
      <div>
        <Ayuda titulo="Prueba">contenido de prueba</Ayuda>
        <span data-testid="afuera">otra cosa</span>
      </div>,
    );

    fireEvent.click(screen.getByRole('button'));
    expect(screen.getByText('contenido de prueba')).toBeInTheDocument();

    fireEvent.mouseDown(screen.getByTestId('afuera'));
    expect(screen.queryByText('contenido de prueba')).not.toBeInTheDocument();
  });

  it('el aria-label nombra lo que explica, no solo "i"', () => {
    render(<Ayuda titulo="Índice de salud">contenido</Ayuda>);

    expect(screen.getByRole('button', { name: /Índice de salud/ })).toBeInTheDocument();
  });
});

describe('FichaVariable', () => {
  it('muestra el limite que llega por props, no uno escrito a mano', () => {
    render(<FichaVariable variable="util_procesos_pct" valorOk={62} valorCritico={88} />);

    fireEvent.click(screen.getByRole('button'));

    // 62/88 son valores calibrados inventados para el test: si la ficha
    // tuviera el umbral escrito adentro, aqui saldrian 70 y 95.
    expect(screen.getByText(/Sano hasta 62, crítico desde 88/)).toBeInTheDocument();
  });

  it('sin limites no inventa una banda', () => {
    render(<FichaVariable variable="p6_sesiones_bloqueadas" valorOk={null} valorCritico={null} />);

    fireEvent.click(screen.getByRole('button'));

    expect(screen.queryByText(/Sano hasta/)).not.toBeInTheDocument();
    expect(screen.getByText(/Cuántas conexiones están detenidas/)).toBeInTheDocument();
  });

  it('avisa de que el umbral no esta calibrado', () => {
    render(<FichaVariable variable="util_procesos_pct" valorOk={70} valorCritico={95} />);

    fireEvent.click(screen.getByRole('button'));

    expect(screen.getByText(/sin calibrar con datos reales/)).toBeInTheDocument();
  });

  it('una variable sin ficha escrita no renderiza boton, en vez de abrir algo vacio', () => {
    const { container } = render(<FichaVariable variable="variable_que_no_existe" />);

    expect(container).toBeEmptyDOMElement();
  });

  it('los contadores acumulados comparten una ficha generica', () => {
    render(<FichaVariable variable="m8_over_alloc_acum" />);

    fireEvent.click(screen.getByRole('button'));

    expect(screen.getByText(/solo sirve para sacar la diferencia|solo crece/i)).toBeInTheDocument();
  });

  it('la trampa se muestra cuando la variable tiene una', () => {
    render(<FichaVariable variable="p5_sesiones_inactivas" />);

    fireEvent.click(screen.getByRole('button'));

    // El malentendido clasico: muchas sesiones inactivas suena mal y no lo es.
    expect(screen.getByText(/NO es malo/)).toBeInTheDocument();
  });
});

describe('FichaConcepto', () => {
  it('explica por que se diseno asi, no solo que es', () => {
    render(<FichaConcepto clave="veto" />);

    fireEvent.click(screen.getByRole('button'));

    expect(screen.getByText('Qué es')).toBeInTheDocument();
    expect(screen.getByText('Cómo funciona')).toBeInTheDocument();
    expect(screen.getByText('Por qué así')).toBeInTheDocument();
    // El ejemplo del enunciado: el promedio que miente.
    expect(screen.getByText(/94/)).toBeInTheDocument();
  });
});

describe('FichaControl', () => {
  it('cuenta los dos lados del cambio, no solo uno', () => {
    render(<FichaControl clave="umbralVeto" />);

    fireEvent.click(screen.getByRole('button'));

    expect(screen.getByText('Si lo subo')).toBeInTheDocument();
    expect(screen.getByText('Si lo bajo')).toBeInTheDocument();
  });

  it('advierte de la consecuencia de desactivar el veto', () => {
    render(<FichaControl clave="vetoHabilitado" />);

    fireEvent.click(screen.getByRole('button'));

    expect(screen.getByText('Cuidado')).toBeInTheDocument();
    expect(screen.getByText(/única protección/)).toBeInTheDocument();
  });
});

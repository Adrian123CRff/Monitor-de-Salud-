import { useEffect, useId, useRef, useState, type ReactNode } from 'react';

interface Props {
  /** Qué explica este botón. Va en el aria-label, para que un lector de pantalla no oiga solo "i". */
  titulo: string;
  children: ReactNode;
}

/**
 * Botón "i" con una ficha explicativa.
 *
 * La ayuda está oculta por defecto a propósito. Esta es una pantalla de
 * vigilancia: si el texto estuviera siempre visible competiría con los números,
 * que es lo que hay que leer primero. De hecho parte del contenido de estas
 * fichas salió de notas que antes ocupaban espacio permanente en pantalla.
 *
 * Accesibilidad, no opcional:
 * - Es un <button> real, no un <span> con onClick: se alcanza con Tab y
 *   responde a Enter y Espacio sin que haya que programarlo.
 * - aria-expanded le dice a un lector de pantalla si está abierto.
 * - Escape cierra y devuelve el foco al botón, o quien navega con teclado
 *   queda perdido al fondo del documento.
 * - Un clic fuera cierra, que es lo que cualquiera espera de un popover.
 */
export function Ayuda({ titulo, children }: Props) {
  const [abierto, setAbierto] = useState(false);
  const contenedor = useRef<HTMLSpanElement>(null);
  const boton = useRef<HTMLButtonElement>(null);
  const id = useId();

  useEffect(() => {
    if (!abierto) return;

    const alPresionar = (e: KeyboardEvent) => {
      if (e.key === 'Escape') {
        setAbierto(false);
        boton.current?.focus();
      }
    };
    const alHacerClic = (e: MouseEvent) => {
      if (!contenedor.current?.contains(e.target as Node)) {
        setAbierto(false);
      }
    };

    document.addEventListener('keydown', alPresionar);
    document.addEventListener('mousedown', alHacerClic);
    return () => {
      document.removeEventListener('keydown', alPresionar);
      document.removeEventListener('mousedown', alHacerClic);
    };
  }, [abierto]);

  return (
    <span className="ayuda" ref={contenedor}>
      <button
        ref={boton}
        type="button"
        className="ayuda-boton"
        aria-label={`Qué es ${titulo}`}
        aria-expanded={abierto}
        aria-controls={abierto ? id : undefined}
        onClick={() => setAbierto((a) => !a)}
      >
        i
      </button>
      {abierto && (
        <span className="ayuda-ficha" id={id} role="note">
          <span className="ayuda-titulo">{titulo}</span>
          {children}
        </span>
      )}
    </span>
  );
}

/** Un bloque de la ficha: un rótulo corto y su texto. */
export function AyudaBloque({ rotulo, children }: { rotulo: string; children: ReactNode }) {
  return (
    <span className="ayuda-bloque">
      <span className="ayuda-rotulo">{rotulo}</span>
      <span>{children}</span>
    </span>
  );
}

import { useState } from 'react';
import { DashboardInstancia } from './componentes/DashboardInstancia';
import { VistaInstancias } from './componentes/VistaInstancias';

interface Seleccion {
  id: number;
  alias: string;
}

/**
 * Pedido del profesor: un dashboard principal con todas las bases de datos
 * (VistaInstancias), donde clic en una entra a su dashboard de detalle
 * (DashboardInstancia, el contenido que antes vivía directo en App). Estado
 * simple en vez de una librería de router -- no hay ninguna instalada y no
 * hace falta para 2 pantallas (ver docs/plan-trabajo-pendiente.md módulo F).
 *
 * No sincroniza con la URL: el botón "atrás" del navegador no vuelve a la
 * vista general todavía. Aceptado por ahora -- se puede agregar después con
 * history.pushState si hace falta, sin traer una librería nueva.
 */
export function App() {
  const [seleccion, setSeleccion] = useState<Seleccion | null>(null);

  if (!seleccion) {
    return <VistaInstancias onSeleccionar={(id, alias) => setSeleccion({ id, alias })} />;
  }

  return (
    <DashboardInstancia
      key={seleccion.id}
      instanciaId={seleccion.id}
      alias={seleccion.alias}
      onVolver={() => setSeleccion(null)}
    />
  );
}

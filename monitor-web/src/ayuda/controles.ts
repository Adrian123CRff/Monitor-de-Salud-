import type { AyudaControl } from './tipos';

/**
 * Fichas de los controles que el usuario puede cambiar.
 *
 * Aquí la pregunta no es "qué significa" sino "qué pasa si lo toco", y hay que
 * contar los DOS lados: un control solo se entiende sabiendo qué se pierde en
 * cada dirección. Una ayuda que solo diga "sube la sensibilidad" invita a
 * subirlo sin saber qué cuesta.
 *
 * Es la pantalla con más riesgo del sistema: se puede deshabilitar el veto sin
 * que nada avise de que con eso el índice global vuelve a poder esconder un
 * componente crítico.
 */
export const AYUDA_CONTROLES: Record<string, AyudaControl> = {
  pesos: {
    titulo: 'Pesos de los componentes',
    que: 'Cuánto influye cada componente en el índice global. Los tres deben sumar 1.0. Hoy son 30 % procesos, 35 % memoria y 35 % archivos, los valores que propone el documento del proyecto.',
    siSubo: 'Subir el peso de un componente hace que sus problemas muevan más el índice global. Útil si en esta base concreta ese componente es el que más falla.',
    siBajo: 'Bajarlo hace que sus problemas casi no se noten en el índice. Un componente con peso muy bajo puede estar mal sin que la cifra global lo refleje.',
    cuidado: 'Estos pesos todavía no están justificados con datos: vienen del documento de diseño. Cambiarlos "a ojo" no los mejora, solo los cambia. Lo defendible es fijarlos con un análisis de sensibilidad o comparación por pares.',
  },
  umbralVeto: {
    titulo: 'Umbral de veto',
    que: 'Por debajo de esta puntuación, un componente fuerza el estado global a CRÍTICO sin importar lo que diga el promedio.',
    siSubo: 'El sistema se vuelve más desconfiado: un componente apenas mediocre ya pone todo en crítico. Riesgo de alarmas frecuentes que la gente termina ignorando.',
    siBajo: 'El sistema se vuelve más tolerante: un componente puede estar bastante mal y el promedio de los otros dos taparlo. Es exactamente el problema que el veto existe para evitar.',
    cuidado: 'El valor actual coincide con el inicio del tramo Crítico de la escala. Moverlo desalinea las dos cosas: un componente podría mostrarse "Crítico" en su tarjeta y aun así no vetar el índice global.',
  },
  vetoHabilitado: {
    titulo: 'Veto absoluto habilitado',
    que: 'Activa o desactiva por completo la regla de veto. Con el veto activo, un componente muy malo fuerza el estado global a crítico.',
    siSubo: 'Activado (lo recomendado): el índice global nunca puede mostrarse verde mientras un componente está en rojo, y se explica qué lo causó.',
    siBajo: 'Desactivado: el estado global pasa a ser solo el promedio ponderado.',
    cuidado: 'Desactivarlo elimina la única protección contra un índice que esconde un problema. Con procesos en 95, memoria en 91 y archivos en 98, el promedio da 94 y la pantalla se vería verde mientras la base se cae. Ese es el caso que el enunciado plantea en su sección 20.',
  },
};

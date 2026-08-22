import { AYUDA_CONCEPTOS } from '../ayuda/conceptos';
import { AYUDA_CONTROLES } from '../ayuda/controles';
import { ayudaDeVariable } from '../ayuda/variables';
import { Ayuda, AyudaBloque } from './Ayuda';

/**
 * Las tres formas de ficha, una por tipo de pregunta. Envuelven <Ayuda> para
 * que conectarlas en cada pantalla sea una sola línea.
 */

interface PropsVariable {
  variable: string;
  /** Límites vigentes, tal como los manda el backend. Nunca se escriben aquí. */
  valorOk?: number | null;
  valorCritico?: number | null;
  pesoEnComponente?: number;
}

/**
 * Ficha de una variable medida.
 *
 * Devuelve null cuando no hay texto escrito para esa variable: un botón de
 * ayuda que al abrirse no explica nada es peor que no tener botón.
 */
export function FichaVariable({ variable, valorOk, valorCritico, pesoEnComponente }: PropsVariable) {
  const ficha = ayudaDeVariable(variable);
  if (!ficha) return null;

  const hayBanda = valorOk != null && valorCritico != null;

  return (
    <Ayuda titulo={ficha.titulo}>
      <AyudaBloque rotulo="Qué mide">{ficha.que}</AyudaBloque>
      <AyudaBloque rotulo="Por qué importa">{ficha.porQue}</AyudaBloque>
      <AyudaBloque rotulo="Si está en rojo">{ficha.siFalla}</AyudaBloque>
      {ficha.trampa && (
        <span className="ayuda-bloque ayuda-trampa">
          <span className="ayuda-rotulo">Ojo</span>
          <span>{ficha.trampa}</span>
        </span>
      )}

      {hayBanda && (
        <AyudaBloque rotulo="Límite vigente">
          {/* El umbral llega por props: si alguien recalibra desde la pantalla
              de calibración, esta frase cambia sola. Un número escrito aquí
              quedaría mintiendo. */}
          {valorCritico > valorOk
            ? `Sano hasta ${valorOk}, crítico desde ${valorCritico}.`
            : `Sano desde ${valorOk}, crítico en ${valorCritico} o menos.`}{' '}
          <em>Valor de diseño, todavía sin calibrar con datos reales de esta base.</em>
        </AyudaBloque>
      )}

      {pesoEnComponente != null && (
        <AyudaBloque rotulo="Peso">
          Aporta {Math.round(pesoEnComponente * 100)} % de la nota de su componente.
        </AyudaBloque>
      )}

      <span className="ayuda-origen">{ficha.origen}</span>
    </Ayuda>
  );
}

/** Ficha de un concepto del método (ISBD, veto, episodios de alerta...). */
export function FichaConcepto({ clave }: { clave: keyof typeof AYUDA_CONCEPTOS }) {
  const ficha = AYUDA_CONCEPTOS[clave];
  if (!ficha) return null;

  return (
    <Ayuda titulo={ficha.titulo}>
      <AyudaBloque rotulo="Qué es">{ficha.que}</AyudaBloque>
      <AyudaBloque rotulo="Cómo funciona">{ficha.como}</AyudaBloque>
      <AyudaBloque rotulo="Por qué así">{ficha.porQueAsi}</AyudaBloque>
    </Ayuda>
  );
}

/** Ficha de un control editable: qué pasa si lo toco, en las dos direcciones. */
export function FichaControl({ clave }: { clave: keyof typeof AYUDA_CONTROLES }) {
  const ficha = AYUDA_CONTROLES[clave];
  if (!ficha) return null;

  return (
    <Ayuda titulo={ficha.titulo}>
      <AyudaBloque rotulo="Qué controla">{ficha.que}</AyudaBloque>
      <AyudaBloque rotulo="Si lo subo">{ficha.siSubo}</AyudaBloque>
      <AyudaBloque rotulo="Si lo bajo">{ficha.siBajo}</AyudaBloque>
      {ficha.cuidado && (
        <span className="ayuda-bloque ayuda-trampa">
          <span className="ayuda-rotulo">Cuidado</span>
          <span>{ficha.cuidado}</span>
        </span>
      )}
    </Ayuda>
  );
}

/**
 * Ayuda contextual: la prosa vive en el frontend, los números vienen del
 * backend.
 *
 * Esa separación no es casual. Un umbral escrito aquí quedaría mintiendo en
 * cuanto alguien recalibre desde la pantalla de calibración; por eso las
 * fichas de variable reciben `valorOk`/`valorCritico` por props y nunca los
 * escriben. Lo que sí vive aquí es el texto explicativo, que es copy de
 * interfaz y no lógica de negocio: el dominio no debe cargar cadenas de UI.
 *
 * El lector objetivo es alguien que NO sabe de Oracle. El nombre técnico y la
 * vista V$ van al final de la ficha, como dato para quien lo necesite, nunca
 * como la explicación principal.
 */

/** Una variable medida (p1-p8, m1-m10, a1-a8 y las derivadas que puntúan). */
export interface AyudaVariable {
  /** Nombre legible, para no obligar a leer `a6_min_miembros_grupo`. */
  titulo: string;
  /** ¿Qué mide? Una frase, sin jerga. */
  que: string;
  /** ¿Por qué importa? Qué se rompe si esto va mal. */
  porQue: string;
  /** ¿Qué hago si está en rojo? El primer paso de diagnóstico. */
  siFalla: string;
  /**
   * Cómo se malinterpreta. Es el campo de mayor valor y el que más se olvida:
   * "más memoria usada" o "muchas sesiones inactivas" suenan a problema y no
   * lo son. Opcional porque no toda variable tiene una trampa conocida.
   */
  trampa?: string;
  /** De dónde sale el dato. Se muestra en letra chica, al final. */
  origen: string;
}

/** Un concepto del método: ISBD, veto, episodio de alerta, histéresis... */
export interface AyudaConcepto {
  titulo: string;
  que: string;
  /** Cómo se calcula, o cuándo ocurre. */
  como: string;
  /**
   * Por qué se diseñó así. Deliberado: es la respuesta a "¿y por qué no lo
   * hicieron de la forma obvia?", que es exactamente lo que se pregunta en una
   * defensa.
   */
  porQueAsi: string;
}

/**
 * Un control que el usuario puede cambiar. Aquí la pregunta no es qué
 * significa sino qué pasa si lo toco, y hay que contar los DOS lados: un
 * control solo se entiende sabiendo qué se pierde en cada dirección.
 */
export interface AyudaControl {
  titulo: string;
  que: string;
  siSubo: string;
  siBajo: string;
  /** La consecuencia que nadie anticipa. Se muestra destacada. */
  cuidado?: string;
}

import type { AyudaConcepto } from './tipos';

/**
 * Fichas de los conceptos del método, no de las variables medidas.
 *
 * El campo `porQueAsi` es deliberado: responde "¿y por qué no lo hicieron de la
 * forma obvia?", que es exactamente lo que se pregunta cuando alguien evalúa el
 * sistema. Traducido desde el glosario del proyecto y los ADR.
 */
export const AYUDA_CONCEPTOS: Record<string, AyudaConcepto> = {
  isbd: {
    titulo: 'Índice de Salud (ISBD)',
    que: 'Un solo número de 0 a 100 que resume si la base de datos está en condiciones de operar. 100 es perfecto, 0 es crítico.',
    como: 'Combina los tres indicadores —procesos, memoria y archivos— con un peso cada uno. Hoy: 30 % procesos, 35 % memoria, 35 % archivos.',
    porQueAsi: 'Que una base responda no significa que esté sana: puede estar respondiendo con un tablespace al 97 % y caerse en dos horas. El índice existe para que alguien que no es especialista pueda mirar una cifra y saber si hay que actuar.',
  },
  escala: {
    titulo: 'Escala de estados',
    que: 'Los cinco tramos en que se traduce el número: Óptimo (90-100), Saludable (75-89), Advertencia (60-74), Degradado (40-59) y Crítico (por debajo de 40).',
    como: 'Se aplica igual al índice global y a cada indicador por separado, así que un 85 significa lo mismo en cualquier parte de la pantalla.',
    porQueAsi: 'Un número suelto no dice si hay que actuar. Los tramos convierten "82.5" en "saludable, sin acción inmediata", que es lo que alguien necesita para decidir.',
  },
  ip: {
    titulo: 'Indicador de Procesos (IP)',
    que: 'Qué tan bien está manejando la base sus conexiones y sus procesos internos.',
    como: 'Combina dos cosas: los procesos de usuario (cuántas conexiones hay contra el tope, si hay bloqueos) y los procesos internos de Oracle, que pesan más porque sin ellos la base no funciona.',
    porQueAsi: 'Se separan porque son problemas distintos. Muchas conexiones es un problema de capacidad que se resuelve con configuración; un proceso interno caído es un fallo grave. Mezclarlos en un solo promedio escondería el segundo detrás del primero.',
  },
  im: {
    titulo: 'Indicador de Memoria (IM)',
    que: 'Si la base tiene memoria suficiente para trabajar sin recurrir al disco.',
    como: 'Mide presión, no ocupación: cuántas veces la memoria de trabajo no alcanzó y cuántas operaciones terminaron usando disco temporal.',
    porQueAsi: 'Es el error más común al monitorear Oracle. La base está diseñada para llenar su memoria de caché — que esté llena es señal de que funciona bien. Un monitor que penalice "memoria muy usada" grita cuando todo está perfecto.',
  },
  ia: {
    titulo: 'Indicador de Archivos (IA)',
    que: 'Si la base tiene espacio y si sus archivos están todos accesibles.',
    como: 'Mira el espacio del tablespace más lleno, los archivos que estén fuera de línea o dañados, y si el registro de cambios tiene copia de respaldo.',
    porQueAsi: 'Quedarse sin espacio es la causa más común de que una base se detenga, y también la más evitable: se ve venir con horas de anticipación si alguien está mirando.',
  },
  veto: {
    titulo: 'Veto absoluto',
    que: 'Una regla que fuerza el estado a CRÍTICO cuando un componente está muy mal, sin importar lo que diga el promedio.',
    como: 'Si un componente cae por debajo del umbral configurado, o si ocurre algo que no admite grados (un archivo de datos fuera de línea, un proceso interno caído), el estado global pasa a crítico y se explica la causa.',
    porQueAsi: 'Sin esto, el promedio miente. Con procesos en 95, memoria en 91 y archivos en 98, el promedio da 94 y el semáforo se pondría verde mientras la base se cae. Es el problema que el enunciado plantea en su sección 20, resuelto como mecanismo y no como advertencia en un documento.',
  },
  parcial: {
    titulo: 'Cálculo parcial',
    que: 'Marca que uno o más componentes no se pudieron leer en ese ciclo de medición.',
    como: 'El componente que faltó se excluye del cálculo y su peso se reparte entre los demás. Si solo se pudo leer uno de los tres, el estado se topa en Advertencia.',
    porQueAsi: 'Un componente que no se pudo leer no vale cero: "no sé" no es lo mismo que "está mal". Contarlo como cero produciría una alarma falsa cada vez que hay un corte de red.',
  },
  vetusto: {
    titulo: 'Dato desactualizado',
    que: 'Avisa que la última medición es más vieja de lo que debería, así que lo que se ve en pantalla puede no reflejar la realidad.',
    como: 'Se marca cuando pasaron más de tres ciclos de medición sin un dato nuevo.',
    porQueAsi: 'Es el peor modo de fallo de un monitor: si deja de medir en silencio, la pantalla sigue mostrando el último dato bueno y todo parece tranquilo. Un monitor que no sabe que dejó de mirar es peor que no tener monitor.',
  },
  alertas: {
    titulo: 'Alertas',
    que: 'Condiciones concretas que se registran con su fecha de apertura y de cierre, no notificaciones sueltas.',
    como: 'Cada alerta es un episodio: se abre cuando la condición aparece, puede cambiar de nivel mientras dura, y se cierra cuando se resuelve. Una condición que dura seis horas es UNA fila, no cientos.',
    porQueAsi: 'Un monitor que avisa en cada medición enseña a la gente a ignorarlo. Además, con episodios se puede responder "¿cuánto duró?" y "¿cuántas veces pasó este mes?", que es lo que sirve para decidir.',
  },
  histeresis: {
    titulo: 'Histéresis y confirmación',
    que: 'Dos mecanismos que evitan que una alerta se encienda y apague sola.',
    como: 'La histéresis usa un límite para abrir y otro más bajo para cerrar, así un valor oscilando alrededor del corte no parpadea. La confirmación exige que la condición aparezca en varias mediciones seguidas antes de abrir el episodio.',
    porQueAsi: 'Sin esto, una variable ruidosa que baila alrededor del umbral genera decenas de alertas por hora. La confirmación solo aplica al disparo inicial: una vez abierto el episodio, empeorar se refleja de inmediato.',
  },
  tablespaces: {
    titulo: 'Tablespaces',
    que: 'Los espacios de almacenamiento donde viven las tablas. Cada uno tiene un tope de crecimiento.',
    como: 'Se ordenan del más lleno al menos lleno. El porcentaje es contra el tamaño máximo al que puede crecer, no contra el actual.',
    porQueAsi: 'Se muestra el peor y no el promedio porque promediar esconde el problema: doce al 40 % y uno al 99 % dan 45 % y suenan tranquilos. Y no aparecen los espacios temporales ni los de deshacer, que rutinariamente marcan casi lleno sin que eso signifique nada malo.',
  },
  historico: {
    titulo: 'Evolución en el tiempo',
    que: 'Cómo se movieron el índice y sus tres componentes a lo largo del tiempo.',
    como: 'Cada punto es una medición guardada. Las cuatro líneas usan la misma escala de 0 a 100.',
    porQueAsi: 'El estado actual no distingue "siempre estuvo así" de "empeoró en la última hora", y son situaciones muy distintas. La tendencia también es lo que permite anticipar: un tablespace que sube dos puntos por día avisa con semanas.',
  },
  vistaGeneral: {
    titulo: 'Vista general',
    que: 'Una tarjeta por cada base de datos monitoreada, con su estado de salud actual.',
    como: 'Se ordenan por gravedad, peor primero. Al hacer clic en una se entra a su detalle completo.',
    porQueAsi: 'En una pantalla de vigilancia lo que está mal va arriba: quien la mira tiene poco tiempo. Una base recién agregada, sin mediciones todavía, aparece como "sin datos" y va al final — nunca se le inventa un semáforo.',
  },
  desglose: {
    titulo: 'Qué está puntuando',
    que: 'Cuánto aporta cada variable a la nota del componente, y cuántos puntos le está restando.',
    como: 'Se ordena por lo que cada variable le cuesta al componente, así que la responsable del problema queda siempre arriba.',
    porQueAsi: 'Saber que archivos marca 90 no sirve de nada si no se sabe por qué. Esta tabla convierte "tu salud es 90" en "tu salud es 90 porque la bitácora no tiene copia de respaldo", que ya es algo sobre lo que se puede actuar.',
  },
  crudo: {
    titulo: 'Dato crudo de Oracle',
    que: 'Los valores tal como se leyeron de la base, sin interpretar.',
    como: 'Incluye tanto las variables que puntúan como las de contexto, que se recolectan y guardan pero no afectan la nota.',
    porQueAsi: 'Permite verificar de dónde salió cada puntuación en lugar de tener que confiar en ella. Los valores sin color son los de contexto: no están ni bien ni mal, y pintarlos sería inventar una evaluación que nadie hizo.',
  },
};

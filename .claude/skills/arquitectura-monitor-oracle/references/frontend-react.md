# Frontend React

## Estructura

```
monitor-web/
├── package.json
├── vite.config.ts
├── tsconfig.json
└── src/
    ├── main.tsx
    ├── App.tsx
    ├── api/
    │   ├── cliente.ts          fetch tipado + manejo de ProblemDetail
    │   └── tipos.ts            tipos que reflejan los DTO del backend
    ├── hooks/
    │   ├── useSalud.ts         polling del estado actual
    │   ├── useHistorico.ts     serie temporal
    │   └── useAlertas.ts
    ├── componentes/
    │   ├── IndiceGlobal.tsx    el número grande + estado
    │   ├── TarjetaComponente.tsx   IP / IM / IA
    │   ├── PanelAlertas.tsx
    │   ├── GraficoHistorico.tsx
    │   └── TablaTablespaces.tsx
    └── estilos/
```

## El contrato con el backend

Define los tipos primero; son el contrato y hacen de documentación viva:

```typescript
// src/api/tipos.ts
export type Estado = 'OPTIMO' | 'SALUDABLE' | 'ADVERTENCIA' | 'DEGRADADO' | 'CRITICO';
export type Componente = 'PROCESOS' | 'MEMORIA' | 'ARCHIVOS';
export type Nivel = 'ADVERTENCIA' | 'ALTO' | 'CRITICO';

export interface Indicador {
  componente: Componente;
  puntuacion: number;              // 0-100, salud
  puntuacionesPorVariable: Record<string, number>;
}

export interface Salud {
  instanciaId: number;
  momento: string;                 // ISO-8601
  isbd: number;
  estado: Estado;
  estadoPorVeto: boolean;
  causas: string[];
  ip: Indicador;
  im: Indicador;
  ia: Indicador;
}

export interface Alerta {
  id: number;
  abiertaEn: string;
  cerradaEn: string | null;
  componente: Componente;
  variable: string;
  entidad: string | null;          // 'USERS' para un tablespace concreto
  valor: number;
  umbral: number;
  nivel: Nivel;
  descripcion: string;
}
```

`estadoPorVeto` y `causas` no son adorno: son lo que permite que la interfaz
explique por qué el índice dice 78 y el estado dice CRÍTICO. Sin esos dos campos,
el dashboard se contradice a sí mismo delante del usuario y parece un bug.

## Cliente

```typescript
// src/api/cliente.ts
const BASE = import.meta.env.VITE_API_URL ?? '/api/v1';

class ErrorApi extends Error {
  constructor(readonly status: number, readonly detail: string) {
    super(detail);
  }
}

async function get<T>(ruta: string): Promise<T> {
  const res = await fetch(`${BASE}${ruta}`, {
    headers: { Accept: 'application/json' },
  });
  if (!res.ok) {
    // El backend responde ProblemDetail (RFC 9457)
    const problema = await res.json().catch(() => ({ detail: res.statusText }));
    throw new ErrorApi(res.status, problema.detail ?? 'Error desconocido');
  }
  return res.json() as Promise<T>;
}

export const api = {
  salud:      (id: number) => get<Salud>(`/instancias/${id}/salud`),
  historico:  (id: number, desde: string, hasta: string) =>
                get<PuntoHistorico[]>(`/instancias/${id}/salud/historico?desde=${desde}&hasta=${hasta}`),
  alertas:    (id: number, soloAbiertas = true) =>
                get<Alerta[]>(`/instancias/${id}/alertas?abiertas=${soloAbiertas}`),
  tablespaces:(id: number) => get<Tablespace[]>(`/instancias/${id}/tablespaces`),
};
```

## Actualización: polling con backoff

Un dashboard de monitoreo tiene que refrescarse solo. El polling simple es lo
correcto aquí — WebSockets o SSE serían más elegantes y añaden complejidad que este
proyecto no necesita, y esa es una decisión que conviene documentar como ADR en
lugar de dejarla implícita.

Lo que sí importa es el comportamiento ante fallos:

```typescript
export function useSalud(instanciaId: number, intervaloMs = 30_000) {
  const [salud, setSalud] = useState<Salud | null>(null);
  const [error, setError] = useState<string | null>(null);
  const fallosRef = useRef(0);

  useEffect(() => {
    let cancelado = false;
    let timer: number;

    const consultar = async () => {
      try {
        const datos = await api.salud(instanciaId);
        if (cancelado) return;
        setSalud(datos);
        setError(null);
        fallosRef.current = 0;
        timer = window.setTimeout(consultar, intervaloMs);
      } catch (e) {
        if (cancelado) return;
        fallosRef.current += 1;
        setError(e instanceof Error ? e.message : 'Error de conexión');
        // Backoff exponencial con techo: no martillees un backend caído
        const espera = Math.min(intervaloMs * 2 ** fallosRef.current, 300_000);
        timer = window.setTimeout(consultar, espera);
      }
    };

    consultar();
    return () => { cancelado = true; clearTimeout(timer); };
  }, [instanciaId, intervaloMs]);

  return { salud, error, desconectado: fallosRef.current > 2 };
}
```

Dos cosas que suelen faltar y aquí importan especialmente:

**El backoff exponencial.** Si el backend cae, un polling fijo cada 30 s desde
varias pestañas abiertas le añade carga justo cuando está en problemas.

**El estado `desconectado`.** Es la diferencia entre un dashboard honesto y uno que
miente. Sin él, cuando la conexión se pierde la interfaz sigue mostrando el último
valor conocido con aspecto de dato fresco. Un monitor que muestra "verde, todo
bien" cuando en realidad perdió contacto hace veinte minutos es peor que no tener
monitor: transmite una seguridad falsa. La interfaz debe atenuar los datos, mostrar
la antigüedad ("hace 12 min") y decir explícitamente que no hay conexión.

## Diseño del dashboard

La propuesta original ya define el layout, y es correcto. La jerarquía visual debe
reflejar la jerarquía de la información:

```
┌───────────────────────────────────────────────────┐
│  MONITOR DE SALUD — ORACLE            ● conectado │
│                                                   │
│                     82.75                         │  ← lo más grande
│                   SALUDABLE                       │
│                                        hace 14 s  │  ← siempre visible
├─────────────────┬─────────────────┬───────────────┤
│    PROCESOS     │     MEMORIA     │   ARCHIVOS    │
│       82        │       74        │      91       │
│        ●        │        ●        │       ●       │
├─────────────────┴─────────────────┴───────────────┤
│  ALERTAS ABIERTAS                                 │
│  ● Tablespace USERS al 93 % (umbral 90 %)  6h 12m │
│  ● PGA con sobreasignación: 4 eventos/min    18m  │
├───────────────────────────────────────────────────┤
│  EVOLUCIÓN — últimas 24 h                         │
│  [gráfico de líneas: ISBD, IP, IM, IA]            │
└───────────────────────────────────────────────────┘
```

Cuatro criterios de diseño:

**La antigüedad del dato siempre visible.** "hace 14 s" es tan importante como el
82.75. Un número sin edad no se puede interpretar.

**Las alertas muestran su duración, no su hora de inicio.** "6h 12m" comunica
gravedad de inmediato; "abierta a las 03:14" obliga a calcular mentalmente.

**El color nunca es la única señal.** Entre el 5 y el 8 % de los hombres tiene algún
tipo de daltonismo, y verde/rojo es justo el par problemático. Acompaña siempre el
color con texto (`SALUDABLE`, `CRÍTICO`) y forma de icono distinta. Es accesibilidad
básica y es un punto que suma en la evaluación porque casi nadie lo hace.

**Cuando el estado viene de un veto, dilo en la cara.** Si `estadoPorVeto` es true,
el bloque del índice global debe mostrar las `causas` junto al número. Es la regla
más importante del sistema; esconderla en un tooltip la desperdicia.

## Gráfico histórico

Recharts es más simple; Apache ECharts maneja mejor series largas (miles de puntos)
y trae zoom temporal de fábrica. Con 24 h de datos cada 30 s son 2 880 puntos por
serie y cuatro series: ECharts es la elección más segura, y el zoom por rango es
justo lo que hace útil un gráfico de monitoreo.

Agrega en el backend, no en el navegador. Un endpoint con parámetro `granularidad`
(`raw`, `1m`, `5m`, `1h`) que devuelva promedios y máximos por intervalo evita
mandar decenas de miles de puntos por la red para dibujar 400 píxeles de ancho.

En un gráfico de salud, el **mínimo** del intervalo suele importar más que el
promedio: un bajón de 30 segundos a ISBD 20 desaparece completamente en un promedio
horario, y es exactamente el evento que interesa ver. Devuelve `min`, `avg` y `max`
por punto y dibuja el promedio como línea con una banda min–max detrás.

## Configuración de Vite

```typescript
// vite.config.ts
export default defineConfig({
  plugins: [react()],
  server: {
    proxy: {
      // Evita CORS en desarrollo: el front llama a /api y Vite lo reenvía
      '/api': { target: 'http://localhost:8080', changeOrigin: true },
    },
  },
  build: { outDir: 'dist', sourcemap: true },
});
```

El proxy de desarrollo es la forma limpia de no pelear con CORS mientras
desarrollas. Para la entrega tienes dos opciones, y conviene elegir conscientemente:

- **Desplegar por separado** (front en un servidor estático, back en otro puerto).
  Requiere configurar CORS en Spring. Es la arquitectura más realista.
- **Empaquetar el front dentro del jar** (`maven-resources-plugin` copiando `dist/`
  a `src/main/resources/static/`). Un solo artefacto, un solo puerto, cero CORS.
  Para una demostración en clase, esto es notablemente más simple y menos frágil.

Para este proyecto la segunda opción suele ganar: menos piezas que puedan fallar el
día de la presentación. Documenta la decisión con un ADR y menciona la primera
opción como la ruta de despliegue real — muestra que la elección fue informada y no
por desconocimiento.

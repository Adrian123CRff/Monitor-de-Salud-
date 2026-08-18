package cr.ac.una.monitor.dominio.agregacion;

import cr.ac.una.monitor.dominio.calibracion.Calibracion;
import cr.ac.una.monitor.dominio.modelo.Componente;
import cr.ac.una.monitor.dominio.modelo.Estado;
import cr.ac.una.monitor.dominio.modelo.Indicador;
import cr.ac.una.monitor.dominio.modelo.Isbd;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Combina IP, IM e IA en el ISBD. ADR 0003: media aritmética ponderada,
 * con un veto que fuerza el estado a CRITICO si algún componente presente
 * cae por debajo del umbral configurado, sin importar el promedio.
 *
 * Un componente ausente (Optional.empty(), cuando su recolección falló --
 * ver RecoleccionFallidaException / MuestrearInstanciaServicio.recolectarSeguro)
 * se excluye del promedio: su peso se redistribuye entre los presentes,
 * igual que CalculadorComponente hace con variables faltantes dentro de un
 * componente. No participa en el veto tampoco -- "no sé" no es lo mismo
 * que "está mal". El ISBD queda marcado parcial=true y la ausencia se
 * anota en causas para que no pase inadvertida.
 *
 * Los vetos absolutos de agregacion.md (datafile en RECOVER, datafile
 * OFFLINE, miembro de redo INVALID, tablespace >= 98 %) ya no viven aquí:
 * se resuelven un nivel más abajo, en CalculadorComponente, forzando la
 * puntuación del componente entero a 0. El veto de componente de aquí
 * (puntuación < umbralVetoComponente) los atrapaba en la mayoría de los
 * casos, pero no siempre: cuando el Indicador viene de combinar
 * sub-indicadores (IP_usuarios + IP_fondo, ver CombinadorSubIndicadores),
 * un 0 vetado se puede diluir en un promedio que cae por encima del
 * umbral según los pesos -- verificado en vivo con la calibración por
 * defecto (IP=40.0 exacto contra un umbral de 40.0, comparación estricta,
 * no vetaba). Por eso el chequeo de abajo mira primero i.vetado()
 * (propagado explícitamente) y solo cae al umbral numérico si no lo está.
 *
 * "Fallo de recolección sostenido" (el otro veto absoluto de agregacion.md)
 * es distinto de lo que hace MuestrearInstanciaServicio.recolectarSeguro:
 * un solo fallo puntual NO veta aquí, se excluye del promedio (ver
 * Isbd.parcial) -- es una decisión de diseño explícita, no un olvido:
 * un corte transitorio de red no debería gritar CRITICO. Un fallo
 * *sostenido* (N ciclos seguidos) sí debería, pero esa lógica de conteo
 * todavía no existe.
 *
 * Con solo un componente presente (los otros dos fallaron el mismo ciclo),
 * el ISBD es literalmente la puntuación de ese único componente -- si
 * resulta sano, el estado sale OPTIMO con dos tercios del sistema
 * ilegibles, y "parcial=true" es la única señal de que algo anda mal (fácil
 * de pasar por alto si solo se mira el semáforo). Encontrado por auditoría
 * externa (ver docs/): el estado se topa en ADVERTENCIA como máximo en ese
 * caso -- no CRITICO (sería alarmismo si el único componente que sí
 * respondió está perfecto), pero tampoco un semáforo verde sin matices.
 */
public final class MotorIndicadores {

    public Isbd calcular(Instant momento, Optional<Indicador> ip, Optional<Indicador> im,
            Optional<Indicador> ia, Calibracion cal) {
        Map<Componente, Indicador> presentes = new LinkedHashMap<>();
        List<String> causas = new ArrayList<>();

        ip.ifPresentOrElse(i -> presentes.put(Componente.PROCESOS, i),
            () -> causas.add("PROCESOS: fallo de recolección, excluido del cálculo"));
        im.ifPresentOrElse(i -> presentes.put(Componente.MEMORIA, i),
            () -> causas.add("MEMORIA: fallo de recolección, excluido del cálculo"));
        ia.ifPresentOrElse(i -> presentes.put(Componente.ARCHIVOS, i),
            () -> causas.add("ARCHIVOS: fallo de recolección, excluido del cálculo"));

        if (presentes.isEmpty()) {
            throw new IllegalStateException(
                "No se pudo recolectar ningún componente en este ciclo -- nada que calcular.");
        }

        double puntuacion = aritmeticaPonderada(presentes, cal.pesos());
        boolean parcial = presentes.size() < Componente.values().length;

        boolean vetado = false;
        if (cal.vetoHabilitado()) {
            for (Indicador i : presentes.values()) {
                if (i.vetado()) {
                    causas.add("%s vetado (una variable disparó un veto absoluto, ver detalle del componente)"
                        .formatted(i.componente()));
                    vetado = true;
                } else if (i.puntuacion() < cal.umbralVetoComponente()) {
                    causas.add("%s en %.0f (veto: < %.0f)".formatted(
                        i.componente(), i.puntuacion(), cal.umbralVetoComponente()));
                    vetado = true;
                }
            }
        }

        Estado estado = vetado ? Estado.CRITICO : Estado.desdePuntuacion(puntuacion);
        if (!vetado && presentes.size() <= 1 && estado.ordinal() < Estado.ADVERTENCIA.ordinal()) {
            causas.add("Cobertura insuficiente: solo %s pudo recolectarse este ciclo, estado topado en ADVERTENCIA"
                .formatted(presentes.keySet()));
            estado = Estado.ADVERTENCIA;
        }
        return new Isbd(momento, puntuacion, estado, ip, im, ia, vetado, List.copyOf(causas), parcial);
    }

    private double aritmeticaPonderada(Map<Componente, Indicador> indicadores, Map<Componente, Double> pesos) {
        double sumaPonderada = 0;
        double sumaPesos = 0;
        for (var entrada : indicadores.entrySet()) {
            Double peso = pesos.get(entrada.getKey());
            if (peso == null) {
                throw new IllegalArgumentException(
                    "Falta el peso de " + entrada.getKey() + " en la calibración vigente");
            }
            sumaPonderada += peso * entrada.getValue().puntuacion();
            sumaPesos += peso;
        }
        return sumaPonderada / sumaPesos;
    }
}

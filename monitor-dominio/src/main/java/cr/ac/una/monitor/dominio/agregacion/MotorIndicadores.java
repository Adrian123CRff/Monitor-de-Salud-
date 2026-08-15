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
 * puntuación del componente entero a 0 -- este veto de componente
 * (puntuación < umbralVetoComponente) los atrapa igual que atraparía
 * cualquier otro valor bajo, sin necesitar lógica propia.
 *
 * "Fallo de recolección sostenido" (el otro veto absoluto de agregacion.md)
 * es distinto de lo que hace MuestrearInstanciaServicio.recolectarSeguro:
 * un solo fallo puntual NO veta aquí, se excluye del promedio (ver
 * Isbd.parcial) -- es una decisión de diseño explícita, no un olvido:
 * un corte transitorio de red no debería gritar CRITICO. Un fallo
 * *sostenido* (N ciclos seguidos) sí debería, pero esa lógica de conteo
 * todavía no existe.
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
                if (i.puntuacion() < cal.umbralVetoComponente()) {
                    causas.add("%s en %.0f (veto: < %.0f)".formatted(
                        i.componente(), i.puntuacion(), cal.umbralVetoComponente()));
                    vetado = true;
                }
            }
        }

        Estado estado = vetado ? Estado.CRITICO : Estado.desdePuntuacion(puntuacion);
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

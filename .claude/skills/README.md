# Skills del proyecto Monitor de Salud de Oracle

Estas skills se cargan automáticamente cuando ejecutas Claude Code (terminal)
desde la raíz de este repositorio o desde cualquier subdirectorio.

| Skill | Para qué |
|---|---|
| `monitor-salud-oracle` | Dominio: variables, fórmulas IP/IM/IA/ISBD, esquema MONITOR_* |
| `oracle-vistas-dinamicas` | SQL contra vistas V$, permisos, CDB/PDB, costos |
| `arquitectura-monitor-oracle` | Estructura hexagonal, Maven, ArchUnit, tests, CI |
| `diseno-de-indicadores` | Normalización, calibración, histéresis, agregación, AHP |
| `interrogar-diseno` | Interrogatorio de decisiones antes de implementar (`/interrogar-diseno`) |

Se invocan solas cuando el contexto encaja, o a mano con `/nombre-de-la-skill`.

Para que funcionen también en Cowork (app de escritorio) hay que habilitarlas
en la cuenta de claude.ai: Cowork no lee este directorio ni `~/.claude/skills/`.

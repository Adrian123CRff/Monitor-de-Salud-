# C4 — Nivel 1: Contexto

```mermaid
C4Context
    title Monitor de Salud de Oracle — Contexto

    Person(dba, "DBA / Administrador", "Vigila la salud de varias instancias Oracle 'de clientes' (ADR 0001, ADR 0005)")

    System(monitor, "Monitor de Salud de Oracle", "Recolecta procesos/memoria/archivos, calcula IP/IM/IA/ISBD, alerta y muestra un dashboard")

    System_Ext(oracleA, "Instancia Oracle — cliente A", "Simulada, sana (MVP: 2 instancias)")
    System_Ext(oracleB, "Instancia Oracle — cliente B", "Simulada, con problemas (MVP: 2 instancias)")

    Rel(dba, monitor, "Consulta el dashboard, revisa alertas")
    Rel(monitor, oracleA, "Lee vistas V$ / DBA_*, solo lectura", "JDBC")
    Rel(monitor, oracleB, "Lee vistas V$ / DBA_*, solo lectura", "JDBC")
```

Notas:

- El monitor nunca escribe en las instancias monitoreadas (pool `readOnly=true`,
  ver `references/estructura-backend.md` de la skill de arquitectura).
- El histórico del propio monitor vive en PostgreSQL, no en las instancias
  Oracle observadas — ver ADR 0002. No aparece en este nivel porque es un
  contenedor interno del sistema, no un actor externo; se detalla en el
  diagrama de Nivel 2 (Contenedores, pendiente).

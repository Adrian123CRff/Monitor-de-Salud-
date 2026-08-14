package cr.ac.una.monitor.api;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.library.Architectures.layeredArchitecture;

/**
 * Convierte la regla de dependencia del hexágono (ver referencia de
 * arquitectura) en algo que rompe la compilación si se viola, en vez de en
 * una convención que se erosiona con el tiempo.
 */
@AnalyzeClasses(packages = "cr.ac.una.monitor", importOptions = ImportOption.DoNotIncludeTests.class)
class ArquitecturaTest {

    @ArchTest
    static final ArchRule el_dominio_no_conoce_frameworks =
        noClasses().that().resideInAPackage("..dominio..")
            .should().dependOnClassesThat()
            .resideInAnyPackage("org.springframework..", "jakarta.persistence..",
                                 "com.fasterxml.jackson..", "oracle.jdbc..");

    @ArchTest
    static final ArchRule las_capas_respetan_su_orden =
        layeredArchitecture().consideringOnlyDependenciesInLayers()
            .layer("Dominio").definedBy("..dominio..")
            .layer("Aplicacion").definedBy("..aplicacion..")
            .layer("Infraestructura").definedBy("..infraestructura..")
            .layer("Api").definedBy("..api..")
            .whereLayer("Api").mayNotBeAccessedByAnyLayer()
            .whereLayer("Infraestructura").mayNotBeAccessedByAnyLayer()
            .whereLayer("Aplicacion").mayOnlyBeAccessedByLayers("Api", "Infraestructura")
            .whereLayer("Dominio").mayOnlyBeAccessedByLayers(
                "Aplicacion", "Infraestructura", "Api");

    @ArchTest
    static final ArchRule los_controladores_no_tocan_repositorios =
        noClasses().that().resideInAPackage("..api.rest..")
            .should().dependOnClassesThat().resideInAPackage("..infraestructura.persistencia..");
}

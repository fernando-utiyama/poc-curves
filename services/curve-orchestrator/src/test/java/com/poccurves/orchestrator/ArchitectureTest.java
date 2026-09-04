package com.poccurves.orchestrator;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

/**
 * Guarda de fronteira ports & adapters (ver openspec/changes/hexagonal-architecture): reprova o
 * build se uma classe em domain/application importar tipo de framework de infraestrutura.
 * `org.springframework.transaction..` e `org.springframework.scheduling.support.CronExpression`
 * (usada só para validar sintaxe de expressão cron em {@code Agendamento}, sem wiring de
 * infraestrutura por trás) são exceções deliberadas — ver design.md - Decisions. Jackson é banido
 * por CLASSE de serviço (ObjectMapper/JsonMapper/TypeReference), não por pacote inteiro.
 */
@AnalyzeClasses(packages = "com.poccurves.orchestrator", importOptions = ImportOption.DoNotIncludeTests.class)
class ArchitectureTest {

    @ArchTest
    static final ArchRule domainAndApplicationMustNotDependOnFrameworks =
            noClasses().that().resideInAnyPackage("..domain..", "..application..")
                    .should().dependOnClassesThat().resideInAnyPackage(
                            "org.springframework.stereotype..",
                            "org.springframework.web..",
                            "org.springframework.jdbc..",
                            "org.springframework.data..",
                            "org.springframework.kafka..",
                            "org.springframework.context.annotation..",
                            "org.springframework.boot..",
                            "org.springframework.scheduling.annotation..",
                            "org.springframework.scheduling.concurrent..",
                            "jakarta.persistence..",
                            "org.apache.kafka..")
                    .allowEmptyShould(true);

    @ArchTest
    static final ArchRule domainAndApplicationMustNotDependOnJacksonServiceClasses =
            noClasses().that().resideInAnyPackage("..domain..", "..application..")
                    .should().dependOnClassesThat().haveNameMatching(".*\\.ObjectMapper|.*\\.JsonMapper|.*\\.TypeReference")
                    .allowEmptyShould(true);
}

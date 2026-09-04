package com.poccurves.processor;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

/**
 * Guarda de fronteira ports & adapters (ver openspec/changes/hexagonal-architecture): reprova o
 * build se uma classe em domain/application importar tipo de framework de infraestrutura.
 * `org.springframework.transaction..` é exceção deliberada — demarcação de transação é tratada
 * como concern de caso de uso, não de infraestrutura (ver design.md - Decisions). Jackson é banido
 * por CLASSE de serviço (ObjectMapper/JsonMapper/TypeReference), não por pacote inteiro — este
 * módulo usa `tools.jackson.databind.JsonNode` como estrutura de dado genérica vinda do envelope
 * de evento ({@code EventEnvelope.payload()}, do módulo `common`), não como uso de infraestrutura
 * de serialização (achado real desta migração, ver design.md - Decisions).
 */
@AnalyzeClasses(packages = "com.poccurves.processor", importOptions = ImportOption.DoNotIncludeTests.class)
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
                            "jakarta.persistence..",
                            "org.apache.kafka..")
                    .allowEmptyShould(true);

    @ArchTest
    static final ArchRule domainAndApplicationMustNotDependOnJacksonServiceClasses =
            noClasses().that().resideInAnyPackage("..domain..", "..application..")
                    .should().dependOnClassesThat().haveNameMatching(".*\\.ObjectMapper|.*\\.JsonMapper|.*\\.TypeReference")
                    .allowEmptyShould(true);
}

package com.poccurves.engine;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

/**
 * Guarda de fronteira ports & adapters (ver openspec/changes/hexagonal-architecture): reprova o
 * build se uma classe em application/ (e seus subpacotes — model/service/usecase/validator/port/
 * exception, layout espelhando o padrão hex real usado em outro projeto) importar tipo de
 * framework de infraestrutura.
 * `org.springframework.transaction..` é exceção deliberada — demarcação de transação é tratada
 * como concern de caso de uso, não de infraestrutura (ver design.md - Decisions). Jackson é banido
 * por CLASSE de serviço (ObjectMapper/JsonMapper/TypeReference), não por pacote inteiro.
 */
@AnalyzeClasses(packages = "com.poccurves.engine", importOptions = ImportOption.DoNotIncludeTests.class)
class ArchitectureTest {

    @ArchTest
    static final ArchRule applicationMustNotDependOnFrameworks =
            noClasses().that().resideInAnyPackage("..application..")
                    .should().dependOnClassesThat().resideInAnyPackage(
                            "org.springframework.stereotype..",
                            "org.springframework.web..",
                            "org.springframework.jdbc..",
                            "org.springframework.data..",
                            "org.springframework.kafka..",
                            "org.springframework.context.annotation..",
                            "org.springframework.boot..",
                            "jakarta.persistence..",
                            "org.apache.kafka..")
                    .allowEmptyShould(true);

    @ArchTest
    static final ArchRule applicationMustNotDependOnJacksonServiceClasses =
            noClasses().that().resideInAnyPackage("..application..")
                    .should().dependOnClassesThat().haveNameMatching(".*\\.ObjectMapper|.*\\.JsonMapper|.*\\.TypeReference")
                    .allowEmptyShould(true);
}

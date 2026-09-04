package com.poccurves.common;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

/**
 * Guarda de fronteira (ver openspec/changes/hexagonal-architecture): `common` é uma biblioteca
 * pura consumida por todos os outros módulos — ao contrário deles, não tem separação
 * domain/application/adapter para fazer (não há framework nem I/O aqui hoje, só o envelope de
 * evento e seu validador de schema), então a regra cobre o módulo inteiro, não só subpacotes.
 * Jackson (`JsonNode`/`@JsonInclude`) e o validador de schema (`com.networknt.schema`) ficam de
 * fora do ban: carregar o payload do envelope como estrutura de dado genérica e validar contra o
 * contrato JSON Schema são exatamente a razão de existir deste módulo, não infraestrutura de
 * framework. O que a regra impede é a dependência de Spring vazar para cá — isso obrigaria todo
 * módulo consumidor a herdar Spring transitivamente só por depender de `common`.
 */
@AnalyzeClasses(packages = "com.poccurves.common", importOptions = ImportOption.DoNotIncludeTests.class)
class ArchitectureTest {

    @ArchTest
    static final ArchRule mustNotDependOnSpringFramework =
            noClasses().should().dependOnClassesThat().resideInAnyPackage("org.springframework..")
                    .allowEmptyShould(true);
}

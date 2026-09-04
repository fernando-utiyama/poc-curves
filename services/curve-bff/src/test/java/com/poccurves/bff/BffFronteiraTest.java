package com.poccurves.bff;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class BffFronteiraTest {

    @Test
    void servicoNaoDeveDeclararDependenciasProibidasNoPom() throws IOException {
        Path pomPath = Paths.get("pom.xml");
        if (!Files.exists(pomPath)) {
            pomPath = Paths.get("services/curve-bff/pom.xml");
        }
        assertThat(pomPath).exists();

        String pomContent = Files.readString(pomPath);
        assertThat(pomContent)
                .as("curve-bff MUST NOT declarar dependência de driver de banco ou Kafka")
                .doesNotContain("<artifactId>mssql-jdbc</artifactId>")
                .doesNotContain("<artifactId>spring-boot-starter-jdbc</artifactId>")
                .doesNotContain("<artifactId>spring-boot-starter-data-jpa</artifactId>")
                .doesNotContain("<artifactId>kafka-clients</artifactId>")
                .doesNotContain("<artifactId>spring-kafka</artifactId>");
    }

    @Test
    void servicoNaoDeveImportarClassesDeBancoOuKafkaNoCodigoFonte() throws IOException {
        Path srcPath = Paths.get("src/main/java");
        if (!Files.exists(srcPath)) {
            srcPath = Paths.get("services/curve-bff/src/main/java");
        }
        assertThat(srcPath).exists();

        try (var stream = Files.walk(srcPath)) {
            List<Path> javaFiles = stream.filter(p -> p.toString().endsWith(".java")).toList();
            for (Path javaFile : javaFiles) {
                String content = Files.readString(javaFile);

                assertThat(content)
                        .as("Arquivo %s não deve conter referências a java.sql ou javax.sql", javaFile.getFileName())
                        .doesNotContain("import java.sql")
                        .doesNotContain("import javax.sql")
                        .doesNotContain("import org.apache.kafka")
                        .doesNotContain("import org.springframework.kafka")
                        .doesNotContain("import org.springframework.jdbc");
            }
        }
    }
}

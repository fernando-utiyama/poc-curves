package com.poccurves.api;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * A verificação real de que o serviço não consegue ESCREVER fora da sua
 * fronteira (definicao_curva/versao_definicao_curva) mora em
 * {@link FronteiraEscritaCredencialIT} — contra o banco real, com a
 * credencial real da aplicação. O teste que existia aqui antes
 * (grep de string em código-fonte, nunca tocava o banco) foi removido na
 * auditoria desta sessão por dar falsa confiança: passaria mesmo com o
 * serviço rodando como `sa`, sem nenhuma restrição real.
 */
class FronteiraEscritaTest {

    @Test
    void servicoNaoDeveDeclararDependenciaDeKafka() throws IOException {
        Path pomPath = Paths.get("pom.xml");
        if (!Files.exists(pomPath)) {
            pomPath = Paths.get("services/curve-api/pom.xml");
        }
        assertThat(pomPath).exists();

        String pomContent = Files.readString(pomPath);
        assertThat(pomContent)
                .as("curve-api é serviço REST puro — MUST NOT declarar dependência de Kafka")
                .doesNotContain("kafka-clients")
                .doesNotContain("spring-kafka");
    }
}

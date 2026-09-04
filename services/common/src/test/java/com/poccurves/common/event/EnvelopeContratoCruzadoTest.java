package com.poccurves.common.event;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.InputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

/**
 * Teste de contrato cruzado (tarefa 2.6 da mudança curves-solution-architecture):
 * carrega os MESMOS exemplos reais de envelope
 * (contracts/events/fixtures/*.json — fonte única, copiada para o classpath
 * de services/common via pom.xml, e para services/feeder-marketdata via
 * npm run sync-contracts) e prova que o lado produtor (validador TypeScript
 * em envelope-contrato-cruzado.test.ts, ajv) e o lado consumidor (este
 * teste — validador Java networknt E deserialização real para
 * {@link EventEnvelope}, o mesmo caminho que {@code IngestaoListener} do
 * curve-processor usa) concordam sobre o que é um envelope válido.
 */
class EnvelopeContratoCruzadoTest {

    private final EventEnvelopeSchemaValidator validator = new EventEnvelopeSchemaValidator();
    private final ObjectMapper objectMapper = new ObjectMapper();

    private JsonNode carregarFixture(String nomeArquivo) throws Exception {
        String caminho = "/contracts/events/fixtures/" + nomeArquivo;
        try (InputStream in = getClass().getResourceAsStream(caminho)) {
            assertThat(in).as("fixture não encontrada no classpath: " + caminho).isNotNull();
            return objectMapper.readTree(in);
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"envelope-valido-exemplo.json", "envelope-manual-valido-exemplo.json"})
    @DisplayName("Fixture real de envelope é válida contra o schema (lado consumidor)")
    void fixtureRealEhValidaContraOSchema(String nomeArquivo) throws Exception {
        JsonNode envelope = carregarFixture(nomeArquivo);

        assertThat(validator.isValid(envelope))
                .as("validate() deveria retornar vazio: " + validator.validate(envelope))
                .isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {"envelope-valido-exemplo.json", "envelope-manual-valido-exemplo.json"})
    @DisplayName("Fixture real de envelope deserializa para EventEnvelope sem erro (mesmo caminho de IngestaoListener)")
    void fixtureRealDeserializaParaEventEnvelope(String nomeArquivo) throws Exception {
        JsonNode envelopeJson = carregarFixture(nomeArquivo);

        assertThatCode(() -> objectMapper.treeToValue(envelopeJson, EventEnvelope.class))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("Fixture MANUAL deserializada preserva submittedBy/justification")
    void fixtureManualPreservaCamposObrigatoriosDeManual() throws Exception {
        JsonNode envelopeJson = carregarFixture("envelope-manual-valido-exemplo.json");
        EventEnvelope envelope = objectMapper.treeToValue(envelopeJson, EventEnvelope.class);

        assertThat(envelope.source()).isEqualTo(EventSource.MANUAL);
        assertThat(envelope.submittedBy()).isEqualTo("operador.mesa");
        assertThat(envelope.justification()).isNotBlank();
    }
}

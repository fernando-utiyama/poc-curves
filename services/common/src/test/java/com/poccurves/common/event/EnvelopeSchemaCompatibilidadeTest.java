package com.poccurves.common.event;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Teste de compatibilidade (tarefa 2.7 da mudança curves-solution-architecture):
 * fixa (pin) o conjunto de campos obrigatórios e o tipo/formato declarado de
 * cada campo do contrato real (contracts/events/envelope.schema.json, lido
 * do classpath — nunca uma cópia local). Se alguém remover um campo
 * obrigatório ou mudar o tipo declarado de um campo existente, este teste
 * falha — uma mudança assim quebra tanto {@link EventEnvelope} (Java)
 * quanto `envelope.ts` (TypeScript, services/feeder-marketdata) sem que
 * nenhum dos dois avise em tempo de compilação, porque os dois só espelham
 * o schema manualmente.
 * <p>
 * Acrescentar um campo NOVO opcional não quebra este teste (de propósito —
 * isso é uma mudança compatível). Só a remoção de um campo obrigatório
 * fixado aqui, ou a mudança do tipo/formato de um campo já fixado, falha.
 */
class EnvelopeSchemaCompatibilidadeTest {

    private static final List<String> CAMPOS_OBRIGATORIOS_FIXADOS = List.of(
            "eventId", "correlationId", "source", "dataset", "referenceDate",
            "producedAt", "schemaVersion", "payloadKind", "loteId", "sequencia",
            "totalBlocos", "payload");

    private JsonNode carregarSchemaReal() throws Exception {
        try (InputStream in = getClass().getResourceAsStream("/contracts/events/envelope.schema.json")) {
            assertThat(in).as("schema real não encontrado no classpath").isNotNull();
            return new ObjectMapper().readTree(in);
        }
    }

    private List<String> extrairArrayDeString(JsonNode arrayNode) {
        List<String> valores = new ArrayList<>();
        for (JsonNode item : arrayNode) {
            valores.add(item.asText());
        }
        return valores;
    }

    @Test
    @DisplayName("Todos os campos obrigatórios fixados continuam obrigatórios no schema real")
    void camposObrigatoriosContinuamObrigatorios() throws Exception {
        JsonNode schema = carregarSchemaReal();
        List<String> obrigatoriosReais = extrairArrayDeString(schema.get("required"));

        assertThat(obrigatoriosReais).containsAll(CAMPOS_OBRIGATORIOS_FIXADOS);
    }

    @Test
    @DisplayName("Nenhum campo obrigatório novo apareceu sem os dois lados (Java/TypeScript) saberem — lista fixada é exaustiva")
    void nenhumCampoObrigatorioNovoInesperado() throws Exception {
        JsonNode schema = carregarSchemaReal();
        List<String> obrigatoriosReais = extrairArrayDeString(schema.get("required"));

        assertThat(obrigatoriosReais)
                .as("um campo obrigatório novo apareceu no schema sem atualizar EventEnvelope.java/envelope.ts — "
                        + "atualize os dois lados e só então esta lista fixada")
                .containsExactlyInAnyOrderElementsOf(CAMPOS_OBRIGATORIOS_FIXADOS);
    }

    @Test
    @DisplayName("Tipo/formato declarado de cada campo fixado não mudou")
    void tipoDeclaradoDeCadaCampoNaoMudou() throws Exception {
        JsonNode schema = carregarSchemaReal();
        JsonNode props = schema.get("properties");

        assertTipo(props, "eventId", "string", "uuid");
        assertTipo(props, "correlationId", "string", "uuid");
        assertTipo(props, "source", "string", null);
        assertTipo(props, "dataset", "string", null);
        assertTipo(props, "referenceDate", "string", "date");
        assertTipo(props, "producedAt", "string", "date-time");
        assertTipo(props, "schemaVersion", "string", null);
        assertTipo(props, "payloadKind", "string", null);
        assertTipo(props, "loteId", "string", null);
        assertTipo(props, "sequencia", "integer", null);
        assertTipo(props, "totalBlocos", "integer", null);
        assertTipo(props, "payload", "object", null);
    }

    @Test
    @DisplayName("O enum de source continua contendo os quatro valores originais (B3, BLOOMBERG, LSEG, MANUAL) — acrescentar é compatível, remover não")
    void enumSourceContinuaContendoOsValoresOriginais() throws Exception {
        JsonNode schema = carregarSchemaReal();
        List<String> valoresOriginais = List.of("B3", "BLOOMBERG", "LSEG", "MANUAL");
        List<String> valoresReais = extrairArrayDeString(schema.get("properties").get("source").get("enum"));

        assertThat(valoresReais).containsAll(valoresOriginais);
    }

    private void assertTipo(JsonNode props, String campo, String tipoEsperado, String formatoEsperado) {
        JsonNode campoNode = props.get(campo);
        assertThat(campoNode).as("campo '%s' desapareceu do schema", campo).isNotNull();
        assertThat(campoNode.get("type").asText()).as("tipo de '%s'", campo).isEqualTo(tipoEsperado);
        if (formatoEsperado != null) {
            JsonNode formato = campoNode.get("format");
            assertThat(formato).as("formato de '%s' desapareceu", campo).isNotNull();
            assertThat(formato.asText()).as("formato de '%s'", campo).isEqualTo(formatoEsperado);
        }
    }
}

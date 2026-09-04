package com.poccurves.processor.domain.parsing;
import com.poccurves.processor.domain.ingestao.PontoDadoMercado;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DatasetParserRegistryTest {

    private record DummyParser(String dataset) implements DatasetParser {
        @Override
        public ParseResult parse(byte[] conteudo, String encoding, LocalDate referenceDate) {
            return new ParseResult.Sucesso(List.of());
        }
    }

    @Test
    void deveResolverParserRegistradoComSucesso() {
        DatasetParser parserA = new DummyParser("PR_DI1");
        DatasetParser parserB = new DummyParser("BVBG.086");
        DatasetParserRegistry registry = new DatasetParserRegistry(List.of(parserA, parserB));

        Optional<DatasetParser> resultado = registry.resolver("PR_DI1");

        assertThat(resultado).isPresent();
        assertThat(resultado.get().dataset()).isEqualTo("PR_DI1");
    }

    @Test
    void deveRetornarVazioQuandoDatasetNaoEstiverRegistrado() {
        DatasetParser parserA = new DummyParser("PR_DI1");
        DatasetParserRegistry registry = new DatasetParserRegistry(List.of(parserA));

        Optional<DatasetParser> resultado = registry.resolver("dataset-nao-registrado");

        assertThat(resultado).isEmpty();
    }

    @Test
    void deveLancarExcecaoQuandoHouverDatasetsDuplicados() {
        DatasetParser parserA1 = new DummyParser("PR_DI1");
        DatasetParser parserA2 = new DummyParser("PR_DI1");

        assertThatThrownBy(() -> new DatasetParserRegistry(List.of(parserA1, parserA2)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("dataset duplicado no registro de parsers: PR_DI1");
    }

    @Test
    void deveLancarExcecaoQuandoListaDeParsersForNula() {
        assertThatThrownBy(() -> new DatasetParserRegistry(null))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("parsers não pode ser nulo");
    }

    @Test
    void deveCriarParseResultSucessoComPontosValidos() {
        PontoDadoMercado ponto = new PontoDadoMercado(
                "B3",
                "PR_DI1",
                LocalDate.of(2026, 8, 21),
                "DI1F27",
                new BigDecimal("10.5"),
                null,
                null
        );

        ParseResult.Sucesso sucesso = new ParseResult.Sucesso(List.of(ponto));

        assertThat(sucesso.pontos()).containsExactly(ponto);
    }

    @Test
    void deveLancarExcecaoQuandoParseResultFalhaTiverMotivoInvalido() {
        assertThatThrownBy(() -> new ParseResult.Falha(null, "diagnostico"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("motivo não pode ser nulo ou vazio");

        assertThatThrownBy(() -> new ParseResult.Falha("", "diagnostico"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("motivo não pode ser nulo ou vazio");
    }

    @Test
    void deveExecutarBranchCorretoNoPatternMatchingSwitch() {
        ParseResult resultadoSucesso = new ParseResult.Sucesso(List.of());
        ParseResult resultadoFalha = new ParseResult.Falha("ERRO_FORMATO", "Detalhes tecnicos");

        String tipoSucesso = switch (resultadoSucesso) {
            case ParseResult.Sucesso s -> "SUCESSO: " + s.pontos().size();
            case ParseResult.Falha f -> "FALHA: " + f.motivo();
        };

        String tipoFalha = switch (resultadoFalha) {
            case ParseResult.Sucesso s -> "SUCESSO: " + s.pontos().size();
            case ParseResult.Falha f -> "FALHA: " + f.motivo();
        };

        assertThat(tipoSucesso).isEqualTo("SUCESSO: 0");
        assertThat(tipoFalha).isEqualTo("FALHA: ERRO_FORMATO");
    }
}

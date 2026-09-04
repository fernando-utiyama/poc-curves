package com.poccurves.processor.domain;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class B3CurvaProntaParserTest {

    private final B3CurvaProntaParser parser = new B3CurvaProntaParser("B3_CURVA_PRE");
    private final LocalDate referenceDate = LocalDate.of(2026, 8, 21);
    private final String encoding = "ISO-8859-1";

    @Test
    void parse_sucessoCom5LinhasReais() {
        String[] linhas = {
                "DI x pré;1;3;13,90",
                "DI x pré;4;6;13,90",
                "DI x pré;10;14;13,87",
                "DI x pré;8511;12414;14,37",
                "DI x pré;7405;10800;14,38"
        };
        byte[] conteudo = String.join("\n", linhas).getBytes(StandardCharsets.ISO_8859_1);

        ParseResult result = parser.parse(conteudo, encoding, referenceDate);

        assertThat(result).isInstanceOf(ParseResult.Sucesso.class);
        ParseResult.Sucesso sucesso = (ParseResult.Sucesso) result;
        List<PontoDadoMercado> pontos = sucesso.pontos();
        assertThat(pontos).hasSize(5);

        PontoDadoMercado pontoV1 = pontos.get(0);
        assertThat(pontoV1.chaveInstrumento()).isEqualTo("1");
        assertThat(pontoV1.valor()).isEqualByComparingTo(new BigDecimal("13.90"));
        assertThat(pontoV1.fonte()).isEqualTo("B3");
        assertThat(pontoV1.dataReferencia()).isEqualTo(referenceDate);
        assertThat(pontoV1.tipoCotacao()).isEqualTo(B3CurvaProntaParser.TIPO_COTACAO_TAXA_CURVA_PRONTA);
        assertThat(pontoV1.dataVencimento()).isNull();
        assertThat(pontoV1.conjuntoDados()).isEqualTo("B3_CURVA_PRE");

        PontoDadoMercado pontoV8511 = pontos.get(3);
        assertThat(pontoV8511.chaveInstrumento()).isEqualTo("8511");
        assertThat(pontoV8511.valor()).isEqualByComparingTo(new BigDecimal("14.37"));
    }

    @Test
    void parse_preservaDigitosExatosSemArredondar() {
        String[] linhas = {
                "DI x pré;10;14;13,87"
        };
        byte[] conteudo = String.join("\n", linhas).getBytes(StandardCharsets.ISO_8859_1);

        ParseResult result = parser.parse(conteudo, encoding, referenceDate);

        assertThat(result).isInstanceOf(ParseResult.Sucesso.class);
        ParseResult.Sucesso sucesso = (ParseResult.Sucesso) result;
        PontoDadoMercado ponto = sucesso.pontos().get(0);
        
        assertThat(ponto.valor().toPlainString()).isEqualTo("13.87");
    }

    @Test
    void parse_falhaNumeroDeCamposErrado() {
        byte[] conteudo = "DI x pré;1;3".getBytes(StandardCharsets.ISO_8859_1);

        ParseResult result = parser.parse(conteudo, encoding, referenceDate);

        assertThat(result).isInstanceOf(ParseResult.Falha.class);
    }

    @Test
    void parse_falhaTaxaNaoNumerica() {
        byte[] conteudo = "DI x pré;1;3;abc".getBytes(StandardCharsets.ISO_8859_1);

        ParseResult result = parser.parse(conteudo, encoding, referenceDate);

        assertThat(result).isInstanceOf(ParseResult.Falha.class);
    }

    @Test
    void parse_falhaConteudoVazioNenhumaLinhaDeDado() {
        byte[] conteudo = new byte[0];

        ParseResult result = parser.parse(conteudo, encoding, referenceDate);

        assertThat(result).isInstanceOf(ParseResult.Falha.class);
    }

    @Test
    void parse_falhaEncodingDesconhecido() {
        byte[] conteudo = "DI x pré;1;3;13,90".getBytes(StandardCharsets.ISO_8859_1);

        ParseResult result = parser.parse(conteudo, "NAO-EXISTE-9999", referenceDate);

        assertThat(result).isInstanceOf(ParseResult.Falha.class);
    }

    @Test
    void dataset_devolveOValorPassadoNoConstrutor() {
        B3CurvaProntaParser parserTest = new B3CurvaProntaParser("TEST_DATASET");
        assertThat(parserTest.dataset()).isEqualTo("TEST_DATASET");
    }

    @Test
    void parse_puloCabecalhoQuandoPresenteESucessoComAsLinhasDeDadoReais() {
        String[] linhas = {
                "Descrição da Taxa;Dias Úteis;Dias Corridos;Preço/Taxa",
                "DI x pré;1;3;13,90",
                "DI x pré;4;6;13,90"
        };
        byte[] conteudo = String.join("\n", linhas).getBytes(StandardCharsets.ISO_8859_1);

        ParseResult result = parser.parse(conteudo, encoding, referenceDate);

        assertThat(result).isInstanceOf(ParseResult.Sucesso.class);
        ParseResult.Sucesso sucesso = (ParseResult.Sucesso) result;
        assertThat(sucesso.pontos()).hasSize(2);
        assertThat(sucesso.pontos().get(0).valor()).isEqualByComparingTo(new BigDecimal("13.90"));
    }

    @Test
    void parse_sucessoSemCabecalhoQuandoPrimeiraLinhaJaENumerica() {
        // Achado real: a B3 nem sempre envia a linha de cabeçalho (confirmado ao vivo contra
        // o endpoint real) — o parser não pode assumir presença fixa.
        String[] linhas = {
                "DI x pré;1;3;13,90",
                "DI x pré;4;6;13,90"
        };
        byte[] conteudo = String.join("\n", linhas).getBytes(StandardCharsets.ISO_8859_1);

        ParseResult result = parser.parse(conteudo, encoding, referenceDate);

        assertThat(result).isInstanceOf(ParseResult.Sucesso.class);
        ParseResult.Sucesso sucesso = (ParseResult.Sucesso) result;
        assertThat(sucesso.pontos()).hasSize(2);
    }

    @Test
    void parse_falhaTaxaNaoNumericaEmLinhaQueNaoEAPrimeira() {
        // Taxa não numérica DEPOIS da primeira linha é erro real, não cabeçalho —
        // o pulo de cabeçalho só se aplica à primeira linha do arquivo.
        String[] linhas = {
                "DI x pré;1;3;13,90",
                "DI x pré;4;6;abc"
        };
        byte[] conteudo = String.join("\n", linhas).getBytes(StandardCharsets.ISO_8859_1);

        ParseResult result = parser.parse(conteudo, encoding, referenceDate);

        assertThat(result).isInstanceOf(ParseResult.Falha.class);
    }

    @Test
    void parse_ignoraLinhasEmBrancoEntreRegistros() {
        String[] linhas = {
                "DI x pré;1;3;13,90",
                "",
                "DI x pré;4;6;13,90"
        };
        byte[] conteudo = String.join("\n", linhas).getBytes(StandardCharsets.ISO_8859_1);

        ParseResult result = parser.parse(conteudo, encoding, referenceDate);

        assertThat(result).isInstanceOf(ParseResult.Sucesso.class);
        ParseResult.Sucesso sucesso = (ParseResult.Sucesso) result;
        assertThat(sucesso.pontos()).hasSize(2);
    }
}

package com.poccurves.processor.domain;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

class Bvbg086PricRptParserTest {

    private byte[] lerFixture() throws IOException {
        try (InputStream in = getClass().getResourceAsStream("/fixtures/bvbg086_di1_4blocos.xml")) {
            assertThat(in).as("fixture bvbg086_di1_4blocos.xml deve existir no classpath de teste").isNotNull();
            return in.readAllBytes();
        }
    }

    @Test
    void extraiOsTresContratosDeDi1ComTaxaDeAjusteReal() throws IOException {
        Bvbg086PricRptParser parser = new Bvbg086PricRptParser("BVBG.086");

        ParseResult resultado = parser.parse(lerFixture(), StandardCharsets.UTF_8.name(), LocalDate.of(2026, 8, 21));

        assertThat(resultado).isInstanceOf(ParseResult.Sucesso.class);
        List<PontoDadoMercado> pontos = ((ParseResult.Sucesso) resultado).pontos();

        // 4 <BizGrp> no fixture: 3 DI1 com AdjstdQtTax + 1 opção (TTENT131) sem — só os 3 DI1 viram ponto.
        assertThat(pontos).hasSize(3);

        Map<String, PontoDadoMercado> porTicker = pontos.stream()
                .collect(Collectors.toMap(PontoDadoMercado::chaveInstrumento, Function.identity()));

        assertThat(porTicker).containsKeys("DI1Z28", "DI1J30", "DI1V31");

        PontoDadoMercado di1z28 = porTicker.get("DI1Z28");
        assertThat(di1z28.valor()).isEqualByComparingTo(new BigDecimal("14.129"));
        assertThat(di1z28.dataReferencia()).isEqualTo(LocalDate.of(2026, 8, 21));
        assertThat(di1z28.tipoCotacao()).isEqualTo(Bvbg086PricRptParser.TIPO_COTACAO_TAXA_AJUSTE);
        assertThat(di1z28.fonte()).isEqualTo("B3");
        assertThat(di1z28.conjuntoDados()).isEqualTo("BVBG.086");
        assertThat(di1z28.dataVencimento()).isNull();
    }

    @Test
    void ignoraSilenciosamenteInstrumentoSemTaxaDeAjuste() throws IOException {
        Bvbg086PricRptParser parser = new Bvbg086PricRptParser("BVBG.086");

        ParseResult resultado = parser.parse(lerFixture(), StandardCharsets.UTF_8.name(), LocalDate.of(2026, 8, 21));

        List<PontoDadoMercado> pontos = ((ParseResult.Sucesso) resultado).pontos();
        assertThat(pontos).extracting(PontoDadoMercado::chaveInstrumento).doesNotContain("TTENT131");
    }

    @Test
    void preservaOsDigitosExatosDaTaxaDeAjusteSemArredondar() throws IOException {
        Bvbg086PricRptParser parser = new Bvbg086PricRptParser("BVBG.086");

        ParseResult resultado = parser.parse(lerFixture(), StandardCharsets.UTF_8.name(), LocalDate.of(2026, 8, 21));
        List<PontoDadoMercado> pontos = ((ParseResult.Sucesso) resultado).pontos();

        PontoDadoMercado di1j30 = pontos.stream()
                .filter(p -> p.chaveInstrumento().equals("DI1J30"))
                .findFirst().orElseThrow();

        // 14.334 é a AdjstdQtTax real do contrato DI1J30 no pregão de 2026-08-21 (fixture)
        assertThat(di1j30.valor()).isEqualByComparingTo(new BigDecimal("14.334"));
        assertThat(di1j30.valor().toPlainString()).isEqualTo("14.334");
    }

    @Test
    void retornaFalhaParaBlocoSemNenhumBizGrp() {
        Bvbg086PricRptParser parser = new Bvbg086PricRptParser("BVBG.086");

        ParseResult resultado = parser.parse("<vazio></vazio>".getBytes(StandardCharsets.UTF_8), "UTF-8", LocalDate.of(2026, 8, 21));

        assertThat(resultado).isInstanceOf(ParseResult.Falha.class);
    }

    @Test
    void retornaFalhaParaEncodingDesconhecido() throws IOException {
        Bvbg086PricRptParser parser = new Bvbg086PricRptParser("BVBG.086");

        ParseResult resultado = parser.parse(lerFixture(), "NAO-EXISTE-9999", LocalDate.of(2026, 8, 21));

        assertThat(resultado).isInstanceOf(ParseResult.Falha.class);
    }

    @Test
    void datasetRetornaOValorPassadoNoConstrutor() {
        assertThat(new Bvbg086PricRptParser("PR_DI1").dataset()).isEqualTo("PR_DI1");
        assertThat(new Bvbg086PricRptParser("BVBG.086").dataset()).isEqualTo("BVBG.086");
    }
}

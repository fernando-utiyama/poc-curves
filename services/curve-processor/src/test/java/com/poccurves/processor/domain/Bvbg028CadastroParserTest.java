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

class Bvbg028CadastroParserTest {

    private byte[] lerFixture() throws IOException {
        try (InputStream in = getClass().getResourceAsStream("/fixtures/bvbg028_di1_3blocos.xml")) {
            assertThat(in).as("fixture bvbg028_di1_3blocos.xml deve existir no classpath de teste").isNotNull();
            return in.readAllBytes();
        }
    }

    @Test
    void extraiVencimentoEDiasUteisReaisDosTresContratosDeDi1() throws IOException {
        Bvbg028CadastroParser parser = new Bvbg028CadastroParser();

        ParseResult resultado = parser.parse(lerFixture(), StandardCharsets.UTF_8.name(), LocalDate.of(2026, 8, 21));

        assertThat(resultado).isInstanceOf(ParseResult.Sucesso.class);
        List<PontoDadoMercado> pontos = ((ParseResult.Sucesso) resultado).pontos();
        assertThat(pontos).hasSize(3);

        Map<String, PontoDadoMercado> porTicker = pontos.stream()
                .collect(Collectors.toMap(PontoDadoMercado::chaveInstrumento, Function.identity()));

        PontoDadoMercado di1z28 = porTicker.get("DI1Z28");
        assertThat(di1z28.dataVencimento()).isEqualTo(LocalDate.of(2028, 12, 1));
        assertThat(di1z28.valor()).isEqualByComparingTo(new BigDecimal("565"));
        assertThat(di1z28.tipoCotacao()).isEqualTo(Bvbg028CadastroParser.TIPO_COTACAO_DIAS_UTEIS_VENCIMENTO);
        assertThat(di1z28.dataReferencia()).isEqualTo(LocalDate.of(2026, 8, 21));

        PontoDadoMercado di1j30 = porTicker.get("DI1J30");
        assertThat(di1j30.dataVencimento()).isEqualTo(LocalDate.of(2030, 4, 1));
        assertThat(di1j30.valor()).isEqualByComparingTo(new BigDecimal("892"));

        PontoDadoMercado di1v31 = porTicker.get("DI1V31");
        assertThat(di1v31.dataVencimento()).isEqualTo(LocalDate.of(2031, 10, 1));
        assertThat(di1v31.valor()).isEqualByComparingTo(new BigDecimal("1269"));
    }

    @Test
    void datasetEBvbg028() {
        assertThat(new Bvbg028CadastroParser().dataset()).isEqualTo("BVBG.028");
    }

    @Test
    void retornaFalhaParaBlocoSemNenhumBizGrp() {
        Bvbg028CadastroParser parser = new Bvbg028CadastroParser();

        ParseResult resultado = parser.parse("<vazio></vazio>".getBytes(StandardCharsets.UTF_8), "UTF-8", LocalDate.of(2026, 8, 21));

        assertThat(resultado).isInstanceOf(ParseResult.Falha.class);
    }
}

package com.poccurves.processor.application.model;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Linhas reais capturadas de docs/TaxaSwap.txt (data de geração 2026-09-14) — os valores
 * esperados (13.90, -117.96, 5.1696, 185648.0, 18.59) foram conferidos independentemente
 * dígito a dígito antes de escrever este teste, servindo como oráculo do layout de 72 colunas.
 */
class B3TaxaSwapParserTest {

    private final LocalDate referenceDate = LocalDate.of(2026, 9, 14);
    private final String encoding = "ISO-8859-1";

    private static final String LINHA_PRE_V1 = "0146360010120260914T1PRE  DIxPRE         0000100001+00000139000000F00001";
    private static final String LINHA_PRE_V2 = "0146370010120260914T1PRE  DIxPRE         0000200002+00000138020000M00002";
    private static final String LINHA_DCL_V1 = "0049060010120260914T1DCL  CUPOM LIMPO - S0000100001-00001179600000F00001";
    private static final String LINHA_PTX_V1 = "0149140010120260914T1PTX  PTAX           0000100001+00000051696000F00001";
    private static final String LINHA_INP_V1 = "0115780010120260914T1INP  IBOVESPA       0000100001+01856480000000F00001";
    private static final String LINHA_DPL_V1 = "0076860010120260914T1DPL  Cupom Limpo de 0000100001+00000185900000F00001";
    private static final String LINHA_IPS_V1 = "0118560010120260914T1IPS  IGPxPRE SINTET.0000100001+00012068970000F00001";

    private ParseResult.Sucesso parseComo(String codigoCurva, String... linhas) {
        String dataset = "B3_TAXA_SWAP_" + codigoCurva;
        byte[] conteudo = String.join("\n", linhas).getBytes(StandardCharsets.ISO_8859_1);
        ParseResult resultado = new B3TaxaSwapParser(dataset, codigoCurva).parse(conteudo, encoding, referenceDate);
        assertThat(resultado).isInstanceOf(ParseResult.Sucesso.class);
        return (ParseResult.Sucesso) resultado;
    }

    @Test
    void parse_extraiTaxaPreComoOraculoDoLayout() {
        List<PontoDadoMercado> pontos = parseComo("PRE", LINHA_PRE_V1, LINHA_PRE_V2).pontos();

        assertThat(pontos).hasSize(2);
        assertThat(pontos.get(0).chaveInstrumento()).isEqualTo("1");
        assertThat(pontos.get(0).valor()).isEqualByComparingTo(new BigDecimal("13.9000000"));
        assertThat(pontos.get(0).fonte()).isEqualTo("B3");
        assertThat(pontos.get(0).conjuntoDados()).isEqualTo("B3_TAXA_SWAP_PRE");
        assertThat(pontos.get(0).tipoCotacao()).isEqualTo(B3TaxaSwapParser.TIPO_COTACAO_TAXA_MERCADO_SWAP);
        assertThat(pontos.get(1).chaveInstrumento()).isEqualTo("2");
        assertThat(pontos.get(1).valor()).isEqualByComparingTo(new BigDecimal("13.8020000"));
    }

    @Test
    void parse_taxaNegativaDeCupomCambialLimpo() {
        List<PontoDadoMercado> pontos = parseComo("DCL", LINHA_DCL_V1).pontos();

        assertThat(pontos).hasSize(1);
        assertThat(pontos.get(0).valor()).isEqualByComparingTo(new BigDecimal("-117.9600000"));
    }

    @Test
    void parse_precoDeCambioComSeteCasasDecimais() {
        List<PontoDadoMercado> pontos = parseComo("PTX", LINHA_PTX_V1).pontos();

        assertThat(pontos).hasSize(1);
        assertThat(pontos.get(0).valor()).isEqualByComparingTo(new BigDecimal("5.1696000"));
    }

    @Test
    void parse_pontosDeIndiceIbovespa() {
        List<PontoDadoMercado> pontos = parseComo("INP", LINHA_INP_V1).pontos();

        assertThat(pontos).hasSize(1);
        assertThat(pontos.get(0).valor()).isEqualByComparingTo(new BigDecimal("185648.0000000"));
    }

    @Test
    void parse_cupomLimpoDeIpca() {
        List<PontoDadoMercado> pontos = parseComo("DPL", LINHA_DPL_V1).pontos();

        assertThat(pontos).hasSize(1);
        assertThat(pontos.get(0).valor()).isEqualByComparingTo(new BigDecimal("18.5900000"));
    }

    @Test
    void parse_ignoraCodigosDeCurvaForaDoEscopoNoMesmoArquivo() {
        // Arquivo real traz 106 códigos misturados — o parser de uma curva SHALL
        // ignorar silenciosamente as linhas de todas as outras.
        List<PontoDadoMercado> pontos = parseComo(
                "PRE", LINHA_DCL_V1, LINHA_PTX_V1, LINHA_INP_V1, LINHA_DPL_V1, LINHA_IPS_V1, LINHA_PRE_V1
        ).pontos();

        assertThat(pontos).hasSize(1);
        assertThat(pontos.get(0).valor()).isEqualByComparingTo(new BigDecimal("13.9000000"));
    }

    @Test
    void parse_falhaQuandoCodigoDeCurvaNaoAparece() {
        String dataset = "B3_TAXA_SWAP_DPL";
        byte[] conteudo = LINHA_PRE_V1.getBytes(StandardCharsets.ISO_8859_1);

        ParseResult resultado = new B3TaxaSwapParser(dataset, "DPL").parse(conteudo, encoding, referenceDate);

        assertThat(resultado).isInstanceOf(ParseResult.Falha.class);
        assertThat(((ParseResult.Falha) resultado).motivo()).contains("DPL");
    }

    @Test
    void parse_falhaQuandoLinhaTemTamanhoDiferenteDe72Colunas() {
        String dataset = "B3_TAXA_SWAP_PRE";
        byte[] conteudo = "linha curta demais".getBytes(StandardCharsets.ISO_8859_1);

        ParseResult resultado = new B3TaxaSwapParser(dataset, "PRE").parse(conteudo, encoding, referenceDate);

        assertThat(resultado).isInstanceOf(ParseResult.Falha.class);
        assertThat(((ParseResult.Falha) resultado).motivo()).contains("72 colunas");
    }

    @Test
    void parse_falhaComEncodingNaoSuportado() {
        ParseResult resultado = new B3TaxaSwapParser("B3_TAXA_SWAP_PRE", "PRE")
                .parse(LINHA_PRE_V1.getBytes(StandardCharsets.ISO_8859_1), "encoding-invalido", referenceDate);

        assertThat(resultado).isInstanceOf(ParseResult.Falha.class);
    }

    @Test
    void dataset_retornaODatasetConfigurado() {
        B3TaxaSwapParser parser = new B3TaxaSwapParser("B3_TAXA_SWAP_INP", "INP");

        assertThat(parser.dataset()).isEqualTo("B3_TAXA_SWAP_INP");
    }
}

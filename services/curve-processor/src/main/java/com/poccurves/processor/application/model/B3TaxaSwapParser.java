package com.poccurves.processor.application.model;


import java.math.BigDecimal;
import java.nio.charset.Charset;
import java.nio.charset.IllegalCharsetNameException;
import java.nio.charset.UnsupportedCharsetException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Parser do arquivo de largura fixa "Mercado de Derivativos – Taxas de Mercado para Swaps" da
 * B3 (TaxaSwap.txt, endpoint {@code pesquisapregao/download}, prefixo {@code TS}), publicado
 * pelo function-marketdata sob um dataset por curva alvo (ex.: {@code B3_TAXA_SWAP_DCL}).
 * <p>
 * O arquivo traz mais de 100 códigos de curva misturados na mesma publicação (confirmado contra
 * o arquivo real: 106 códigos, 278 vértices cada) — este parser extrai só os vértices do
 * {@code codigoCurva} configurado no construtor, ignorando o resto sem erro (é esperado que a
 * maioria das linhas não seja da curva pedida).
 * <p>
 * Layout de 72 colunas confirmado contra a planilha oficial da B3 ("Layout Mercado de
 * Derivativos - Taxas de Mercado para Swaps", fornecida pelo usuário) e validado campo a campo
 * contra um arquivo real (data de geração 2026-09-14): identificação da transação (1-6),
 * complemento fixo "001" (7-9), tipo de registro fixo "01" (10-11), data de geração AAAAMMDD
 * (12-19), código das curvas a termo (20-21), código da taxa (22-26), descrição da taxa
 * (27-41), dias corridos (42-46, não usado por este parser — {@link PontoDadoMercado} não tem
 * campo para os dois prazos), dias úteis — "número de saques" no layout oficial (47-51), sinal
 * '+'/'-' da taxa teórica (52), taxa teórica com 7 casas decimais implícitas e sem separador
 * (53-66), característica do vértice F=fixo (dado real)/M=móvel (interpolado pela B3) (67, não
 * usado por este parser pelo mesmo motivo), código do vértice (68-72, redundante com dias
 * corridos em todas as amostras observadas).
 */
public class B3TaxaSwapParser implements DatasetParser {

    public static final String TIPO_COTACAO_TAXA_MERCADO_SWAP = "TAXA_MERCADO_SWAP";

    private static final int TAMANHO_LINHA = 72;
    private static final int CASAS_DECIMAIS_TAXA_TEORICA = 7;

    /**
     * Um vértice bruto do TaxaSwap.txt, com os dois prazos (dias úteis e dias corridos) — usado
     * pelo caminho novo de gravação em {@code tBtrsCurvaPrimr} (openspec/changes/legado-schema-
     * curvas-mercado), que tem coluna para os dois, diferente de {@link PontoDadoMercado}.
     */
    public record VerticeTaxaSwap(int diasUteis, int diasCorridos, java.math.BigDecimal taxa) {
    }

    private final String dataset;
    private final String codigoCurva;

    public B3TaxaSwapParser(String dataset, String codigoCurva) {
        if (dataset == null || dataset.isBlank()) {
            throw new IllegalArgumentException("dataset não pode ser nulo ou vazio");
        }
        if (codigoCurva == null || codigoCurva.isBlank()) {
            throw new IllegalArgumentException("codigoCurva não pode ser nulo ou vazio");
        }
        this.dataset = dataset;
        this.codigoCurva = codigoCurva;
    }

    @Override
    public String dataset() {
        return this.dataset;
    }

    @Override
    public ParseResult parse(byte[] conteudo, String encoding, LocalDate referenceDate) {
        Charset charset;
        try {
            charset = Charset.forName(encoding);
        } catch (IllegalCharsetNameException | UnsupportedCharsetException e) {
            return new ParseResult.Falha("encoding não suportado: " + encoding, e.toString());
        }

        Objects.requireNonNull(referenceDate, "referenceDate não pode ser nula");

        List<PontoDadoMercado> pontos = new ArrayList<>();

        for (String linha : linhasNaoVazias(conteudo, charset)) {
            LinhaTaxaSwap linhaParseada;
            try {
                linhaParseada = parseLinha(linha, codigoCurva);
            } catch (IllegalArgumentException e) {
                return new ParseResult.Falha(e.getMessage(), "linha: \"" + linha + "\"");
            }
            if (linhaParseada == null) {
                continue;
            }

            pontos.add(new PontoDadoMercado(
                    "B3",
                    this.dataset,
                    referenceDate,
                    String.valueOf(linhaParseada.diasUteis()),
                    linhaParseada.taxa(),
                    TIPO_COTACAO_TAXA_MERCADO_SWAP,
                    null
            ));
        }

        if (pontos.isEmpty()) {
            return new ParseResult.Falha(
                    "nenhum vértice encontrado para o código de curva " + codigoCurva + " no arquivo de taxas de swap",
                    "");
        }

        return new ParseResult.Sucesso(pontos);
    }

    /**
     * Extrai os vértices de um código de curva com os dois prazos (dias úteis e dias corridos) —
     * caminho novo de gravação em {@code tBtrsCurvaPrimr}, que tem coluna para os dois, diferente
     * do caminho genérico ({@link #parse}, que só usa dias úteis porque {@link PontoDadoMercado}
     * não tem campo para os dois). Estático porque este caminho não passa pelo
     * {@code DatasetParserRegistry} genérico — é acionado direto por
     * {@code ProcessarEnvelopeIngestaoUseCase} para os datasets do TaxaSwap.txt (PRE/DCL/PTX/
     * INP/DPL), sem instanciar um {@code B3TaxaSwapParser} por curva.
     *
     * @throws IllegalArgumentException se o encoding não for suportado ou alguma linha do código
     *                                   pedido estiver malformada
     */
    public static List<VerticeTaxaSwap> extrairVertices(byte[] conteudo, String encoding, String codigoCurva) {
        Charset charset;
        try {
            charset = Charset.forName(encoding);
        } catch (IllegalCharsetNameException | UnsupportedCharsetException e) {
            throw new IllegalArgumentException("encoding não suportado: " + encoding, e);
        }

        List<VerticeTaxaSwap> vertices = new ArrayList<>();
        for (String linha : linhasNaoVazias(conteudo, charset)) {
            LinhaTaxaSwap linhaParseada = parseLinha(linha, codigoCurva);
            if (linhaParseada != null) {
                vertices.add(new VerticeTaxaSwap(
                        linhaParseada.diasUteis(), linhaParseada.diasCorridos(), linhaParseada.taxa()));
            }
        }
        return vertices;
    }

    private static List<String> linhasNaoVazias(byte[] conteudo, Charset charset) {
        String textoCompleto = new String(conteudo, charset);
        return java.util.Arrays.stream(textoCompleto.split("\n", -1))
                .map(String::strip)
                .filter(linha -> !linha.isEmpty())
                .toList();
    }

    /** Uma linha (72 colunas) já decodificada do TaxaSwap.txt, compartilhada por {@link #parse} e {@link #extrairVertices}. */
    private record LinhaTaxaSwap(int diasUteis, int diasCorridos, BigDecimal taxa) {
    }

    /**
     * Parseia uma linha do código de curva pedido — layout compartilhado por {@link #parse} e
     * {@link #extrairVertices}. Retorna {@code null} (sem erro) quando a linha é de outro código
     * de curva, o caso comum já que o arquivo real traz mais de 100 códigos misturados.
     *
     * @throws IllegalArgumentException se a linha do código pedido estiver malformada
     */
    private static LinhaTaxaSwap parseLinha(String linha, String codigoCurva) {
        if (linha.length() != TAMANHO_LINHA) {
            throw new IllegalArgumentException(
                    "linha do arquivo de taxas de swap com tamanho inesperado (esperado " + TAMANHO_LINHA
                            + " colunas, encontrado " + linha.length() + ")");
        }

        String codigoTaxaLinha = linha.substring(21, 26).strip();
        if (!codigoTaxaLinha.equals(codigoCurva)) {
            return null;
        }

        int diasCorridos;
        int diasUteis;
        try {
            diasCorridos = Integer.parseInt(linha.substring(41, 46).strip());
            diasUteis = Integer.parseInt(linha.substring(46, 51).strip());
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(
                    "prazo não numérico na linha da curva " + codigoCurva + ": \"" + linha + "\"", e);
        }

        char sinal = linha.charAt(51);
        if (sinal != '+' && sinal != '-') {
            throw new IllegalArgumentException(
                    "sinal da taxa teórica inválido na linha da curva " + codigoCurva + ": '" + sinal + "'");
        }

        BigDecimal taxa;
        try {
            taxa = new BigDecimal(linha.substring(52, 66)).movePointLeft(CASAS_DECIMAIS_TAXA_TEORICA);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(
                    "taxa teórica não numérica na linha da curva " + codigoCurva + ": \"" + linha + "\"", e);
        }
        if (sinal == '-') {
            taxa = taxa.negate();
        }

        return new LinhaTaxaSwap(diasUteis, diasCorridos, taxa);
    }
}

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

        String textoCompleto = new String(conteudo, charset);
        String[] linhas = textoCompleto.split("\n", -1);

        List<PontoDadoMercado> pontos = new ArrayList<>();

        for (String linhaComEspacos : linhas) {
            String linha = linhaComEspacos.strip();
            if (linha.isEmpty()) {
                continue;
            }

            if (linha.length() != TAMANHO_LINHA) {
                return new ParseResult.Falha(
                        "linha do arquivo de taxas de swap com tamanho inesperado (esperado " + TAMANHO_LINHA + " colunas)",
                        "tamanho encontrado: " + linha.length() + ", linha: \"" + linha + "\"");
            }

            String codigoTaxaLinha = linha.substring(21, 26).strip();
            if (!codigoTaxaLinha.equals(codigoCurva)) {
                continue;
            }

            String diasUteisTexto = linha.substring(46, 51).strip();
            int diasUteis;
            try {
                diasUteis = Integer.parseInt(diasUteisTexto);
            } catch (NumberFormatException e) {
                return new ParseResult.Falha(
                        "dias úteis não numéricos na linha da curva " + codigoCurva,
                        "valor: \"" + diasUteisTexto + "\", linha: \"" + linha + "\"");
            }

            char sinal = linha.charAt(51);
            if (sinal != '+' && sinal != '-') {
                return new ParseResult.Falha(
                        "sinal da taxa teórica inválido na linha da curva " + codigoCurva,
                        "sinal encontrado: '" + sinal + "', linha: \"" + linha + "\"");
            }

            String taxaTeoricaTexto = linha.substring(52, 66);
            BigDecimal valor;
            try {
                valor = new BigDecimal(taxaTeoricaTexto).movePointLeft(CASAS_DECIMAIS_TAXA_TEORICA);
            } catch (NumberFormatException e) {
                return new ParseResult.Falha(
                        "taxa teórica não numérica na linha da curva " + codigoCurva,
                        "valor: \"" + taxaTeoricaTexto + "\", linha: \"" + linha + "\"");
            }
            if (sinal == '-') {
                valor = valor.negate();
            }

            pontos.add(new PontoDadoMercado(
                    "B3",
                    this.dataset,
                    referenceDate,
                    String.valueOf(diasUteis),
                    valor,
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
}

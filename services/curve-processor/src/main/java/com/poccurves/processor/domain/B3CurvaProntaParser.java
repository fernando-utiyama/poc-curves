package com.poccurves.processor.domain;


import java.math.BigDecimal;
import java.nio.charset.Charset;
import java.nio.charset.IllegalCharsetNameException;
import java.nio.charset.UnsupportedCharsetException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Parser para o CSV de curva pronta da B3 (endpoint sistemaswebb3-derivativos.b3.com.br/referenceRatesProxy),
 * publicado pelo feeder-marketdata sob o dataset B3_CURVA_PRE.
 * 
 * Formato esperado: Descrição;Dias Úteis;Dias Corridos;Preço/Taxa
 * Sendo que Preço/Taxa é um número decimal com vírgula.
 * <p>
 * A B3 nem sempre envia a linha de cabeçalho ("Descrição da Taxa;Dias Úteis;Dias
 * Corridos;Preço/Taxa") — verificado ao vivo em sessões diferentes, com e sem cabeçalho, para
 * a mesma curva PRE. O parser detecta e pula um cabeçalho quando presente (primeira linha com
 * o campo de taxa não numérico), mas não exige que ele exista.
 *
 * A data de referência vem do envelope passado para o parser (e não do conteúdo CSV) porque o CSV em si
 * não possui coluna com esta data.
 */
public class B3CurvaProntaParser implements DatasetParser {

    public static final String TIPO_COTACAO_TAXA_CURVA_PRONTA = "TAXA_CURVA_PRONTA";

    private final String dataset;

    public B3CurvaProntaParser(String dataset) {
        if (dataset == null || dataset.isBlank()) {
            throw new IllegalArgumentException("dataset não pode ser nulo ou vazio");
        }
        this.dataset = dataset;
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
        boolean primeiraLinhaVista = false;

        for (String linhaComEspacos : linhas) {
            String linha = linhaComEspacos.strip();
            if (linha.isEmpty()) {
                continue;
            }

            String[] campos = linha.split(";", -1);
            if (campos.length != 4) {
                return new ParseResult.Falha("linha da curva pronta com número de campos inesperado (esperado 4, separado por ';')", "linha: \"" + linha + "\"");
            }

            BigDecimal valor;
            try {
                valor = ConversorDecimal.paraBigDecimal(campos[3], ',');
            } catch (IllegalArgumentException e) {
                // Achado real: a B3 nem sempre envia a linha de cabeçalho
                // ("Descrição da Taxa;Dias Úteis;Dias Corridos;Preço/Taxa") — verificado ao
                // vivo em sessões diferentes com e sem cabeçalho para a mesma curva (PRE).
                // Em vez de assumir presença fixa (que quebraria o caso real sem cabeçalho já
                // comprovado), detectamos pela primeira linha não numérica no campo de taxa —
                // só na PRIMEIRA linha do arquivo isso é tratado como cabeçalho, nunca depois.
                if (!primeiraLinhaVista) {
                    primeiraLinhaVista = true;
                    continue;
                }
                return new ParseResult.Falha("taxa não numérica na curva pronta", "linha: \"" + linha + "\", erro: " + e.getMessage());
            }
            primeiraLinhaVista = true;

            String diasUteis = campos[1].strip();
            if (diasUteis.isEmpty()) {
                return new ParseResult.Falha("linha da curva pronta sem Dias Úteis", "linha: \"" + linha + "\"");
            }

            pontos.add(new PontoDadoMercado(
                    "B3",
                    this.dataset,
                    referenceDate,
                    diasUteis,
                    valor,
                    TIPO_COTACAO_TAXA_CURVA_PRONTA,
                    null
            ));
        }

        if (pontos.isEmpty()) {
            return new ParseResult.Falha("nenhuma linha de dado na curva pronta", "");
        }

        return new ParseResult.Sucesso(pontos);
    }
}

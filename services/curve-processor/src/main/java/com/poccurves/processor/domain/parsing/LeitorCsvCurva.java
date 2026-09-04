package com.poccurves.processor.domain.parsing;
import com.poccurves.processor.domain.cargamanual.ErroLinhaCarga;
import com.poccurves.processor.domain.cargamanual.ResultadoLeituraCarga;
import com.poccurves.processor.domain.curva.VerticeCurva;


import java.math.BigDecimal;
import java.nio.charset.Charset;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Utilitário puro para leitura e validação de arquivos CSV de carga manual de vértices de curva.
 * Espelha o leiaute especificado em docs/leiaute-carga-manual-curva.md.
 */
public final class LeitorCsvCurva {

    private static final List<String> CABECALHO_ESPERADO = List.of(
            "prazo_dias_uteis",
            "prazo_dias_corridos",
            "data_vencimento",
            "taxa",
            "fator_desconto"
    );

    private LeitorCsvCurva() {
    }

    /**
     * Lê e valida os vértices de uma curva a partir de um array de bytes CSV.
     *
     * @param conteudo         bytes do arquivo CSV
     * @param encoding         nome do charset (ex. "UTF-8", "ISO-8859-1")
     * @param separadorColuna  caractere separador de campos (ex. ';' ou ',')
     * @param separadorDecimal caractere separador decimal para taxa e fator_desconto (ex. ',' ou '.')
     * @return {@link ResultadoLeituraCarga.Sucesso} com a lista de vértices se todas as linhas forem válidas,
     *         ou {@link ResultadoLeituraCarga.Falha} com todos os erros encontrados.
     */
    public static ResultadoLeituraCarga ler(
            byte[] conteudo,
            String encoding,
            char separadorColuna,
            char separadorDecimal
    ) {
        Charset charset;
        try {
            if (encoding == null) {
                return new ResultadoLeituraCarga.Falha(List.of(
                        new ErroLinhaCarga(1, "encoding", "encoding não suportado: null")
                ));
            }
            charset = Charset.forName(encoding);
        } catch (IllegalArgumentException e) {
            return new ResultadoLeituraCarga.Falha(List.of(
                    new ErroLinhaCarga(1, "encoding", "encoding não suportado: " + encoding)
            ));
        }

        if (conteudo == null) {
            return new ResultadoLeituraCarga.Falha(List.of(
                    new ErroLinhaCarga(1, "arquivo", "arquivo vazio: nenhuma linha de dado encontrada")
            ));
        }

        String texto = new String(conteudo, charset);
        String[] linhasBrutas = texto.split("\n", -1);

        record LinhaArquivo(int numeroLinha, String conteudo) {}
        List<LinhaArquivo> linhasNaoVazias = new ArrayList<>();

        for (int i = 0; i < linhasBrutas.length; i++) {
            String linha = linhasBrutas[i];
            if (linha.endsWith("\r")) {
                linha = linha.substring(0, linha.length() - 1);
            }
            if (!linha.isEmpty()) {
                linhasNaoVazias.add(new LinhaArquivo(i + 1, linha));
            }
        }

        if (linhasNaoVazias.size() <= 1) {
            return new ResultadoLeituraCarga.Falha(List.of(
                    new ErroLinhaCarga(1, "arquivo", "arquivo vazio: nenhuma linha de dado encontrada")
            ));
        }

        LinhaArquivo cabecalho = linhasNaoVazias.get(0);
        String separadorRegex = Pattern.quote(String.valueOf(separadorColuna));
        String[] colunasCabecalhoBruto = cabecalho.conteudo().split(separadorRegex, -1);
        List<String> colunasCabecalho = Arrays.stream(colunasCabecalhoBruto)
                .map(String::trim)
                .toList();

        if (!colunasCabecalho.equals(CABECALHO_ESPERADO)) {
            return new ResultadoLeituraCarga.Falha(List.of(
                    new ErroLinhaCarga(1, "cabecalho", "cabeçalho divergente do esperado: prazo_dias_uteis;prazo_dias_corridos;data_vencimento;taxa;fator_desconto")
            ));
        }

        List<ErroLinhaCarga> erros = new ArrayList<>();
        record LinhaVertice(VerticeCurva vertice, int numeroLinha) {}
        List<LinhaVertice> verticesValidos = new ArrayList<>();

        for (int i = 1; i < linhasNaoVazias.size(); i++) {
            LinhaArquivo linhaArq = linhasNaoVazias.get(i);
            int numeroLinha = linhaArq.numeroLinha();
            String linha = linhaArq.conteudo();

            String[] campos = linha.split(separadorRegex, -1);
            if (campos.length != 5) {
                erros.add(new ErroLinhaCarga(numeroLinha, "linha", "esperado 5 colunas, encontrado " + campos.length));
                continue;
            }

            int errosAntes = erros.size();

            // Campo 0: prazo_dias_uteis (obrigatório)
            String campo0 = campos[0].trim();
            int prazoDiasUteis = 0;
            if (campo0.isEmpty()) {
                erros.add(new ErroLinhaCarga(numeroLinha, "prazo_dias_uteis", "campo obrigatório vazio"));
            } else {
                try {
                    int val = Integer.parseInt(campo0);
                    if (val < 0) {
                        erros.add(new ErroLinhaCarga(numeroLinha, "prazo_dias_uteis", "não pode ser negativo: " + val));
                    } else {
                        prazoDiasUteis = val;
                    }
                } catch (NumberFormatException e) {
                    erros.add(new ErroLinhaCarga(numeroLinha, "prazo_dias_uteis", "não é um inteiro válido: \"" + campo0 + "\""));
                }
            }

            // Campo 1: prazo_dias_corridos (opcional)
            String campo1 = campos[1].trim();
            Integer prazoDiasCorridos = null;
            if (!campo1.isEmpty()) {
                try {
                    int val = Integer.parseInt(campo1);
                    if (val < 0) {
                        erros.add(new ErroLinhaCarga(numeroLinha, "prazo_dias_corridos", "não pode ser negativo: " + val));
                    } else {
                        prazoDiasCorridos = val;
                    }
                } catch (NumberFormatException e) {
                    erros.add(new ErroLinhaCarga(numeroLinha, "prazo_dias_corridos", "não é um inteiro válido: \"" + campo1 + "\""));
                }
            }

            // Campo 2: data_vencimento (opcional, formato ISO YYYY-MM-DD)
            String campo2 = campos[2].trim();
            LocalDate dataVencimento = null;
            if (!campo2.isEmpty()) {
                try {
                    dataVencimento = LocalDate.parse(campo2);
                } catch (DateTimeParseException e) {
                    erros.add(new ErroLinhaCarga(numeroLinha, "data_vencimento", "data inválida (esperado YYYY-MM-DD): \"" + campo2 + "\""));
                }
            }

            // Campo 3: taxa (obrigatório)
            String campo3 = campos[3].trim();
            BigDecimal taxa = null;
            if (campo3.isEmpty()) {
                erros.add(new ErroLinhaCarga(numeroLinha, "taxa", "campo obrigatório vazio"));
            } else {
                try {
                    taxa = ConversorDecimal.paraBigDecimal(campo3, separadorDecimal);
                } catch (IllegalArgumentException e) {
                    erros.add(new ErroLinhaCarga(numeroLinha, "taxa", e.getMessage()));
                }
            }

            // Campo 4: fator_desconto (opcional)
            String campo4 = campos[4].trim();
            BigDecimal fatorDesconto = null;
            if (!campo4.isEmpty()) {
                try {
                    fatorDesconto = ConversorDecimal.paraBigDecimal(campo4, separadorDecimal);
                } catch (IllegalArgumentException e) {
                    erros.add(new ErroLinhaCarga(numeroLinha, "fator_desconto", e.getMessage()));
                }
            }

            if (erros.size() == errosAntes) {
                VerticeCurva vertice = new VerticeCurva(prazoDiasUteis, prazoDiasCorridos, dataVencimento, taxa, fatorDesconto);
                verticesValidos.add(new LinhaVertice(vertice, numeroLinha));
            }
        }

        // Checagem de prazo_dias_uteis duplicado entre os vértices válidos
        Set<Integer> prazosVistos = new HashSet<>();
        for (LinhaVertice lv : verticesValidos) {
            int prazo = lv.vertice().prazoDiasUteis();
            if (!prazosVistos.add(prazo)) {
                erros.add(new ErroLinhaCarga(lv.numeroLinha(), "prazo_dias_uteis", "prazo duplicado: " + prazo));
            }
        }

        if (erros.isEmpty()) {
            List<VerticeCurva> listaVertices = verticesValidos.stream()
                    .map(LinhaVertice::vertice)
                    .toList();
            return new ResultadoLeituraCarga.Sucesso(listaVertices);
        }

        return new ResultadoLeituraCarga.Falha(erros);
    }
}

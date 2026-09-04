package com.poccurves.processor;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verificação estática (tarefa 8.14 do backlog): falha o build se a palavra
 * reservada {@code double} ou {@code float} aparecer no código-fonte
 * principal do serviço (D8 do design.md: nunca `double`/`float` em valor de
 * mercado). Escaneia o texto-fonte diretamente, ignorando comentários — não
 * pega uso de APIs externas cujo retorno é `double` (ex.: Apache POI
 * {@code Cell.getNumericCellValue()}), só a palavra-chave escrita no
 * próprio código deste serviço, que é o que D8 realmente proíbe.
 */
class NuncaDoubleOuFloatEmValorDeMercadoTest {

    private static final Pattern PALAVRA_RESERVADA = Pattern.compile("\\b(double|float)\\b");

    @Test
    void nenhumArquivoFonteDeclaraDoubleOuFloat() throws IOException {
        Path raiz = Path.of("src/main/java");
        assertThat(raiz).as("diretório de fonte principal deve existir").exists();

        List<String> violacoes = new ArrayList<>();
        try (Stream<Path> arquivos = Files.walk(raiz)) {
            arquivos.filter(p -> p.toString().endsWith(".java")).forEach(arquivo -> {
                List<String> linhasSemComentario = removerComentarios(lerLinhas(arquivo));
                for (int i = 0; i < linhasSemComentario.size(); i++) {
                    if (PALAVRA_RESERVADA.matcher(linhasSemComentario.get(i)).find()) {
                        violacoes.add(arquivo + ":" + (i + 1) + ": " + linhasSemComentario.get(i).trim());
                    }
                }
            });
        }

        assertThat(violacoes)
                .as("nenhum arquivo de src/main/java pode declarar double/float — D8 do design.md")
                .isEmpty();
    }

    private List<String> lerLinhas(Path arquivo) {
        try {
            return Files.readAllLines(arquivo);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** Remove comentários de linha (//) e de bloco (/* *&#47;), inclusive multilinha, preservando o resto do texto. */
    private List<String> removerComentarios(List<String> linhas) {
        List<String> resultado = new ArrayList<>();
        boolean dentroDeBloco = false;
        for (String linha : linhas) {
            StringBuilder saida = new StringBuilder();
            int i = 0;
            while (i < linha.length()) {
                if (dentroDeBloco) {
                    int fim = linha.indexOf("*/", i);
                    if (fim == -1) {
                        i = linha.length();
                    } else {
                        dentroDeBloco = false;
                        i = fim + 2;
                    }
                } else if (linha.startsWith("//", i)) {
                    break;
                } else if (linha.startsWith("/*", i)) {
                    dentroDeBloco = true;
                    i += 2;
                } else {
                    saida.append(linha.charAt(i));
                    i++;
                }
            }
            resultado.add(saida.toString());
        }
        return resultado;
    }
}

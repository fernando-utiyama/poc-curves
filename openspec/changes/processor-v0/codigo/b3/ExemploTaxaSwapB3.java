package br.com.poc.exemplo;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.zip.ZipInputStream;

/**
 * Exemplo em um arquivo só: baixa o TS{AAMMDD}.ex_ da B3 e devolve o TaxaSwap.txt.
 * Sem Spring, sem tratamento de erro elaborado: só para entender o caminho.
 *
 * .ex_ (zip) → .exe (autoextraível: começa com "MZ", o zip vem depois) → TaxaSwap.txt
 */
public class ExemploTaxaSwapB3 {

    public static void main(String[] args) throws Exception {
        LocalDate data = LocalDate.of(2026, 9, 14);

        byte[] ex = baixar(data);              // 1. download
        byte[] exe = abrirZip(ex);             // 2. primeiro nível: o .exe
        byte[] texto = abrirZip(exe);          // 3. segundo nível: o TaxaSwap.txt
        String canonico = canonizar(texto);    // 4. quebras de linha padronizadas

        System.out.println("Data do arquivo: " + dataBase(canonico));
        System.out.println("Primeira linha:  " + canonico.lines().findFirst().orElse(""));
    }

    /** 1. GET https://www.b3.com.br/pesquisapregao/download?filelist=TS260914.ex_ */
    static byte[] baixar(LocalDate data) throws IOException, InterruptedException {
        String nome = "TS" + data.format(DateTimeFormatter.ofPattern("yyMMdd")) + ".ex_";
        URI uri = URI.create("https://www.b3.com.br/pesquisapregao/download?filelist=" + nome);

        HttpResponse<byte[]> resposta = HttpClient.newHttpClient()
            .send(HttpRequest.newBuilder(uri).GET().build(), HttpResponse.BodyHandlers.ofByteArray());

        if (resposta.statusCode() != 200 || resposta.body().length == 0) {
            throw new IllegalStateException("Arquivo não disponível: HTTP " + resposta.statusCode());
        }
        return resposta.body();
    }

    /** 2 e 3. Abre o zip a partir da primeira assinatura "PK\3\4" (pula o "MZ" do .exe) e devolve o único arquivo. */
    static byte[] abrirZip(byte[] bytes) throws IOException {
        int inicio = indiceDe(bytes, new byte[]{'P', 'K', 3, 4});
        try (var zip = new ZipInputStream(new ByteArrayInputStream(bytes, inicio, bytes.length - inicio))) {
            var entrada = zip.getNextEntry();
            if (entrada == null) {
                throw new IllegalStateException("Zip vazio");
            }
            return zip.readAllBytes();
        }
    }

    /** 4. Latin-1; \r\n, \n ou \r viram \n; sem linhas vazias; \n no fim. */
    static String canonizar(byte[] texto) {
        return new String(texto, StandardCharsets.ISO_8859_1)
            .lines()
            .filter(linha -> !linha.isBlank())
            .reduce(new StringBuilder(), (sb, linha) -> sb.append(linha).append('\n'), StringBuilder::append)
            .toString();
    }

    /** Data-base: posições 12 a 19 da primeira linha (AAAAMMDD). */
    static LocalDate dataBase(String canonico) {
        String primeira = canonico.lines().findFirst().orElseThrow();
        return LocalDate.parse(primeira.substring(11, 19), DateTimeFormatter.BASIC_ISO_DATE);
    }

    private static int indiceDe(byte[] bytes, byte[] padrao) {
        for (int i = 0; i <= bytes.length - padrao.length; i++) {
            int j = 0;
            while (j < padrao.length && bytes[i + j] == padrao[j]) {
                j++;
            }
            if (j == padrao.length) {
                return i;
            }
        }
        throw new IllegalStateException("Conteúdo sem zip");
    }
}

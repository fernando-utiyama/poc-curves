package br.com.poc.adapter.out.client.b3;

import br.com.poc.application.exception.CargaException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

/**
 * Baixa o TS{AAMMDD}.ex_ da B3. Só a data entra no endereço; nenhum cabeçalho interno vai para a B3.
 * 404, corpo vazio ou corpo que não é zip = 503 ARQUIVO_INDISPONIVEL; outro erro HTTP = 502 FONTE_INDISPONIVEL.
 */
@Component
public class B3TaxaSwapClient {

    private static final DateTimeFormatter AAMMDD = DateTimeFormatter.ofPattern("yyMMdd");

    private final HttpClient http = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(30))
        .followRedirects(HttpClient.Redirect.NORMAL)
        .build();

    /** Ex.: https://www.b3.com.br/pesquisapregao/download?filelist=TS{data}.ex_ */
    @Value("${processor.b3.url}")
    private String urlModelo;

    public byte[] baixar(LocalDate dataBase) {
        URI uri = URI.create(urlModelo.replace("{data}", dataBase.format(AAMMDD)));
        HttpRequest pedido = HttpRequest.newBuilder(uri).timeout(Duration.ofSeconds(90)).GET().build();
        try {
            HttpResponse<byte[]> resposta = http.send(pedido, HttpResponse.BodyHandlers.ofByteArray());
            int status = resposta.statusCode();
            byte[] corpo = resposta.body();
            if (status == 404) {
                throw CargaException.indisponivel("Arquivo da B3 de " + dataBase + " ainda não publicado");
            }
            if (status >= 400) {
                throw CargaException.fonteIndisponivel("B3 respondeu " + status);
            }
            if (corpo == null || corpo.length == 0 || !ExtratorTaxaSwap.temZip(corpo)) {
                throw CargaException.indisponivel("Resposta da B3 de " + dataBase + " vazia ou não é zip");
            }
            return corpo;
        } catch (IOException e) {
            throw CargaException.indisponivel("Falha de rede ao baixar da B3: " + e.getMessage());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw CargaException.indisponivel("Download da B3 interrompido");
        }
    }
}

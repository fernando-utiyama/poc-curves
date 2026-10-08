package br.com.poc.adapter.out.client.b3;

import br.com.poc.application.exception.CargaException;
import br.com.poc.application.model.leiaute.LeiauteTaxaSwap;
import org.springframework.stereotype.Component;

import java.time.LocalDate;

/**
 * Do download ao texto canônico: baixar o .ex_ → extrair o TaxaSwap.txt → canonizar → conferir a data.
 * O caso de uso (CargaArquivoService) segue daí: idCarga pelo hash, original no Blob, interpretar e gravar.
 */
@Component
public class B3DownloadSiteProvedor {

    private final B3TaxaSwapClient client;

    public B3DownloadSiteProvedor(B3TaxaSwapClient client) {
        this.client = client;
    }

    /** Download: a data do conteúdo tem que ser a pedida; senão a B3 ainda não publicou a do dia (503). */
    public byte[] baixar(LocalDate dataBase) {
        byte[] canonico = paraTexto(client.baixar(dataBase));
        LocalDate dataDoArquivo = LeiauteTaxaSwap.dataBase(canonico);
        if (!dataDoArquivo.equals(dataBase)) {
            throw CargaException.indisponivel("B3 devolveu o arquivo de " + dataDoArquivo + " em vez de " + dataBase);
        }
        return canonico;
    }

    /** Upload: aceita o .ex_ (reconhecido pela assinatura de zip) ou o TaxaSwap.txt direto. */
    public byte[] preparar(byte[] enviado) {
        return paraTexto(enviado);
    }

    private static byte[] paraTexto(byte[] bytes) {
        byte[] texto = ExtratorTaxaSwap.temZip(bytes) ? ExtratorTaxaSwap.extrair(bytes) : bytes;
        return LeiauteTaxaSwap.canonizar(texto);
    }
}

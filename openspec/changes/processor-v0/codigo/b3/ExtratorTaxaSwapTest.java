package br.com.poc.adapter.out.client.b3;

import br.com.poc.application.exception.CargaException;
import br.com.poc.application.model.leiaute.LeiauteTaxaSwap;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Usa o .exe real da B3 de 2026-09-14 (docs/TS260914.exe → src/test/resources/b3/TS260914.exe, sem alterar bytes). */
class ExtratorTaxaSwapTest {

    private static byte[] exeReal() throws IOException {
        try (InputStream in = ExtratorTaxaSwapTest.class.getResourceAsStream("/b3/TS260914.exe")) {
            return in.readAllBytes();
        }
    }

    /** Monta o .ex_ como a B3 entrega: um zip com o .exe dentro. */
    private static byte[] exComo(String nome, byte[] conteudo) throws IOException {
        var saida = new ByteArrayOutputStream();
        try (var zip = new ZipOutputStream(saida)) {
            zip.putNextEntry(new ZipEntry(nome));
            zip.write(conteudo);
            zip.closeEntry();
        }
        return saida.toByteArray();
    }

    @Test
    void extraiTaxaSwapDoExReal() throws IOException {
        byte[] texto = ExtratorTaxaSwap.extrair(exComo("TS260914.exe", exeReal()));

        assertThat(new String(texto, 0, 60, StandardCharsets.ISO_8859_1))
            .startsWith("0000010010120260914T1021  LFT");
        assertThat(texto).hasSize(2_255_594);
    }

    @Test
    void canonizaEAchaADataBase() throws IOException {
        byte[] canonico = LeiauteTaxaSwap.canonizar(ExtratorTaxaSwap.extrair(exComo("TS260914.exe", exeReal())));
        String conteudo = new String(canonico, StandardCharsets.ISO_8859_1);

        assertThat(conteudo).doesNotContain("\r").endsWith("\n");
        assertThat(LeiauteTaxaSwap.dataBase(canonico)).isEqualTo(LocalDate.of(2026, 9, 14));
    }

    @Test
    void canonizarTrocaQuebrasERetiraLinhasVazias() {
        byte[] canonico = LeiauteTaxaSwap.canonizar("a\r\n\r\n  \rb\nc".getBytes(StandardCharsets.ISO_8859_1));

        assertThat(new String(canonico, StandardCharsets.ISO_8859_1)).isEqualTo("a\nb\nc\n");
    }

    @Test
    void zipComDoisArquivosEhInvalido() throws IOException {
        var saida = new ByteArrayOutputStream();
        try (var zip = new ZipOutputStream(saida)) {
            zip.putNextEntry(new ZipEntry("TaxaSwap.txt"));
            zip.write("x".getBytes(StandardCharsets.ISO_8859_1));
            zip.putNextEntry(new ZipEntry("outro.txt"));
            zip.write("y".getBytes(StandardCharsets.ISO_8859_1));
        }

        assertThatThrownBy(() -> ExtratorTaxaSwap.extrair(saida.toByteArray()))
            .isInstanceOf(CargaException.class)
            .extracting("status").isEqualTo(422);
    }

    @Test
    void conteudoSemZipEhInvalido() {
        assertThatThrownBy(() -> ExtratorTaxaSwap.extrair("<html>manutenção</html>".getBytes(StandardCharsets.ISO_8859_1)))
            .isInstanceOf(CargaException.class)
            .extracting("status").isEqualTo(422);
    }
}

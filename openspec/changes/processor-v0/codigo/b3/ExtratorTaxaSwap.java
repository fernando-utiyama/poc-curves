package br.com.poc.adapter.out.client.b3;

import br.com.poc.application.exception.CargaException;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Tira o TaxaSwap.txt do .ex_ da B3, que tem dois níveis:
 * <pre>
 * TS260914.ex_   (zip)
 *  └─ TS260914.exe   (autoextraível: stub "MZ" e, a partir de um ponto, um zip comum)
 *      └─ TaxaSwap.txt
 * </pre>
 * O ZipInputStream lê cabeçalhos locais em sequência e não pula o stub do .exe; por isso cada nível começa
 * na primeira assinatura de zip (PK\3\4). No arquivo de 2026-09-14, ela fica no byte 86036 do .exe.
 */
public final class ExtratorTaxaSwap {

    private static final byte[] ASSINATURA_ZIP = {'P', 'K', 3, 4};
    private static final String NOME_TEXTO = "TaxaSwap.txt";
    private static final int MAX_NIVEIS = 2;
    private static final long MAX_BYTES = 50L * 1024 * 1024;   // contra zip-bomba

    private ExtratorTaxaSwap() {}

    public static boolean temZip(byte[] bytes) {
        return indiceZip(bytes) >= 0;
    }

    /** Devolve os bytes do TaxaSwap.txt como estão no arquivo. Formato inesperado = 422 ARQUIVO_INVALIDO. */
    public static byte[] extrair(byte[] ex) {
        byte[] atual = ex;
        for (int nivel = 1; nivel <= MAX_NIVEIS; nivel++) {
            Entrada entrada = unicaEntrada(atual);
            if (entrada.nome().endsWith(NOME_TEXTO)) {
                return entrada.bytes();
            }
            if (!temZip(entrada.bytes())) {
                throw CargaException.invalido("Entrada " + entrada.nome() + " não é o TaxaSwap.txt nem um zip");
            }
            atual = entrada.bytes();
        }
        throw CargaException.invalido("TaxaSwap.txt não encontrado em até " + MAX_NIVEIS + " níveis");
    }

    private record Entrada(String nome, byte[] bytes) {}

    private static Entrada unicaEntrada(byte[] bytes) {
        int inicio = indiceZip(bytes);
        if (inicio < 0) {
            throw CargaException.invalido("Conteúdo sem zip");
        }
        try (var zip = new ZipInputStream(new ByteArrayInputStream(bytes, inicio, bytes.length - inicio))) {
            Entrada encontrada = null;
            for (ZipEntry e = zip.getNextEntry(); e != null; e = zip.getNextEntry()) {
                if (e.isDirectory()) {
                    continue;
                }
                if (encontrada != null) {
                    throw CargaException.invalido("Mais de um arquivo no zip: " + encontrada.nome() + ", " + e.getName());
                }
                byte[] conteudo = zip.readNBytes((int) MAX_BYTES + 1);
                if (conteudo.length > MAX_BYTES) {
                    throw CargaException.invalido("Arquivo " + e.getName() + " passa de " + MAX_BYTES + " bytes");
                }
                encontrada = new Entrada(e.getName(), conteudo);
            }
            if (encontrada == null) {
                throw CargaException.invalido("Zip vazio");
            }
            return encontrada;
        } catch (IOException e) {
            throw CargaException.invalido("Zip ilegível: " + e.getMessage());
        }
    }

    private static int indiceZip(byte[] bytes) {
        fora:
        for (int i = 0; i <= bytes.length - ASSINATURA_ZIP.length; i++) {
            for (int j = 0; j < ASSINATURA_ZIP.length; j++) {
                if (bytes[i + j] != ASSINATURA_ZIP[j]) {
                    continue fora;
                }
            }
            return i;
        }
        return -1;
    }
}

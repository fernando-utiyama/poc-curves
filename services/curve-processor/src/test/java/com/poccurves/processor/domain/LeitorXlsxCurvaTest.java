package com.poccurves.processor.domain;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class LeitorXlsxCurvaTest {

    private static final String[] CABECALHO = {
            "prazo_dias_uteis", "prazo_dias_corridos", "data_vencimento", "taxa", "fator_desconto"
    };

    /** Gera um XLSX real em memória com o cabeçalho padrão e as linhas de dado informadas. */
    private byte[] gerarXlsx(Object[]... linhasDado) {
        try (XSSFWorkbook workbook = new XSSFWorkbook()) {
            XSSFSheet sheet = workbook.createSheet("curva");

            Row cabecalho = sheet.createRow(0);
            for (int c = 0; c < CABECALHO.length; c++) {
                cabecalho.createCell(c).setCellValue(CABECALHO[c]);
            }

            for (int r = 0; r < linhasDado.length; r++) {
                Row row = sheet.createRow(r + 1);
                Object[] valores = linhasDado[r];
                for (int c = 0; c < valores.length; c++) {
                    Object valor = valores[c];
                    Cell cell = row.createCell(c);
                    if (valor == null) {
                        // não escreve nada -> célula BLANK
                        continue;
                    }
                    if (valor instanceof Integer i) {
                        cell.setCellValue(i);
                    } else if (valor instanceof Double d) {
                        cell.setCellValue(d);
                    } else {
                        cell.setCellValue(valor.toString());
                    }
                }
            }

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            workbook.write(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** Só o cabeçalho, sem linhas de dado (usado pelo teste de arquivo vazio). */
    private byte[] gerarXlsxSemLinhasDeDado() {
        try (XSSFWorkbook workbook = new XSSFWorkbook()) {
            XSSFSheet sheet = workbook.createSheet("curva");
            Row cabecalho = sheet.createRow(0);
            for (int c = 0; c < CABECALHO.length; c++) {
                cabecalho.createCell(c).setCellValue(CABECALHO[c]);
            }
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            workbook.write(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Test
    void leXlsxValidoComCelulasNumericasEDeTexto() {
        byte[] xlsx = gerarXlsx(
                new Object[]{1, 1, "2026-08-24", 14.129, 0.9994},
                new Object[]{21, 21, "2026-09-15", 14.25, 0.988}
        );

        ResultadoLeituraCarga resultado = LeitorXlsxCurva.ler(xlsx, ',');

        assertThat(resultado).isInstanceOf(ResultadoLeituraCarga.Sucesso.class);
        List<VerticeCurva> vertices = ((ResultadoLeituraCarga.Sucesso) resultado).vertices();
        assertThat(vertices).hasSize(2);

        assertThat(vertices.get(0).prazoDiasUteis()).isEqualTo(1);
        assertThat(vertices.get(0).prazoDiasCorridos()).isEqualTo(1);
        assertThat(vertices.get(0).dataVencimento()).isEqualTo(LocalDate.of(2026, 8, 24));
        assertThat(vertices.get(0).taxa()).isEqualByComparingTo("14.129");
        assertThat(vertices.get(0).fatorDesconto()).isEqualByComparingTo("0.9994");

        assertThat(vertices.get(1).prazoDiasUteis()).isEqualTo(21);
    }

    @Test
    void leCelulaDeTaxaComoTextoUsandoSeparadorDecimalDeclarado() {
        byte[] xlsx = gerarXlsx(new Object[]{1, null, null, "14,129", null});

        ResultadoLeituraCarga resultado = LeitorXlsxCurva.ler(xlsx, ',');

        assertThat(resultado).isInstanceOf(ResultadoLeituraCarga.Sucesso.class);
        VerticeCurva vertice = ((ResultadoLeituraCarga.Sucesso) resultado).vertices().get(0);
        assertThat(vertice.taxa()).isEqualByComparingTo("14.129");
    }

    @Test
    void celulasVaziasFicamNulas() {
        byte[] xlsx = gerarXlsx(new Object[]{1, null, null, 14.129, null});

        ResultadoLeituraCarga resultado = LeitorXlsxCurva.ler(xlsx, ',');

        assertThat(resultado).isInstanceOf(ResultadoLeituraCarga.Sucesso.class);
        VerticeCurva vertice = ((ResultadoLeituraCarga.Sucesso) resultado).vertices().get(0);
        assertThat(vertice.prazoDiasCorridos()).isNull();
        assertThat(vertice.dataVencimento()).isNull();
        assertThat(vertice.fatorDesconto()).isNull();
    }

    @Test
    void cabecalhoDivergenteRetornaFalha() {
        try (XSSFWorkbook workbook = new XSSFWorkbook()) {
            XSSFSheet sheet = workbook.createSheet("curva");
            Row cabecalho = sheet.createRow(0);
            cabecalho.createCell(0).setCellValue("prazo_du");
            cabecalho.createCell(1).setCellValue("prazo_dc");
            cabecalho.createCell(2).setCellValue("vencimento");
            cabecalho.createCell(3).setCellValue("taxa");
            cabecalho.createCell(4).setCellValue("fator");
            Row dado = sheet.createRow(1);
            dado.createCell(0).setCellValue(1);
            dado.createCell(3).setCellValue(14.129);

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            workbook.write(out);

            ResultadoLeituraCarga resultado = LeitorXlsxCurva.ler(out.toByteArray(), ',');

            assertThat(resultado).isInstanceOf(ResultadoLeituraCarga.Falha.class);
            ErroLinhaCarga erro = ((ResultadoLeituraCarga.Falha) resultado).erros().get(0);
            assertThat(erro.coluna()).isEqualTo("cabecalho");
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Test
    void arquivoSemLinhasDeDadoRetornaFalha() {
        ResultadoLeituraCarga resultado = LeitorXlsxCurva.ler(gerarXlsxSemLinhasDeDado(), ',');

        assertThat(resultado).isInstanceOf(ResultadoLeituraCarga.Falha.class);
        ErroLinhaCarga erro = ((ResultadoLeituraCarga.Falha) resultado).erros().get(0);
        assertThat(erro.coluna()).isEqualTo("arquivo");
        assertThat(erro.mensagem()).contains("arquivo vazio");
    }

    @Test
    void taxaInvalidaRetornaFalhaComLinhaEColunaCorretas() {
        byte[] xlsx = gerarXlsx(
                new Object[]{1, null, null, 14.129, null},
                new Object[]{21, null, null, "nao_numerico", null}
        );

        ResultadoLeituraCarga resultado = LeitorXlsxCurva.ler(xlsx, ',');

        assertThat(resultado).isInstanceOf(ResultadoLeituraCarga.Falha.class);
        ErroLinhaCarga erro = ((ResultadoLeituraCarga.Falha) resultado).erros().get(0);
        assertThat(erro.numeroLinha()).isEqualTo(3);
        assertThat(erro.coluna()).isEqualTo("taxa");
    }

    @Test
    void prazoDiasUteisDuplicadoRetornaFalha() {
        byte[] xlsx = gerarXlsx(
                new Object[]{21, null, null, 14.129, null},
                new Object[]{21, null, null, 14.25, null}
        );

        ResultadoLeituraCarga resultado = LeitorXlsxCurva.ler(xlsx, ',');

        assertThat(resultado).isInstanceOf(ResultadoLeituraCarga.Falha.class);
        ErroLinhaCarga erro = ((ResultadoLeituraCarga.Falha) resultado).erros().get(0);
        assertThat(erro.coluna()).isEqualTo("prazo_dias_uteis");
        assertThat(erro.mensagem()).contains("prazo duplicado: 21");
    }

    @Test
    void arquivoCorrompidoRetornaFalha() {
        byte[] naoEhXlsx = "isto não é um xlsx de verdade".getBytes(java.nio.charset.StandardCharsets.UTF_8);

        ResultadoLeituraCarga resultado = LeitorXlsxCurva.ler(naoEhXlsx, ',');

        assertThat(resultado).isInstanceOf(ResultadoLeituraCarga.Falha.class);
        ErroLinhaCarga erro = ((ResultadoLeituraCarga.Falha) resultado).erros().get(0);
        assertThat(erro.coluna()).isEqualTo("arquivo");
    }

    @Test
    void produzResultadoIdenticoAoCsvEquivalente() {
        byte[] xlsx = gerarXlsx(
                new Object[]{1, 1, "2026-08-24", 14.129, 0.9994},
                new Object[]{21, 21, "2026-09-15", 14.25, 0.988}
        );
        byte[] csv = ("prazo_dias_uteis;prazo_dias_corridos;data_vencimento;taxa;fator_desconto\n"
                + "1;1;2026-08-24;14.129;0.9994\n"
                + "21;21;2026-09-15;14.25;0.988\n").getBytes(java.nio.charset.StandardCharsets.UTF_8);

        ResultadoLeituraCarga resultadoXlsx = LeitorXlsxCurva.ler(xlsx, '.');
        ResultadoLeituraCarga resultadoCsv = LeitorCsvCurva.ler(csv, "UTF-8", ';', '.');

        assertThat(resultadoXlsx).isInstanceOf(ResultadoLeituraCarga.Sucesso.class);
        assertThat(resultadoCsv).isInstanceOf(ResultadoLeituraCarga.Sucesso.class);

        List<VerticeCurva> verticesXlsx = ((ResultadoLeituraCarga.Sucesso) resultadoXlsx).vertices();
        List<VerticeCurva> verticesCsv = ((ResultadoLeituraCarga.Sucesso) resultadoCsv).vertices();

        assertThat(verticesXlsx).hasSize(verticesCsv.size());
        for (int i = 0; i < verticesXlsx.size(); i++) {
            assertThat(verticesXlsx.get(i).prazoDiasUteis()).isEqualTo(verticesCsv.get(i).prazoDiasUteis());
            assertThat(verticesXlsx.get(i).prazoDiasCorridos()).isEqualTo(verticesCsv.get(i).prazoDiasCorridos());
            assertThat(verticesXlsx.get(i).dataVencimento()).isEqualTo(verticesCsv.get(i).dataVencimento());
            assertThat(verticesXlsx.get(i).taxa()).isEqualByComparingTo(verticesCsv.get(i).taxa());
            assertThat(verticesXlsx.get(i).fatorDesconto()).isEqualByComparingTo(verticesCsv.get(i).fatorDesconto());
        }
    }
}

package com.poccurves.processor.domain;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class LeitorCsvCurvaTest {

    @Test
    void leCsvValidoComPontoEVirgulaEVirgulaDecimal() {
        String csv = """
                prazo_dias_uteis;prazo_dias_corridos;data_vencimento;taxa;fator_desconto
                1;1;2026-08-24;14,129;0,9994
                21;21;2026-09-15;14,250;0,9880
                42;42;2026-10-15;14,350;0,9760
                """;

        ResultadoLeituraCarga resultado = LeitorCsvCurva.ler(
                csv.getBytes(StandardCharsets.UTF_8),
                "UTF-8",
                ';',
                ','
        );

        assertThat(resultado).isInstanceOf(ResultadoLeituraCarga.Sucesso.class);
        ResultadoLeituraCarga.Sucesso sucesso = (ResultadoLeituraCarga.Sucesso) resultado;
        List<VerticeCurva> vertices = sucesso.vertices();
        assertThat(vertices).hasSize(3);

        assertThat(vertices.get(0).prazoDiasUteis()).isEqualTo(1);
        assertThat(vertices.get(0).prazoDiasCorridos()).isEqualTo(1);
        assertThat(vertices.get(0).dataVencimento()).isEqualTo(LocalDate.of(2026, 8, 24));
        assertThat(vertices.get(0).taxa()).isEqualByComparingTo("14.129");
        assertThat(vertices.get(0).fatorDesconto()).isEqualByComparingTo("0.9994");

        assertThat(vertices.get(1).prazoDiasUteis()).isEqualTo(21);
        assertThat(vertices.get(1).prazoDiasCorridos()).isEqualTo(21);
        assertThat(vertices.get(1).dataVencimento()).isEqualTo(LocalDate.of(2026, 9, 15));
        assertThat(vertices.get(1).taxa()).isEqualByComparingTo("14.250");
        assertThat(vertices.get(1).fatorDesconto()).isEqualByComparingTo("0.9880");

        assertThat(vertices.get(2).prazoDiasUteis()).isEqualTo(42);
        assertThat(vertices.get(2).prazoDiasCorridos()).isEqualTo(42);
        assertThat(vertices.get(2).dataVencimento()).isEqualTo(LocalDate.of(2026, 10, 15));
        assertThat(vertices.get(2).taxa()).isEqualByComparingTo("14.350");
        assertThat(vertices.get(2).fatorDesconto()).isEqualByComparingTo("0.9760");
    }

    @Test
    void leCsvValidoComVirgulaComoSeparadorDeColunaEPontoDecimal() {
        String csv = """
                prazo_dias_uteis,prazo_dias_corridos,data_vencimento,taxa,fator_desconto
                1,2,2026-08-24,14.129,0.9994
                21,30,2026-09-15,14.25,0.9880
                """;

        ResultadoLeituraCarga resultado = LeitorCsvCurva.ler(
                csv.getBytes(StandardCharsets.UTF_8),
                "UTF-8",
                ',',
                '.'
        );

        assertThat(resultado).isInstanceOf(ResultadoLeituraCarga.Sucesso.class);
        ResultadoLeituraCarga.Sucesso sucesso = (ResultadoLeituraCarga.Sucesso) resultado;
        List<VerticeCurva> vertices = sucesso.vertices();
        assertThat(vertices).hasSize(2);

        assertThat(vertices.get(0).prazoDiasUteis()).isEqualTo(1);
        assertThat(vertices.get(0).prazoDiasCorridos()).isEqualTo(2);
        assertThat(vertices.get(0).taxa()).isEqualByComparingTo("14.129");

        assertThat(vertices.get(1).prazoDiasUteis()).isEqualTo(21);
        assertThat(vertices.get(1).prazoDiasCorridos()).isEqualTo(30);
        assertThat(vertices.get(1).taxa()).isEqualByComparingTo("14.25");
    }

    @Test
    void camposOpcionaisVaziosFicamNulos() {
        String csv = """
                prazo_dias_uteis;prazo_dias_corridos;data_vencimento;taxa;fator_desconto
                1;;;14,129;
                """;

        ResultadoLeituraCarga resultado = LeitorCsvCurva.ler(
                csv.getBytes(StandardCharsets.UTF_8),
                "UTF-8",
                ';',
                ','
        );

        assertThat(resultado).isInstanceOf(ResultadoLeituraCarga.Sucesso.class);
        ResultadoLeituraCarga.Sucesso sucesso = (ResultadoLeituraCarga.Sucesso) resultado;
        assertThat(sucesso.vertices()).hasSize(1);

        VerticeCurva vertice = sucesso.vertices().get(0);
        assertThat(vertice.prazoDiasUteis()).isEqualTo(1);
        assertThat(vertice.prazoDiasCorridos()).isNull();
        assertThat(vertice.dataVencimento()).isNull();
        assertThat(vertice.taxa()).isEqualByComparingTo("14.129");
        assertThat(vertice.fatorDesconto()).isNull();
    }

    @Test
    void cabecalhoDivergenteRetornaFalha() {
        String csv = """
                prazo_du;prazo_dc;vencimento;taxa;fator
                1;;;14,129;
                """;

        ResultadoLeituraCarga resultado = LeitorCsvCurva.ler(
                csv.getBytes(StandardCharsets.UTF_8),
                "UTF-8",
                ';',
                ','
        );

        assertThat(resultado).isInstanceOf(ResultadoLeituraCarga.Falha.class);
        ResultadoLeituraCarga.Falha falha = (ResultadoLeituraCarga.Falha) resultado;
        assertThat(falha.erros()).hasSize(1);

        ErroLinhaCarga erro = falha.erros().get(0);
        assertThat(erro.numeroLinha()).isEqualTo(1);
        assertThat(erro.coluna()).isEqualTo("cabecalho");
        assertThat(erro.mensagem()).contains("cabeçalho divergente do esperado");
    }

    @Test
    void arquivoVazioSemLinhasDeDadoRetornaFalha() {
        String csv = "prazo_dias_uteis;prazo_dias_corridos;data_vencimento;taxa;fator_desconto\n";

        ResultadoLeituraCarga resultado = LeitorCsvCurva.ler(
                csv.getBytes(StandardCharsets.UTF_8),
                "UTF-8",
                ';',
                ','
        );

        assertThat(resultado).isInstanceOf(ResultadoLeituraCarga.Falha.class);
        ResultadoLeituraCarga.Falha falha = (ResultadoLeituraCarga.Falha) resultado;
        assertThat(falha.erros()).hasSize(1);

        ErroLinhaCarga erro = falha.erros().get(0);
        assertThat(erro.numeroLinha()).isEqualTo(1);
        assertThat(erro.coluna()).isEqualTo("arquivo");
        assertThat(erro.mensagem()).isEqualTo("arquivo vazio: nenhuma linha de dado encontrada");
    }

    @Test
    void arquivoTotalmenteVazioRetornaFalha() {
        ResultadoLeituraCarga resultado = LeitorCsvCurva.ler(
                new byte[0],
                "UTF-8",
                ';',
                ','
        );

        assertThat(resultado).isInstanceOf(ResultadoLeituraCarga.Falha.class);
        ResultadoLeituraCarga.Falha falha = (ResultadoLeituraCarga.Falha) resultado;
        assertThat(falha.erros()).hasSize(1);

        ErroLinhaCarga erro = falha.erros().get(0);
        assertThat(erro.numeroLinha()).isEqualTo(1);
        assertThat(erro.coluna()).isEqualTo("arquivo");
        assertThat(erro.mensagem()).isEqualTo("arquivo vazio: nenhuma linha de dado encontrada");
    }

    @Test
    void taxaInvalidaRetornaFalhaComLinhaEColunaCorretas() {
        String csv = """
                prazo_dias_uteis;prazo_dias_corridos;data_vencimento;taxa;fator_desconto
                1;;;14,129;
                21;;;nao_numerico;
                """;

        ResultadoLeituraCarga resultado = LeitorCsvCurva.ler(
                csv.getBytes(StandardCharsets.UTF_8),
                "UTF-8",
                ';',
                ','
        );

        assertThat(resultado).isInstanceOf(ResultadoLeituraCarga.Falha.class);
        ResultadoLeituraCarga.Falha falha = (ResultadoLeituraCarga.Falha) resultado;
        assertThat(falha.erros()).hasSize(1);

        ErroLinhaCarga erro = falha.erros().get(0);
        assertThat(erro.numeroLinha()).isEqualTo(3);
        assertThat(erro.coluna()).isEqualTo("taxa");
        assertThat(erro.mensagem()).contains("nao_numerico");
    }

    @Test
    void prazoDiasUteisDuplicadoRetornaFalha() {
        String csv = """
                prazo_dias_uteis;prazo_dias_corridos;data_vencimento;taxa;fator_desconto
                21;;;14,129;
                21;;;14,250;
                """;

        ResultadoLeituraCarga resultado = LeitorCsvCurva.ler(
                csv.getBytes(StandardCharsets.UTF_8),
                "UTF-8",
                ';',
                ','
        );

        assertThat(resultado).isInstanceOf(ResultadoLeituraCarga.Falha.class);
        ResultadoLeituraCarga.Falha falha = (ResultadoLeituraCarga.Falha) resultado;
        assertThat(falha.erros()).hasSize(1);

        ErroLinhaCarga erro = falha.erros().get(0);
        assertThat(erro.numeroLinha()).isEqualTo(3);
        assertThat(erro.coluna()).isEqualTo("prazo_dias_uteis");
        assertThat(erro.mensagem()).isEqualTo("prazo duplicado: 21");
    }

    @Test
    void erroEmUmaLinhaNaoRetornaSucessoParcial() {
        String csv = """
                prazo_dias_uteis;prazo_dias_corridos;data_vencimento;taxa;fator_desconto
                1;;;14,129;
                21;;;invalido;
                42;;;14,350;
                """;

        ResultadoLeituraCarga resultado = LeitorCsvCurva.ler(
                csv.getBytes(StandardCharsets.UTF_8),
                "UTF-8",
                ';',
                ','
        );

        assertThat(resultado).isInstanceOf(ResultadoLeituraCarga.Falha.class);
        ResultadoLeituraCarga.Falha falha = (ResultadoLeituraCarga.Falha) resultado;
        assertThat(falha.erros()).hasSize(1);

        ErroLinhaCarga erro = falha.erros().get(0);
        assertThat(erro.numeroLinha()).isEqualTo(3);
        assertThat(erro.coluna()).isEqualTo("taxa");
    }

    @Test
    void encodingDesconhecidoRetornaFalha() {
        byte[] conteudo = "prazo_dias_uteis;prazo_dias_corridos;data_vencimento;taxa;fator_desconto\n1;;;14,129;\n"
                .getBytes(StandardCharsets.UTF_8);

        ResultadoLeituraCarga resultado = LeitorCsvCurva.ler(
                conteudo,
                "ENCODING_DESCONHECIDO_XYZ",
                ';',
                ','
        );

        assertThat(resultado).isInstanceOf(ResultadoLeituraCarga.Falha.class);
        ResultadoLeituraCarga.Falha falha = (ResultadoLeituraCarga.Falha) resultado;
        assertThat(falha.erros()).hasSize(1);

        ErroLinhaCarga erro = falha.erros().get(0);
        assertThat(erro.coluna()).isEqualTo("encoding");
        assertThat(erro.mensagem()).contains("encoding não suportado: ENCODING_DESCONHECIDO_XYZ");
    }

    @Test
    void ignoraLinhasVaziasEPreservaNumeracaoRealDasLinhas() {
        String csv = "prazo_dias_uteis;prazo_dias_corridos;data_vencimento;taxa;fator_desconto\r\n"
                + "\r\n"
                + "1;1;2026-08-24;14,129;0,9994\r\n"
                + "\r\n"
                + "21;;;taxa_invalida;\r\n";

        ResultadoLeituraCarga resultado = LeitorCsvCurva.ler(
                csv.getBytes(StandardCharsets.UTF_8),
                "UTF-8",
                ';',
                ','
        );

        assertThat(resultado).isInstanceOf(ResultadoLeituraCarga.Falha.class);
        ResultadoLeituraCarga.Falha falha = (ResultadoLeituraCarga.Falha) resultado;
        assertThat(falha.erros()).hasSize(1);

        ErroLinhaCarga erro = falha.erros().get(0);
        // Linha 1 = cabecalho, Linha 2 = vazia, Linha 3 = dado valido, Linha 4 = vazia, Linha 5 = erro
        assertThat(erro.numeroLinha()).isEqualTo(5);
        assertThat(erro.coluna()).isEqualTo("taxa");
    }

    @Test
    void validaNumeroDeColunasDiferenteDeCinco() {
        String csv = """
                prazo_dias_uteis;prazo_dias_corridos;data_vencimento;taxa;fator_desconto
                1;14,129
                """;

        ResultadoLeituraCarga resultado = LeitorCsvCurva.ler(
                csv.getBytes(StandardCharsets.UTF_8),
                "UTF-8",
                ';',
                ','
        );

        assertThat(resultado).isInstanceOf(ResultadoLeituraCarga.Falha.class);
        ResultadoLeituraCarga.Falha falha = (ResultadoLeituraCarga.Falha) resultado;
        assertThat(falha.erros()).hasSize(1);

        ErroLinhaCarga erro = falha.erros().get(0);
        assertThat(erro.numeroLinha()).isEqualTo(2);
        assertThat(erro.coluna()).isEqualTo("linha");
        assertThat(erro.mensagem()).isEqualTo("esperado 5 colunas, encontrado 2");
    }

    @Test
    void validaPrazoDiasUteisObrigatorioENaoNegativo() {
        String csv = """
                prazo_dias_uteis;prazo_dias_corridos;data_vencimento;taxa;fator_desconto
                ;;;14,129;
                -1;;;14,129;
                abc;;;14,129;
                """;

        ResultadoLeituraCarga resultado = LeitorCsvCurva.ler(
                csv.getBytes(StandardCharsets.UTF_8),
                "UTF-8",
                ';',
                ','
        );

        assertThat(resultado).isInstanceOf(ResultadoLeituraCarga.Falha.class);
        ResultadoLeituraCarga.Falha falha = (ResultadoLeituraCarga.Falha) resultado;
        assertThat(falha.erros()).hasSize(3);

        assertThat(falha.erros().get(0).numeroLinha()).isEqualTo(2);
        assertThat(falha.erros().get(0).coluna()).isEqualTo("prazo_dias_uteis");
        assertThat(falha.erros().get(0).mensagem()).isEqualTo("campo obrigatório vazio");

        assertThat(falha.erros().get(1).numeroLinha()).isEqualTo(3);
        assertThat(falha.erros().get(1).coluna()).isEqualTo("prazo_dias_uteis");
        assertThat(falha.erros().get(1).mensagem()).isEqualTo("não pode ser negativo: -1");

        assertThat(falha.erros().get(2).numeroLinha()).isEqualTo(4);
        assertThat(falha.erros().get(2).coluna()).isEqualTo("prazo_dias_uteis");
        assertThat(falha.erros().get(2).mensagem()).isEqualTo("não é um inteiro válido: \"abc\"");
    }

    @Test
    void validaPrazoDiasCorridosNaoNegativoENaoTexto() {
        String csv = """
                prazo_dias_uteis;prazo_dias_corridos;data_vencimento;taxa;fator_desconto
                1;-10;;14,129;
                2;xyz;;14,129;
                """;

        ResultadoLeituraCarga resultado = LeitorCsvCurva.ler(
                csv.getBytes(StandardCharsets.UTF_8),
                "UTF-8",
                ';',
                ','
        );

        assertThat(resultado).isInstanceOf(ResultadoLeituraCarga.Falha.class);
        ResultadoLeituraCarga.Falha falha = (ResultadoLeituraCarga.Falha) resultado;
        assertThat(falha.erros()).hasSize(2);

        assertThat(falha.erros().get(0).numeroLinha()).isEqualTo(2);
        assertThat(falha.erros().get(0).coluna()).isEqualTo("prazo_dias_corridos");
        assertThat(falha.erros().get(0).mensagem()).isEqualTo("não pode ser negativo: -10");

        assertThat(falha.erros().get(1).numeroLinha()).isEqualTo(3);
        assertThat(falha.erros().get(1).coluna()).isEqualTo("prazo_dias_corridos");
        assertThat(falha.erros().get(1).mensagem()).isEqualTo("não é um inteiro válido: \"xyz\"");
    }

    @Test
    void validaDataVencimentoFormatoInvalido() {
        String csv = """
                prazo_dias_uteis;prazo_dias_corridos;data_vencimento;taxa;fator_desconto
                1;;2026/08/24;14,129;
                """;

        ResultadoLeituraCarga resultado = LeitorCsvCurva.ler(
                csv.getBytes(StandardCharsets.UTF_8),
                "UTF-8",
                ';',
                ','
        );

        assertThat(resultado).isInstanceOf(ResultadoLeituraCarga.Falha.class);
        ResultadoLeituraCarga.Falha falha = (ResultadoLeituraCarga.Falha) resultado;
        assertThat(falha.erros()).hasSize(1);

        ErroLinhaCarga erro = falha.erros().get(0);
        assertThat(erro.numeroLinha()).isEqualTo(2);
        assertThat(erro.coluna()).isEqualTo("data_vencimento");
        assertThat(erro.mensagem()).isEqualTo("data inválida (esperado YYYY-MM-DD): \"2026/08/24\"");
    }
}

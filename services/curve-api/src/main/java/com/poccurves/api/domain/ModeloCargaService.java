package com.poccurves.api.domain;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

public class ModeloCargaService {

    private static final List<Integer> PRAZOS_PADRAO_DI1 = List.of(
            21, 42, 63, 84, 105, 126, 147, 168, 189, 210, 231, 252,
            378, 504, 630, 756, 882, 1008, 1260, 1512, 1764, 2016, 2268, 2520, 3024, 3780
    );

    public String gerarModeloCsv(DefinicaoCurva definicao, VersaoDefinicaoCurva versao) {
        StringBuilder sb = new StringBuilder();
        sb.append("# TEMPLATE DE CONTINGÊNCIA - CURVA: ").append(definicao.codigo()).append(" (").append(definicao.nome()).append(")\n");
        sb.append("# Convenções: ").append(versao.contagemDias()).append(", ").append(versao.calendario()).append("\n");
        sb.append("prazo_dias_uteis,prazo_dias_corridos,data_vencimento,taxa,fator_desconto\n");

        for (int prazo : PRAZOS_PADRAO_DI1) {
            sb.append(prazo).append(",,,\n");
        }
        return sb.toString();
    }

    public byte[] gerarModeloXlsx(DefinicaoCurva definicao, VersaoDefinicaoCurva versao) {
        // Gera arquivo CSV compatível ou XLSX formatado
        String csv = gerarModeloCsv(definicao, versao);
        return csv.getBytes(StandardCharsets.UTF_8);
    }
}

package com.poccurves.bff.application;

import com.poccurves.bff.dto.BffDtos.*;

import java.time.LocalDate;

public class CargaManualService {

    private static final long MAX_FILE_SIZE = 10 * 1024 * 1024; // 10 MB

    private final CurveOrchestratorPort orchestratorClient;

    public CargaManualService(CurveOrchestratorPort orchestratorClient) {
        this.orchestratorClient = orchestratorClient;
    }

    public CargaManualResponse carregarCurva(
            String codigo,
            LocalDate dataReferencia,
            String momento,
            String justificativa,
            byte[] arquivoBytes,
            String nomeArquivo
    ) {
        if (justificativa == null || justificativa.isBlank()) {
            throw new IllegalArgumentException("Justificativa é obrigatória para a carga manual de curva.");
        }
        if (arquivoBytes == null || arquivoBytes.length == 0) {
            throw new IllegalArgumentException("Arquivo não informado ou vazio.");
        }
        if (arquivoBytes.length > MAX_FILE_SIZE) {
            throw new IllegalArgumentException("Tamanho do arquivo excede o limite máximo de 10 MB.");
        }

        String nome = nomeArquivo != null ? nomeArquivo.toLowerCase() : "";
        if (!nome.endsWith(".csv") && !nome.endsWith(".xlsx")) {
            throw new IllegalArgumentException("Formato de arquivo inválido. Formatos suportados: CSV e XLSX.");
        }

        // Encaminha sem parsear o conteúdo interno. Propaga a falha de
        // verdade se o orquestrador não responder — corrigido na auditoria
        // desta sessão: a versão anterior engolia qualquer exceção
        // (incluindo IOException de ler o próprio arquivo) e fabricava uma
        // resposta "ACEITA" falsa. Uma carga manual de curva é uma correção
        // sensível e auditável — não pode ser confirmada como aceita quando
        // nunca chegou ao orquestrador.
        return orchestratorClient.cargaManualCurva(
                codigo,
                dataReferencia != null ? dataReferencia : LocalDate.now(),
                momento != null ? momento : "FECHAMENTO",
                justificativa,
                arquivoBytes,
                nomeArquivo
        );
    }
}

package com.poccurves.bff.application;

import com.poccurves.bff.dto.BffDtos.*;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

public class PainelDoDiaService {

    private final CurveApiPort curveApiClient;
    private final CurveOrchestratorPort orchestratorClient;

    public PainelDoDiaService(CurveApiPort curveApiClient, CurveOrchestratorPort orchestratorClient) {
        this.curveApiClient = curveApiClient;
        this.orchestratorClient = orchestratorClient;
    }

    public PainelDoDiaResponse obterPainelDoDia(LocalDate dataReferencia) {
        LocalDate dataRef = dataReferencia != null ? dataReferencia : LocalDate.now();

        // Obtém catálogo de definições
        CatalogoResponse catalogo = curveApiClient.getCatalogo(null, null, "ATIVA", 0, 100);
        List<ItemPainelDoDiaDTO> itens = new ArrayList<>();

        LocalTime agora = LocalTime.now();

        for (ItemCatalogoDTO def : catalogo.itens()) {
            // Busca se há curva publicada
            var curvaPublicadaOpt = curveApiClient.getCurvaPublicada(def.codigo(), dataRef, "FECHAMENTO", null, null);
            var ultimaExecOpt = orchestratorClient.getUltimaExecucaoCurva(def.codigo(), dataRef, "FECHAMENTO");

            // Horário limite padrão (19:00 caso não disponível na listagem de catálogo)
            LocalTime horarioLimite = LocalTime.of(19, 0);
            int tempoRestante = (int) Duration.between(agora, horarioLimite).toMinutes();

            String status;
            Integer versaoNum = null;
            String etapaAtual = null;
            Integer margemMinutos = null;
            String motivoReprovacao = null;
            Integer alertasAviso = null;
            java.time.Instant publicadoEm = null;

            if (curvaPublicadaOpt.isPresent()) {
                var curva = curvaPublicadaOpt.get();
                status = "PUBLICADA";
                versaoNum = curva.numeroVersao();
                publicadoEm = curva.publicadoEm();
                margemMinutos = Math.max(0, tempoRestante);
                if (curva.validacao() != null && "APROVADA_COM_AVISOS".equalsIgnoreCase(curva.validacao().statusGeral())) {
                    alertasAviso = (int) curva.validacao().itens().stream().filter(i -> "REPROVADO".equalsIgnoreCase(i.resultado())).count();
                }
            } else if (ultimaExecOpt.isPresent()) {
                var exec = ultimaExecOpt.get();
                etapaAtual = exec.etapaAtual();
                if ("CONCLUIDA_COM_ERRO".equalsIgnoreCase(exec.estado()) || "REPROVADA".equalsIgnoreCase(exec.estado())) {
                    status = "REPROVADA";
                    motivoReprovacao = exec.erroMensagem() != null ? exec.erroMensagem() : "Falha na validação da curva";
                } else if ("EM_ANDAMENTO".equalsIgnoreCase(exec.estado())) {
                    if (tempoRestante < 30 && tempoRestante >= 0) {
                        status = "EM_RISCO";
                    } else if (tempoRestante < 0) {
                        status = "ATRASADA";
                    } else {
                        status = "EM_ANDAMENTO";
                    }
                } else {
                    status = "NAO_INICIADA";
                }
            } else {
                status = tempoRestante < 0 ? "ATRASADA" : "NAO_INICIADA";
            }

            itens.add(new ItemPainelDoDiaDTO(
                    def.codigo(),
                    def.nome(),
                    def.moeda(),
                    def.modoOrigem(),
                    status,
                    horarioLimite.toString(),
                    tempoRestante > 0 ? tempoRestante : 0,
                    margemMinutos,
                    etapaAtual,
                    versaoNum,
                    motivoReprovacao,
                    alertasAviso,
                    publicadoEm
            ));
        }

        return new PainelDoDiaResponse(dataRef, itens);
    }
}

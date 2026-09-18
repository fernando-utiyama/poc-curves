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

        CatalogoResponse catalogo = curveApiClient.getCatalogo();
        List<ItemPainelDoDiaDTO> itens = new ArrayList<>();

        LocalTime agora = LocalTime.now();

        for (CurvaMercadoDTO def : catalogo.curvas()) {
            // "publicada" para o schema novo é a curva construída (tCurvaData) ter pontos para a data.
            var curvaConstruidaOpt = curveApiClient.getCurvaConstruida(def.tickerIndcd(), dataRef);
            boolean publicada = curvaConstruidaOpt.isPresent() && !curvaConstruidaOpt.get().pontos().isEmpty();
            var ultimaExecOpt = orchestratorClient.getUltimaExecucaoCurva(def.tickerIndcd(), dataRef, "FECHAMENTO");

            // Horário limite padrão (19:00) — já era um fallback fixo antes desta mudança
            // (o catálogo nunca carregou horário limite por curva de forma confiável).
            LocalTime horarioLimite = LocalTime.of(19, 0);
            int tempoRestante = (int) Duration.between(agora, horarioLimite).toMinutes();

            String status;
            Integer margemMinutos = null;
            String etapaAtual = null;
            String motivoReprovacao = null;

            if (publicada) {
                status = "PUBLICADA";
                margemMinutos = Math.max(0, tempoRestante);
            } else if (ultimaExecOpt.isPresent()) {
                var exec = ultimaExecOpt.get();
                etapaAtual = exec.etapaAtual();
                if ("CONCLUIDA_COM_ERRO".equalsIgnoreCase(exec.estado()) || "REPROVADA".equalsIgnoreCase(exec.estado())) {
                    status = "REPROVADA";
                    motivoReprovacao = exec.erroMensagem() != null ? exec.erroMensagem() : "Falha na construção da curva";
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
                    def.tickerIndcd(),
                    def.tickerIndcd(),
                    def.moedaNegoc(),
                    def.classfInstt(),
                    status,
                    horarioLimite.toString(),
                    tempoRestante > 0 ? tempoRestante : 0,
                    margemMinutos,
                    etapaAtual,
                    // versaoNumero/publicadoEm não existem mais (tCurvaData não tem versionamento
                    // nem carimbo de publicação) — alertasAvisoContagem idem (sem validação no schema novo).
                    null,
                    motivoReprovacao,
                    null,
                    null
            ));
        }

        return new PainelDoDiaResponse(dataRef, itens);
    }
}

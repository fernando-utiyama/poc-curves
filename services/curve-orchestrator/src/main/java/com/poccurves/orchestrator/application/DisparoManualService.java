package com.poccurves.orchestrator.application;

import com.poccurves.orchestrator.domain.CalendarioPregao;
import com.poccurves.orchestrator.domain.ExecucaoCurva;
import com.poccurves.orchestrator.domain.Faixa;
import com.poccurves.orchestrator.domain.MomentoCurva;
import com.poccurves.orchestrator.domain.TipoDisparo;
import com.poccurves.orchestrator.dto.OrchestratorDtos.DisparoManualRequest;
import com.poccurves.orchestrator.dto.OrchestratorDtos.DisparoManualResponse;
import com.poccurves.orchestrator.dto.OrchestratorDtos.ProgressoConjuntoDTO;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Serviço de orquestração para disparo manual de conjuntos de insumo.
 * <p>
 * Implementa as tarefas 4.1 a 4.3, 4.5 e 4.6 do backlog curve-orchestrator (sendo a tarefa 4.4
 * atendida pela integração com {@link CalendarioPregao}) e o encadeamento pós-aquisição até a
 * construção (tarefas 8.1 a 8.4).
 * <p>
 * <b>Decisões de Design:</b>
 * <ul>
 *   <li><b>Momento da curva (6a):</b> {@link DisparoManualRequest} não possui campo de momento.
 *       Adota-se {@link MomentoCurva#INTRADIA} como padrão para disparos manuais, por se tratarem
 *       de intervenções ad-hoc não vinculadas a um horário agendado de corte (revisar se o design
 *       de negócio definir o contrário no futuro).</li>
 *   <li><b>Faixa de execução (6b):</b> Utiliza sempre {@link Faixa#PRIORITARIA}, pois o disparo
 *       manual é por definição uma intervenção operacional prioritária.</li>
 *   <li><b>Correlation ID de lote (6c):</b> É gerado um único {@link UUID} no início do fluxo para
 *       identificar a ação do operador como um todo em todos os conjuntos requisitados, sendo retornado
 *       no campo {@code correlationId} de {@link DisparoManualResponse}.</li>
 *   <li><b>Concorrência no banco (6d):</b> a checagem de duplicidade via
 *       {@link ExecucaoCurvaRepositoryPort#buscarExecucaoAtivaParaConjuntoDados} agora tem garantia real do
 *       banco por trás: {@code ux_execucao_curva_ativa_conjunto} (migração V14) é um índice único
 *       filtrado por {@code conjunto_dados IS NOT NULL}, cobrindo exatamente este fluxo (o índice
 *       original só cobria {@code definicao_curva_id}, e um bug real — SQL Server trata múltiplos
 *       {@code NULL} como iguais num índice único, diferente de Postgres/ANSI — fazia dois conjuntos de
 *       dados diferentes na mesma data/momento colidirem; corrigido em V14, ver tasks.md 4.5).</li>
 *   <li><b>Encadeamento pós-feeder (tarefas 8.1 a 8.4):</b> Quando a aquisição no feeder retorna
 *       {@code PUBLISHED}, resolve as definições BOOTSTRAPPED que consomem o conjunto de dados
 *       adquirido (via {@link DefinicaoCurvaConsultaRepositoryPort}) e emite {@code curve.build.requested.v1}
 *       para cada uma (via {@link BuildRequestPort}), com o mesmo {@code correlacaoId} do lote como
 *       {@code runId}. A execução transiciona para {@code CONSTRUINDO} assim que ao menos um pedido
 *       é emitido com sucesso.</li>
 * </ul>
 */
public class DisparoManualService {

    private final ExecucaoCurvaRepositoryPort execucaoRepository;
    private final AquisicaoExecutionService aquisicaoExecutionService;

    public DisparoManualService(
            ExecucaoCurvaRepositoryPort execucaoRepository,
            AquisicaoExecutionService aquisicaoExecutionService
    ) {
        this.execucaoRepository = execucaoRepository;
        this.aquisicaoExecutionService = aquisicaoExecutionService;
    }

    /**
     * Executa o disparo manual para os conjuntos de insumo solicitados na data de referência informada.
     *
     * @param request dados da requisição contendo data, conjuntos e justificativa
     * @param disparadoPor identificador do usuário ou sistema solicitante
     * @return resposta estruturada com o status geral e progresso por conjunto
     */
    public DisparoManualResponse disparoManual(DisparoManualRequest request, String disparadoPor) {
        if (request == null || request.dataReferencia() == null || request.conjuntosInsumo() == null || request.conjuntosInsumo().isEmpty()) {
            throw new IllegalArgumentException("Requisição inválida: data de referência e lista de conjuntos de insumo são obrigatórios.");
        }

        if (!CalendarioPregao.ehDiaDePregao(request.dataReferencia())) {
            return new DisparoManualResponse(
                    UUID.randomUUID().toString(),
                    "RECUSADO_NAO_PREGAO",
                    "A data " + request.dataReferencia() + " não é dia de pregão.",
                    List.of()
            );
        }

        UUID correlationIdLote = UUID.randomUUID();
        List<ProgressoConjuntoDTO> progresso = new ArrayList<>();
        boolean algumNovoDisparado = false;
        int novosCount = 0;
        int emAndamentoCount = 0;

        for (String conjuntoDados : request.conjuntosInsumo()) {
            Optional<ExecucaoCurva> ativaOpt = execucaoRepository.buscarExecucaoAtivaParaConjuntoDados(
                    conjuntoDados, request.dataReferencia(), MomentoCurva.INTRADIA);

            if (ativaOpt.isPresent()) {
                ExecucaoCurva existente = ativaOpt.get();
                progresso.add(new ProgressoConjuntoDTO(
                        conjuntoDados,
                        "EM_PROCESSAMENTO",
                        "Já existe uma execução em andamento para este conjunto de dados nesta data (correlacao_id original: "
                                + existente.correlacaoId() + ")."
                ));
                emAndamentoCount++;
                continue;
            }

            algumNovoDisparado = true;
            novosCount++;

            ExecucaoCurva execucao = ExecucaoCurva.iniciar(
                    correlationIdLote,
                    null,
                    null,
                    conjuntoDados,
                    request.dataReferencia(),
                    MomentoCurva.INTRADIA,
                    TipoDisparo.MANUAL,
                    disparadoPor,
                    Faixa.PRIORITARIA,
                    null,
                    null
            );
            execucaoRepository.inserir(execucao);
            execucao.iniciarExecucao();
            execucaoRepository.atualizar(execucao);

            try {
                AquisicaoExecutionService.ExecutionResult execResult = aquisicaoExecutionService.acionarFeederEEncadear(
                        execucao, conjuntoDados, request.dataReferencia(), Faixa.PRIORITARIA, correlationIdLote
                );

                if (execResult.resultado() != null && "NO_DATA".equals(execResult.resultado().kind())) {
                    execucao.marcarSemDado(execResult.resultado().motivo());
                }

                progresso.add(new ProgressoConjuntoDTO(conjuntoDados, execResult.progressoStatus(), execResult.progressoMensagem()));
            } finally {
                execucaoRepository.atualizar(execucao);
            }
        }

        String status = algumNovoDisparado ? "DISPARADO" : "JA_EM_ANDAMENTO";
        String mensagem;
        if (algumNovoDisparado) {
            if (emAndamentoCount == 0) {
                mensagem = "Disparo manual processado com sucesso para " + novosCount + " conjunto(s) de insumo.";
            } else {
                mensagem = "Disparo manual processado: " + novosCount + " novo(s) disparado(s) e " + emAndamentoCount + " já em andamento.";
            }
        } else {
            mensagem = "Nenhum novo disparo efetuado: todos os " + emAndamentoCount + " conjunto(s) já possuem execução em andamento.";
        }

        return new DisparoManualResponse(correlationIdLote.toString(), status, mensagem, progresso);
    }
}

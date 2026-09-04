package com.poccurves.orchestrator.application;

import com.poccurves.orchestrator.domain.CalendarioPregao;
import com.poccurves.orchestrator.domain.Agendamento;
import com.poccurves.orchestrator.domain.ExecucaoCurva;
import com.poccurves.orchestrator.domain.TipoDisparo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Optional;
import java.util.UUID;

/**
 * Responsável por processar disparos agendados.
 *
 * <p><b>Decisão de Design (Polling com sleep na thread do scheduler):</b>
 * A janela de tentativa é implementada através de polling (laço while) com Thread.sleep.
 * Essa abordagem é aceitável pois os agendamentos são esparsos (não é um hot path contínuo
 * de alta concorrência) e rodam em um pool dedicado do TaskScheduler configurado
 * especificamente para essa finalidade. Isso evita a complexidade de reagendamentos em fila
 * mantendo a lógica de tentativas restrita a uma única janela de execução, simplificando a
 * manutenção do estado.
 */
public class DisparoAgendadoExecutor {

    private static final Logger log = LoggerFactory.getLogger(DisparoAgendadoExecutor.class);

    private final AgendamentoRepositoryPort agendamentoRepository;
    private final ExecucaoCurvaRepositoryPort execucaoCurvaRepository;
    private final AquisicaoExecutionService aquisicaoExecutionService;

    public DisparoAgendadoExecutor(
            AgendamentoRepositoryPort agendamentoRepository,
            ExecucaoCurvaRepositoryPort execucaoCurvaRepository,
            AquisicaoExecutionService aquisicaoExecutionService
    ) {
        this.agendamentoRepository = agendamentoRepository;
        this.execucaoCurvaRepository = execucaoCurvaRepository;
        this.aquisicaoExecutionService = aquisicaoExecutionService;
    }

    public void executar(UUID agendamentoId) {
        Optional<Agendamento> agendamentoOpt = agendamentoRepository.buscarPorId(agendamentoId);
        if (agendamentoOpt.isEmpty() || !agendamentoOpt.get().ativo()) {
            log.info("Agendamento {} ignorado: não encontrado ou inativo.", agendamentoId);
            return;
        }

        Agendamento agendamento = agendamentoOpt.get();

        if (agendamento.conjuntoDados() == null) {
            // Agendamento.criar aceita definicaoCurvaId como alvo alternativo (espelhando a dualidade de
            // execucao_curva), mas a aquisição real (FunctionMarketdataPort, buscarExecucaoAtivaParaConjuntoDados)
            // é toda indexada por conjunto de dados — não existe hoje, em nenhum serviço do monorepo, um pipeline
            // de disparo agendado para curvas IMPORTED por definicaoCurvaId (o parser de curva pronta do
            // curve-processor também está bloqueado, ver tasks.md 5.1/8.5 desse mesmo backlog). Recusar
            // explicitamente em vez de estourar IllegalArgumentException dentro de buscarExecucaoAtivaParaConjuntoDados.
            log.warn("Agendamento {} ignorado: disparo agendado por definicaoCurvaId ainda não é suportado — " +
                    "só agendamentos por conjuntoDados disparam hoje.", agendamento.id());
            return;
        }

        LocalDate dataReferencia = LocalDate.now(ZoneId.of(agendamento.fusoHorario()));

        if (!CalendarioPregao.ehDiaDePregao(dataReferencia)) {
            log.info("Agendamento {} ignorado: a data {} não é dia de pregão.", agendamento.id(), dataReferencia);
            return;
        }

        Optional<ExecucaoCurva> ativaOpt = execucaoCurvaRepository.buscarExecucaoAtivaParaConjuntoDados(
                agendamento.conjuntoDados(), dataReferencia, agendamento.momentoCurva()
        );

        if (ativaOpt.isPresent()) {
            log.info("Agendamento {} ignorado: já existe execução ativa para {} em {}.",
                    agendamento.id(), agendamento.conjuntoDados(), dataReferencia);
            return;
        }

        UUID correlationIdLote = UUID.randomUUID();
        ExecucaoCurva execucao = ExecucaoCurva.iniciar(
                correlationIdLote,
                null,
                agendamento.definicaoCurvaId(),
                agendamento.conjuntoDados(),
                dataReferencia,
                agendamento.momentoCurva(),
                TipoDisparo.AGENDADO,
                "sistema:agendamento",
                agendamento.faixa(),
                null,
                null
        );
        execucaoCurvaRepository.inserir(execucao);
        agendamentoRepository.vincularExecucao(execucao.id(), agendamento.id());

        execucao.iniciarExecucao();
        execucaoCurvaRepository.atualizar(execucao);

        LocalDateTime limite = LocalDateTime.now().plusMinutes(agendamento.janelaTentativaMinutos());

        while (true) {
            AquisicaoExecutionService.ExecutionResult result = aquisicaoExecutionService.acionarFeederEEncadear(
                    execucao, agendamento.conjuntoDados(), dataReferencia, agendamento.faixa(), correlationIdLote
            );

            if (result.resultado() != null && "NO_DATA".equals(result.resultado().kind())) {
                execucao.incrementarTentativa();
                execucaoCurvaRepository.atualizar(execucao);

                LocalDateTime agora = LocalDateTime.now();
                LocalDateTime proximaTentativa = agora.plusSeconds(agendamento.intervaloTentativaSegundos());

                if (proximaTentativa.isBefore(limite) || proximaTentativa.isEqual(limite)) {
                    try {
                        Thread.sleep(agendamento.intervaloTentativaSegundos() * 1000L);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        log.warn("Thread interrompida durante a espera entre tentativas do agendamento {}.", agendamento.id());
                        break;
                    }
                } else {
                    execucao.marcarSemDado(result.resultado().motivo());
                    execucaoCurvaRepository.atualizar(execucao);
                    break;
                }
            } else {
                break;
            }
        }
    }
}

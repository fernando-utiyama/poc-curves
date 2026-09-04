package com.poccurves.orchestrator.application;

import com.poccurves.orchestrator.application.CurveProcessorCargaManualPort.RespostaCargaInterna;
import com.poccurves.orchestrator.domain.ExecucaoCurva;
import com.poccurves.orchestrator.domain.Faixa;
import com.poccurves.orchestrator.domain.IntegracaoIndisponivelException;
import com.poccurves.orchestrator.domain.MomentoCurva;
import com.poccurves.orchestrator.domain.TipoDisparo;
import com.poccurves.orchestrator.dto.OrchestratorDtos.CargaManualResponse;
import com.poccurves.orchestrator.dto.OrchestratorDtos.ErroCargaDTO;
import com.poccurves.orchestrator.dto.OrchestratorDtos.ItemValidacaoDTO;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Serviço de orquestração para carga manual de curva.
 * <p>
 * Implementa as tarefas 5.1, 5.2, 5.3 e 5.6 do backlog curve-orchestrator.
 * <p>
 * <b>Decisões de Design e Máquina de Estados:</b>
 * <ul>
 *   <li><b>Transição de estado para caso {@code ACEITA} (Decisão 6a):</b> A máquina de estados
 *       de {@link ExecucaoCurva} não possui transição direta de {@code EXECUTANDO} para {@code CONCLUIDA}
 *       (apenas via {@code CONSTRUINDO}). Como a carga manual publica a curva de forma síncrona no
 *       {@code curve-processor} (sem etapa de construção assíncrona desacoplada), utiliza-se
 *       {@link ExecucaoCurva#iniciarConstrucao()} seguido imediatamente de {@link ExecucaoCurva#concluir()}
 *       como uma passagem para manter a conformidade com o grafo de transições válido existente.</li>
 *   <li><b>Validações reprovadas e erros de leitura:</b> Em caso de {@code REPROVADA_VALIDACAO} ou
 *       {@code ERRO_LEITURA}, a execução é finalizada com {@link ExecucaoCurva#falhar(String, String)}
 *       com os respectivos códigos de erro {@code "CARGA_REPROVADA_VALIDACAO"} e {@code "CARGA_ERRO_LEITURA"}.</li>
 *   <li><b>Resiliência de transporte:</b> Falhas de comunicação com o {@code curve-processor}
 *       ({@link IntegracaoIndisponivelException}, traduzida pelo adaptador HTTP a partir da falha real de
 *       transporte) são tratadas com a falha da execução sob o código {@code "CURVE_PROCESSOR_INDISPONIVEL"}.</li>
 * </ul>
 */
public class CargaManualService {

    private final ExecucaoCurvaRepositoryPort execucaoRepository;
    private final DefinicaoCurvaConsultaRepositoryPort definicaoCurvaConsultaRepository;
    private final CurveProcessorCargaManualPort curveProcessorCargaManualClient;

    public CargaManualService(
            ExecucaoCurvaRepositoryPort execucaoRepository,
            DefinicaoCurvaConsultaRepositoryPort definicaoCurvaConsultaRepository,
            CurveProcessorCargaManualPort curveProcessorCargaManualClient
    ) {
        this.execucaoRepository = execucaoRepository;
        this.definicaoCurvaConsultaRepository = definicaoCurvaConsultaRepository;
        this.curveProcessorCargaManualClient = curveProcessorCargaManualClient;
    }

    /**
     * Realiza a carga manual de um arquivo de curva.
     *
     * @param codigo código identificador da definição da curva
     * @param dataReferencia data de referência da curva
     * @param momento momento da curva (ABERTURA, INTRADIA, FECHAMENTO)
     * @param justificativa justificativa operacional obrigatória
     * @param arquivo bytes do arquivo enviado
     * @param nomeArquivo nome original do arquivo
     * @param disparadoPor usuário solicitante
     * @return resposta estruturada contendo o correlationId, status, mensagem e validações/erros
     */
    public CargaManualResponse carregarCurva(
            String codigo, LocalDate dataReferencia, String momento, String justificativa,
            byte[] arquivo, String nomeArquivo, String disparadoPor
    ) {
        if (justificativa == null || justificativa.isBlank()) {
            throw new IllegalArgumentException("Justificativa é obrigatória para a carga manual de curva.");
        }
        if (arquivo == null || arquivo.length == 0) {
            throw new IllegalArgumentException("Arquivo não informado ou vazio.");
        }

        Optional<UUID> definicaoCurvaIdOpt = definicaoCurvaConsultaRepository.buscarIdPorCodigo(codigo);
        if (definicaoCurvaIdOpt.isEmpty()) {
            return new CargaManualResponse(
                    UUID.randomUUID().toString(),
                    "ERRO_LEITURA",
                    "Código de curva não encontrado: " + codigo,
                    List.of(),
                    List.of()
            );
        }

        MomentoCurva momentoCurva = MomentoCurva.valueOf(momento);
        UUID correlacaoId = UUID.randomUUID();

        ExecucaoCurva execucao = ExecucaoCurva.iniciar(
                correlacaoId,
                null,
                definicaoCurvaIdOpt.get(),
                null,
                dataReferencia,
                momentoCurva,
                TipoDisparo.CARGA_MANUAL,
                disparadoPor,
                Faixa.PRIORITARIA,
                null,
                null
        );

        execucaoRepository.inserir(execucao);
        execucao.iniciarExecucao();
        execucaoRepository.atualizar(execucao);

        try {
            RespostaCargaInterna resposta = curveProcessorCargaManualClient.carregar(
                    codigo, dataReferencia, momento, justificativa, disparadoPor, execucao.id(), arquivo, nomeArquivo);

            List<ErroCargaDTO> errosLeitura = resposta.errosLeitura() != null
                    ? resposta.errosLeitura().stream()
                            .map(e -> new ErroCargaDTO(e.numeroLinha(), e.coluna(), e.mensagem()))
                            .toList()
                    : List.of();

            List<ItemValidacaoDTO> validacoesReprovadas = resposta.validacoes() != null
                    ? resposta.validacoes().stream()
                            .filter(v -> "REPROVADO".equals(v.resultado()))
                            .map(v -> new ItemValidacaoDTO(v.identificador(), v.classificacao(), v.resultado(), v.medidaObservada(), v.limiteAplicado(), v.detalhe()))
                            .toList()
                    : List.of();

            switch (resposta.status()) {
                case "ACEITA" -> {
                    execucao.iniciarConstrucao();
                    execucao.concluir();
                }
                case "REPROVADA_VALIDACAO" -> execucao.falhar("CARGA_REPROVADA_VALIDACAO", resposta.mensagem());
                case "ERRO_LEITURA" -> execucao.falhar("CARGA_ERRO_LEITURA", resposta.mensagem());
                default -> throw new IllegalStateException("status de resposta desconhecido: " + resposta.status());
            }

            return new CargaManualResponse(correlacaoId.toString(), resposta.status(), resposta.mensagem(), errosLeitura, validacoesReprovadas);
        } catch (IntegracaoIndisponivelException e) {
            execucao.falhar("CURVE_PROCESSOR_INDISPONIVEL", "Falha de transporte ao acionar o curve-processor: " + e.getMessage());
            return new CargaManualResponse(correlacaoId.toString(), "ERRO_LEITURA", "curve-processor indisponível: " + e.getMessage(), List.of(), List.of());
        } finally {
            execucaoRepository.atualizar(execucao);
        }
    }
}

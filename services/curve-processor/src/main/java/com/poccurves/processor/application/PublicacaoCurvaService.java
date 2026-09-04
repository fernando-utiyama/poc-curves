package com.poccurves.processor.application;

import com.poccurves.processor.domain.BateriaValidacaoCarga;
import com.poccurves.processor.domain.CurvaNaoMapeadaException;
import com.poccurves.processor.domain.CurvaVaziaException;
import com.poccurves.processor.domain.DefinicaoCurvaResumo;
import com.poccurves.processor.domain.IncoerenciaModoOrigemException;
import com.poccurves.processor.domain.ModoOrigem;
import com.poccurves.processor.domain.MomentoCurva;
import com.poccurves.processor.domain.OrigemVersao;
import com.poccurves.processor.domain.ProcedenciaCurva;
import com.poccurves.processor.domain.ResultadoPublicacaoCarga;
import com.poccurves.processor.domain.ResultadoTesteCarga;
import com.poccurves.processor.domain.VersaoCurva;
import com.poccurves.processor.domain.VerticeCurva;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Orquestra a publicação de uma curva pronta (origem IMPORTADA — curva
 * pronta recebida da B3, tarefas 5.2-5.9 do backlog do serviço) ou
 * carregada manualmente (origem CARREGADA — seção 6, ainda não wireada
 * aqui). Tudo numa única transação: resolve a definição, valida coerência,
 * marca a versão anterior publicada como substituída, calcula o próximo
 * número de versão, insere a nova versão + vértices + proveniência, e
 * publica — ou reverte tudo se qualquer passo falhar.
 * <p>
 * Nunca escreve em definicao_curva/versao_definicao_curva (fronteira de
 * leitura da credencial restrita, tarefa 1.6) — só lê para resolver.
 */
public class PublicacaoCurvaService {

    private final DefinicaoCurvaLeituraRepositoryPort definicaoCurvaLeituraRepository;
    private final VersaoCurvaRepositoryPort versaoCurvaRepository;
    private final VerticeCurvaRepositoryPort verticeCurvaRepository;
    private final ProcedenciaCurvaRepositoryPort procedenciaCurvaRepository;
    private final ValidacaoCurvaRepositoryPort validacaoCurvaRepository;
    private final BateriaValidacaoCarga bateriaValidacaoCarga;

    public PublicacaoCurvaService(
            DefinicaoCurvaLeituraRepositoryPort definicaoCurvaLeituraRepository,
            VersaoCurvaRepositoryPort versaoCurvaRepository,
            VerticeCurvaRepositoryPort verticeCurvaRepository,
            ProcedenciaCurvaRepositoryPort procedenciaCurvaRepository,
            ValidacaoCurvaRepositoryPort validacaoCurvaRepository,
            BateriaValidacaoCarga bateriaValidacaoCarga
    ) {
        this.definicaoCurvaLeituraRepository = definicaoCurvaLeituraRepository;
        this.versaoCurvaRepository = versaoCurvaRepository;
        this.verticeCurvaRepository = verticeCurvaRepository;
        this.procedenciaCurvaRepository = procedenciaCurvaRepository;
        this.validacaoCurvaRepository = validacaoCurvaRepository;
        this.bateriaValidacaoCarga = bateriaValidacaoCarga;
    }

    /**
     * Publica uma curva pronta recebida da B3 (payloadKind READY_CURVE).
     *
     * @throws CurvaNaoMapeadaException      se {@code curveIdOrigem} não resolver para nenhuma definicao_curva (UNMAPPED_CURVE)
     * @throws IncoerenciaModoOrigemException se a definição resolvida não for {@code ModoOrigem.IMPORTED} (BOOTSTRAPPED rejeitada aqui — tarefa 5.8)
     * @throws CurvaVaziaException           se {@code vertices} estiver vazia (EMPTY_CURVE)
     */
    @Transactional
    public VersaoCurva publicarCurvaImportada(
            String curveIdOrigem,
            LocalDate dataReferencia,
            MomentoCurva momentoCurva,
            UUID execucaoCurvaId,
            long loteIngestaoId,
            String referenciasInsumo,
            String hashConjuntoInsumos,
            List<VerticeCurva> vertices
    ) {
        DefinicaoCurvaResumo definicao = definicaoCurvaLeituraRepository.resolverPorCodigo(curveIdOrigem)
                .orElseThrow(() -> new CurvaNaoMapeadaException(curveIdOrigem));

        if (definicao.modoOrigem() != ModoOrigem.IMPORTED) {
            throw new IncoerenciaModoOrigemException("READY_CURVE", definicao.modoOrigem());
        }

        if (vertices == null || vertices.isEmpty()) {
            throw new CurvaVaziaException(curveIdOrigem);
        }

        ProcedenciaCurva procedencia = ProcedenciaCurva.importada(
                execucaoCurvaId, loteIngestaoId, referenciasInsumo, hashConjuntoInsumos);

        return publicar(definicao, dataReferencia, momentoCurva, OrigemVersao.IMPORTADA, execucaoCurvaId, vertices, procedencia);
    }

    /**
     * Publica (ou reprova) uma carga manual de curva (seção 6 do backlog):
     * grava em EM_VALIDACAO, roda a bateria de validação, publica só se
     * nenhum teste bloqueante reprovar — senão a versão fica REPROVADA e a
     * versão PUBLICADA anterior (se houver) permanece vigente, intocada.
     * Reconhece recarga do mesmo arquivo pela hash (tarefa 6.10): se já
     * existe uma procedência com esse hash_arquivo, não roda nada de novo
     * e devolve a versão já existente ({@code recarga = true}).
     *
     * @throws CurvaNaoMapeadaException se {@code curveCode} não resolver para nenhuma definicao_curva
     * @throws CurvaVaziaException      se {@code vertices} estiver vazia
     */
    @Transactional
    public ResultadoPublicacaoCarga publicarCurvaCarregada(
            String curveCode,
            LocalDate dataReferencia,
            MomentoCurva momentoCurva,
            UUID execucaoCurvaId,
            List<VerticeCurva> vertices,
            String arquivoCarga,
            String hashArquivo,
            String carregadoPor,
            String justificativa
    ) {
        DefinicaoCurvaResumo definicao = definicaoCurvaLeituraRepository.resolverPorCodigo(curveCode)
                .orElseThrow(() -> new CurvaNaoMapeadaException(curveCode));

        if (vertices == null || vertices.isEmpty()) {
            throw new CurvaVaziaException(curveCode);
        }

        Optional<UUID> versaoExistenteId = procedenciaCurvaRepository.buscarVersaoCurvaIdPorHashArquivo(hashArquivo);
        if (versaoExistenteId.isPresent()) {
            VersaoCurva versaoExistente = versaoCurvaRepository.buscarPorId(versaoExistenteId.get())
                    .orElseThrow(() -> new IllegalStateException(
                            "procedencia_curva referencia uma versao_curva inexistente: " + versaoExistenteId.get()));
            return new ResultadoPublicacaoCarga(versaoExistente, List.of(), true);
        }

        List<ResultadoTesteCarga> resultadosValidacao = bateriaValidacaoCarga.executar(vertices);
        boolean aprovada = bateriaValidacaoCarga.aprovada(resultadosValidacao);

        int proximoNumeroVersao = versaoCurvaRepository
                .buscarMaiorNumeroVersao(definicao.definicaoCurvaId(), dataReferencia, momentoCurva)
                .map(n -> n + 1)
                .orElse(1);

        VersaoCurva novaVersao = VersaoCurva.criar(
                definicao.definicaoCurvaId(), definicao.versaoDefinicaoCurvaId(), dataReferencia,
                momentoCurva, proximoNumeroVersao, OrigemVersao.CARREGADA, execucaoCurvaId);
        versaoCurvaRepository.inserir(novaVersao);

        verticeCurvaRepository.inserirTodos(novaVersao.id(), vertices);

        ProcedenciaCurva procedencia = ProcedenciaCurva.carregada(execucaoCurvaId, arquivoCarga, hashArquivo, carregadoPor, justificativa);
        procedenciaCurvaRepository.inserir(novaVersao.id(), procedencia, definicao.numeroVersaoDefinicao());

        validacaoCurvaRepository.inserirTodos(novaVersao.id(), resultadosValidacao);

        if (aprovada) {
            versaoCurvaRepository.buscarPublicadaAtual(definicao.definicaoCurvaId(), dataReferencia, momentoCurva)
                    .ifPresent(anterior -> {
                        anterior.substituir();
                        versaoCurvaRepository.atualizar(anterior);
                    });
            novaVersao.publicar();
        } else {
            novaVersao.reprovar();
        }
        versaoCurvaRepository.atualizar(novaVersao);

        return new ResultadoPublicacaoCarga(novaVersao, resultadosValidacao, false);
    }

    private VersaoCurva publicar(
            DefinicaoCurvaResumo definicao,
            LocalDate dataReferencia,
            MomentoCurva momentoCurva,
            OrigemVersao origemVersao,
            UUID execucaoCurvaId,
            List<VerticeCurva> vertices,
            ProcedenciaCurva procedencia
    ) {
        versaoCurvaRepository.buscarPublicadaAtual(definicao.definicaoCurvaId(), dataReferencia, momentoCurva)
                .ifPresent(anterior -> {
                    anterior.substituir();
                    versaoCurvaRepository.atualizar(anterior);
                });

        int proximoNumeroVersao = versaoCurvaRepository
                .buscarMaiorNumeroVersao(definicao.definicaoCurvaId(), dataReferencia, momentoCurva)
                .map(n -> n + 1)
                .orElse(1);

        VersaoCurva novaVersao = VersaoCurva.criar(
                definicao.definicaoCurvaId(), definicao.versaoDefinicaoCurvaId(), dataReferencia,
                momentoCurva, proximoNumeroVersao, origemVersao, execucaoCurvaId);
        versaoCurvaRepository.inserir(novaVersao);

        verticeCurvaRepository.inserirTodos(novaVersao.id(), vertices);
        procedenciaCurvaRepository.inserir(novaVersao.id(), procedencia, definicao.numeroVersaoDefinicao());

        novaVersao.publicar();
        versaoCurvaRepository.atualizar(novaVersao);

        return novaVersao;
    }
}

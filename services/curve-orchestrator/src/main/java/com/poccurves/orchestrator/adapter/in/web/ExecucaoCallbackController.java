package com.poccurves.orchestrator.adapter.in.web;

import com.poccurves.orchestrator.application.ExecucaoCurvaRepositoryPort;
import com.poccurves.orchestrator.domain.ExecucaoCurva;
import com.poccurves.orchestrator.dto.OrchestratorDtos;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Optional;
import java.util.UUID;

/**
 * Controller de callback para recepção do desfecho de construção de curvas pelo {@code curve-engine}.
 */
@RestController
@RequestMapping("/api/v1/execucoes")
public class ExecucaoCallbackController {

    private static final Logger log = LoggerFactory.getLogger(ExecucaoCallbackController.class);

    private final ExecucaoCurvaRepositoryPort execucaoRepository;

    public ExecucaoCallbackController(ExecucaoCurvaRepositoryPort execucaoRepository) {
        this.execucaoRepository = execucaoRepository;
    }

    @PostMapping("/{executionId}/concluida")
    public ResponseEntity<Void> callbackConcluida(
            @PathVariable UUID executionId,
            @RequestBody OrchestratorDtos.ConclusaoConstrucaoRequest request) {

        Optional<ExecucaoCurva> execucaoOpt = execucaoRepository.buscarPorId(executionId);
        if (execucaoOpt.isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        ExecucaoCurva execucao = execucaoOpt.get();

        try {
            if (request != null && "PUBLICADA".equals(request.status())) {
                execucao.concluir();
            } else {
                String motivo = (request != null && request.motivo() != null && !request.motivo().isBlank())
                        ? request.motivo()
                        : "Falha não especificada pelo curve-engine";
                execucao.falhar("FALHA_CONSTRUCAO_ENGINE", motivo);
            }
        } catch (IllegalStateException e) {
            log.info("Callback duplicado ignorado (idempotência) para executionId={}: {}", executionId, e.getMessage());
            return ResponseEntity.ok().build();
        }

        execucaoRepository.atualizar(execucao);
        return ResponseEntity.ok().build();
    }
}

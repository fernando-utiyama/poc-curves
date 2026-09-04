package com.poccurves.orchestrator.adapter.in.web;

import com.poccurves.orchestrator.application.AgendamentoService;
import com.poccurves.orchestrator.domain.Agendamento;
import com.poccurves.orchestrator.domain.Faixa;
import com.poccurves.orchestrator.domain.MomentoCurva;
import com.poccurves.orchestrator.dto.OrchestratorDtos.AgendamentoComUltimaExecucaoDTO;
import com.poccurves.orchestrator.dto.OrchestratorDtos.AgendamentoRequest;
import com.poccurves.orchestrator.dto.OrchestratorDtos.AgendamentoResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/v1/agendamentos")
public class AgendamentoController {

    private final AgendamentoService agendamentoService;

    public AgendamentoController(AgendamentoService agendamentoService) {
        this.agendamentoService = agendamentoService;
    }

    @PostMapping
    public ResponseEntity<AgendamentoResponse> cadastrar(
            @RequestBody AgendamentoRequest request,
            @RequestHeader(value = "X-User", defaultValue = "sistema") String usuario
    ) {
        Agendamento agendamento = agendamentoService.cadastrar(
                request.definicaoCurvaId(),
                request.conjuntoDados(),
                MomentoCurva.valueOf(request.momentoCurva()),
                Faixa.valueOf(request.faixa()),
                request.expressaoHorario(),
                request.fusoHorario(),
                request.janelaTentativaMinutos(),
                request.intervaloTentativaSegundos(),
                usuario
        );
        return ResponseEntity.ok(toResponse(agendamento));
    }

    @PutMapping("/{id}")
    public ResponseEntity<AgendamentoResponse> editar(
            @PathVariable UUID id,
            @RequestBody AgendamentoRequest request
    ) {
        Agendamento agendamento = agendamentoService.editar(
                id,
                request.expressaoHorario(),
                request.fusoHorario(),
                request.janelaTentativaMinutos(),
                request.intervaloTentativaSegundos(),
                Faixa.valueOf(request.faixa())
        );
        return ResponseEntity.ok(toResponse(agendamento));
    }

    @PostMapping("/{id}/ativar")
    public ResponseEntity<AgendamentoResponse> ativar(@PathVariable UUID id) {
        Agendamento agendamento = agendamentoService.ativar(id);
        return ResponseEntity.ok(toResponse(agendamento));
    }

    @PostMapping("/{id}/desativar")
    public ResponseEntity<AgendamentoResponse> desativar(@PathVariable UUID id) {
        Agendamento agendamento = agendamentoService.desativar(id);
        return ResponseEntity.ok(toResponse(agendamento));
    }

    @GetMapping
    public ResponseEntity<List<AgendamentoComUltimaExecucaoDTO>> listarComUltimaExecucao() {
        List<AgendamentoComUltimaExecucaoDTO> resultados = agendamentoService.buscarComUltimaExecucao()
                .stream()
                .map(r -> new AgendamentoComUltimaExecucaoDTO(
                        toResponse(r.agendamento()),
                        r.ultimaExecucaoEstado(),
                        r.ultimaExecucaoIniciadoEm(),
                        r.ultimaExecucaoFinalizadoEm(),
                        r.ultimaExecucaoMotivoSemDado()
                ))
                .collect(Collectors.toList());

        return ResponseEntity.ok(resultados);
    }

    private AgendamentoResponse toResponse(Agendamento a) {
        return new AgendamentoResponse(
                a.id(),
                a.definicaoCurvaId(),
                a.conjuntoDados(),
                a.momentoCurva().name(),
                a.faixa().name(),
                a.expressaoHorario(),
                a.fusoHorario(),
                a.janelaTentativaMinutos(),
                a.intervaloTentativaSegundos(),
                a.ativo(),
                a.criadoPor(),
                a.criadoEm(),
                a.atualizadoEm()
        );
    }
}

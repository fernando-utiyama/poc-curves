package com.poccurves.bff.application;

import com.poccurves.bff.dto.BffDtos.*;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public class ModelosService {

    private final CurveEnginePort engineClient;
    private final CurveApiPort curveApiClient;

    public ModelosService(CurveEnginePort engineClient, CurveApiPort curveApiClient) {
        this.engineClient = engineClient;
        this.curveApiClient = curveApiClient;
    }

    public ModelosResponse listarModelos() {
        // TODO gap real, encontrado na auditoria desta sessão: dados fixos
        // (não vem de curve-api nem de curve-engine) — nenhum dos dois expõe
        // hoje um endpoint de listagem de todos os modelos (ModeloCurvaRepository,
        // em curve-api, só busca por id/código individual). Não corrigido aqui
        // porque exigiria construir essa capacidade nova no backend, fora do
        // escopo de "corrigir o que foi encontrado" — documentado, não escondido.
        List<ModeloDTO> modelos = List.of(
                new ModeloDTO(UUID.randomUUID(), "BUILTIN_PRE_DI1", "Modelo Padrão DI1 Pré", "BUILTIN", "ATIVO", "builtin-sha256", Instant.now()),
                new ModeloDTO(UUID.randomUUID(), "GROOVY_CUSTOM_FLAT", "Modelo Custom Flat Forward", "GROOVY", "ATIVO", "groovy-sha256", Instant.now())
        );
        return new ModelosResponse(modelos);
    }

    public ImportarModeloResponse importarModeloGroovy(ImportarModeloGroovyRequest request) {
        if (request.codigo() == null || request.codigo().isBlank()) {
            throw new IllegalArgumentException("Código do modelo é obrigatório.");
        }
        if (request.scriptGroovy() == null || request.scriptGroovy().isBlank()) {
            throw new IllegalArgumentException("Código fonte Groovy não pode ser vazio.");
        }

        // Propaga a falha de verdade se o curve-engine não responder —
        // corrigido na auditoria desta sessão: a versão anterior engolia
        // qualquer exceção e fabricava um sha256 e um "VALIDO" falsos.
        // Importar um modelo de precificação com validação fabricada é
        // particularmente perigoso — o modelo entraria em uso achando que
        // foi compilado e validado quando não foi.
        return engineClient.validarScriptGroovy(request);
    }
}

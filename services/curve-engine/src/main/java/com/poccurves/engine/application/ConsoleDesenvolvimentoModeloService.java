package com.poccurves.engine.application;
import com.poccurves.engine.domain.construcao.InsumoDI1;
import com.poccurves.engine.domain.construcao.ModeloConstrucaoException;
import com.poccurves.engine.domain.construcao.ModeloCurva;
import com.poccurves.engine.domain.curva.CurvaJuros;

import com.poccurves.engine.dto.EngineDtos.*;

import java.util.List;

/**
 * Console local de desenvolvimento de modelo (tarefa 7.11 do backlog curve-engine) — testa um
 * script Groovy contra insumos informados pelo desenvolvedor sem persistir nada. O adaptador
 * HTTP que expõe isto é {@code @Profile("local")}; esta classe em si não sabe nada sobre perfil
 * Spring (isso é concern de adaptador/config, não de caso de uso).
 */
public class ConsoleDesenvolvimentoModeloService {

    private final ModeloConstrucaoPort groovyModeloConstrucao;

    public ConsoleDesenvolvimentoModeloService(ModeloConstrucaoPort groovyModeloConstrucao) {
        this.groovyModeloConstrucao = groovyModeloConstrucao;
    }

    public TestarScriptGroovyResponse testar(TestarScriptGroovyRequest request) {
        if (request.scriptGroovy() == null || request.scriptGroovy().isBlank()) {
            return new TestarScriptGroovyResponse("ERRO_COMPILACAO", "script Groovy não pode ser vazio", List.of());
        }

        List<InsumoDI1> insumos = request.insumos() == null ? List.of() : request.insumos().stream()
                .map(i -> new InsumoDI1(i.ticker(), i.taxaAjuste(), i.diasUteisVencimento(), i.dataVencimento()))
                .toList();

        ModeloCurva modeloTemporario = ModeloCurva.importarGroovy(
                "CONSOLE_DEV_TEMP", "console de desenvolvimento", request.scriptGroovy(), "n/a", "console-dev");

        try {
            CurvaJuros curva = groovyModeloConstrucao.construir(modeloTemporario, insumos);
            return new TestarScriptGroovyResponse("OK", "script executado com sucesso", curva.vertices());
        } catch (ModeloConstrucaoException e) {
            String status = e.fase() == ModeloConstrucaoException.Fase.COMPILACAO ? "ERRO_COMPILACAO" : "ERRO_EXECUCAO";
            return new TestarScriptGroovyResponse(status, e.getMessage(), List.of());
        }
    }
}

package com.poccurves.engine.application.usecase;
import com.poccurves.engine.application.model.CurvaJuros;
import com.poccurves.engine.application.model.DefinicaoResolvida;
import com.poccurves.engine.application.model.InsumoDI1;
import com.poccurves.engine.application.model.ModeloCurva;
import com.poccurves.engine.application.model.Vertice;
import com.poccurves.engine.application.port.DefinicaoCurvaResolutionRepositoryPort;
import com.poccurves.engine.application.port.InsumoDI1RepositoryPort;
import com.poccurves.engine.application.port.ModeloCurvaRepositoryPort;
import com.poccurves.engine.application.service.ModeloConstrucaoResolver;

import com.poccurves.engine.dto.EngineDtos.*;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Compara a curva que dois modelos produziriam para a mesma curva/data, sem publicar nada
 * (tarefa 7.10 do backlog curve-engine) — constrói as duas hipoteticamente a partir dos mesmos
 * insumos reais resolvidos para a data, nunca grava versão nem vértice.
 */
public class CompararModelosService {

    private final DefinicaoCurvaResolutionRepositoryPort definicaoCurvaResolutionRepository;
    private final InsumoDI1RepositoryPort insumoDI1Repository;
    private final ModeloCurvaRepositoryPort modeloCurvaRepository;
    private final ModeloConstrucaoResolver modeloConstrucaoResolver;

    public CompararModelosService(
            DefinicaoCurvaResolutionRepositoryPort definicaoCurvaResolutionRepository,
            InsumoDI1RepositoryPort insumoDI1Repository,
            ModeloCurvaRepositoryPort modeloCurvaRepository,
            ModeloConstrucaoResolver modeloConstrucaoResolver) {
        this.definicaoCurvaResolutionRepository = definicaoCurvaResolutionRepository;
        this.insumoDI1Repository = insumoDI1Repository;
        this.modeloCurvaRepository = modeloCurvaRepository;
        this.modeloConstrucaoResolver = modeloConstrucaoResolver;
    }

    public ComparacaoResponse comparar(ComparacaoModelosRequest request) {
        DefinicaoResolvida definicao = definicaoCurvaResolutionRepository
                .resolverVigente(request.codigoCurva(), request.dataReferencia())
                .orElseThrow(() -> new IllegalStateException(String.format(
                        "definição não encontrada ou sem versão vigente para curveCode=%s e referenceDate=%s",
                        request.codigoCurva(), request.dataReferencia())));

        ModeloCurva modeloA = modeloCurvaRepository.buscarPorCodigo(request.modeloA())
                .orElseThrow(() -> new IllegalStateException("modelo não encontrado: " + request.modeloA()));
        ModeloCurva modeloB = modeloCurvaRepository.buscarPorCodigo(request.modeloB())
                .orElseThrow(() -> new IllegalStateException("modelo não encontrado: " + request.modeloB()));

        List<InsumoDI1> insumos = insumoDI1Repository.buscarInsumosDI1(definicao.vinculosFonte(), request.dataReferencia());

        CurvaJuros curvaA = modeloConstrucaoResolver.construir(modeloA, insumos);
        CurvaJuros curvaB = modeloConstrucaoResolver.construir(modeloB, insumos);

        List<ItemComparacaoDTO> diferencas = mesclar(curvaA, curvaB);

        return new ComparacaoResponse(request.dataReferencia(), modeloA.codigo(), modeloB.codigo(), diferencas);
    }

    private List<ItemComparacaoDTO> mesclar(CurvaJuros curvaA, CurvaJuros curvaB) {
        Map<Integer, Vertice> porPrazoA = new LinkedHashMap<>();
        for (Vertice v : curvaA.vertices()) {
            porPrazoA.put(v.prazoDiasUteis(), v);
        }
        Map<Integer, Vertice> porPrazoB = new LinkedHashMap<>();
        for (Vertice v : curvaB.vertices()) {
            porPrazoB.put(v.prazoDiasUteis(), v);
        }

        List<Integer> todosPrazos = new ArrayList<>();
        todosPrazos.addAll(porPrazoA.keySet());
        for (Integer prazo : porPrazoB.keySet()) {
            if (!todosPrazos.contains(prazo)) {
                todosPrazos.add(prazo);
            }
        }
        todosPrazos.sort(Integer::compareTo);

        List<ItemComparacaoDTO> resultado = new ArrayList<>();
        for (Integer prazo : todosPrazos) {
            Vertice vA = porPrazoA.get(prazo);
            Vertice vB = porPrazoB.get(prazo);

            if (vA != null && vB != null) {
                BigDecimal diferencaBps = vB.taxa().subtract(vA.taxa()).multiply(BigDecimal.valueOf(100));
                resultado.add(new ItemComparacaoDTO(prazo, vA.taxa(), vB.taxa(), diferencaBps,
                        vA.fatorDesconto(), vB.fatorDesconto(), "COINCIDENTE"));
            } else if (vA != null) {
                resultado.add(new ItemComparacaoDTO(prazo, vA.taxa(), null, null, vA.fatorDesconto(), null, "PRESENTE_APENAS_EM_A"));
            } else {
                resultado.add(new ItemComparacaoDTO(prazo, null, vB.taxa(), null, null, vB.fatorDesconto(), "PRESENTE_APENAS_EM_B"));
            }
        }
        return resultado;
    }
}

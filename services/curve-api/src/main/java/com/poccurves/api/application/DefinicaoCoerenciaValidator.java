package com.poccurves.api.application;

import com.poccurves.api.domain.DefinicaoCurva;
import com.poccurves.api.domain.ModoOrigem;
import com.poccurves.api.dto.ApiDtos.LimiteValidacaoDTO;

import java.util.*;

public class DefinicaoCoerenciaValidator {

    private static final Set<String> INTERPOLADORES_VALIDOS = Set.of(
            "LINEAR", "FLAT_FORWARD", "LOG_LINEAR", "LOG_CUBIC",
            "NATURAL_CUBIC_SPLINE", "MONOTONIC_CONVEX", "FLAT_FORWARD_LINEAR"
    );

    private static final Set<String> EXTRAPOLACOES_VALIDAS = Set.of(
            "STRICT", "FLAT_RATE", "FLAT_FORWARD", "FLAT_FORWARD_LINEAR"
    );

    private static final Set<String> ARREDONDAMENTOS_VALIDOS = Set.of(
            "TRUNCATE_8", "TRUNCATE_12", "HALF_UP_8", "HALF_UP_12"
    );

    private final DefinicaoCurvaRepositoryPort definicaoCurvaRepository;
    private final VersaoDefinicaoCurvaRepositoryPort versaoDefinicaoCurvaRepository;
    private final ModeloCurvaRepositoryPort modeloCurvaRepository;
    private final JsonPort jsonPort;

    public DefinicaoCoerenciaValidator(
            DefinicaoCurvaRepositoryPort definicaoCurvaRepository,
            VersaoDefinicaoCurvaRepositoryPort versaoDefinicaoCurvaRepository,
            ModeloCurvaRepositoryPort modeloCurvaRepository,
            JsonPort jsonPort
    ) {
        this.definicaoCurvaRepository = definicaoCurvaRepository;
        this.versaoDefinicaoCurvaRepository = versaoDefinicaoCurvaRepository;
        this.modeloCurvaRepository = modeloCurvaRepository;
        this.jsonPort = jsonPort;
    }

    public void validarCoerencia(
            String codigo,
            ModoOrigem modoOrigem,
            String interpolador,
            String politicaExtrapolacao,
            String politicaArredondamento,
            UUID modeloApontadoId,
            String modeloApontadoCodigo,
            List<String> vinculosFonte,
            List<String> dependeDe,
            List<LimiteValidacaoDTO> limitesValidacao
    ) {
        // 1. Validação de Interpolador e Políticas
        if (interpolador == null || !INTERPOLADORES_VALIDOS.contains(interpolador.toUpperCase())) {
            throw new IllegalArgumentException(
                    "Interpolador inválido: '" + interpolador + "'. Valores suportados: " + INTERPOLADORES_VALIDOS);
        }
        if (politicaExtrapolacao == null || !EXTRAPOLACOES_VALIDAS.contains(politicaExtrapolacao.toUpperCase())) {
            throw new IllegalArgumentException(
                    "Política de extrapolação inválida: '" + politicaExtrapolacao + "'. Valores suportados: " + EXTRAPOLACOES_VALIDAS);
        }
        if (politicaArredondamento == null || !ARREDONDAMENTOS_VALIDOS.contains(politicaArredondamento.toUpperCase())) {
            throw new IllegalArgumentException(
                    "Política de arredondamento inválida: '" + politicaArredondamento + "'. Valores suportados: " + ARREDONDAMENTOS_VALIDOS);
        }

        // 2. Coerência Modo de Origem x Vínculos de Fonte
        if (modoOrigem == ModoOrigem.BOOTSTRAPPED) {
            if (vinculosFonte == null || vinculosFonte.isEmpty()) {
                throw new IllegalArgumentException(
                        "Curva construída (BOOTSTRAPPED) exige pelo menos um vínculo de conjunto de dado individual (ex: BVBG_086, BVBG_028, PR_DI1).");
            }
            if (vinculosFonte.contains("TAXAS_REFERENCIA")) {
                throw new IllegalArgumentException(
                        "Curva construída (BOOTSTRAPPED) não pode ser vinculada ao conjunto de curva pronta (TAXAS_REFERENCIA).");
            }
        } else if (modoOrigem == ModoOrigem.IMPORTED) {
            if (vinculosFonte == null || vinculosFonte.isEmpty() || !vinculosFonte.contains("TAXAS_REFERENCIA")) {
                throw new IllegalArgumentException(
                        "Curva importada (IMPORTED) exige vínculo com conjunto de curva pronta (TAXAS_REFERENCIA).");
            }
            if (vinculosFonte.size() > 1) {
                throw new IllegalArgumentException(
                        "Curva importada (IMPORTED) exige exatamente um vínculo de conjunto de curva pronta.");
            }
            if (modeloApontadoId != null || (modeloApontadoCodigo != null && !modeloApontadoCodigo.isBlank())) {
                throw new IllegalArgumentException(
                        "Curva importada (IMPORTED) não pode referenciar modelo de cálculo, pois não há construção.");
            }
        }

        // 3. Validação do Modelo Apontado (para BOOTSTRAPPED)
        if (modoOrigem == ModoOrigem.BOOTSTRAPPED) {
            if (modeloApontadoId != null) {
                var modelo = modeloCurvaRepository.buscarPorId(modeloApontadoId)
                        .orElseThrow(() -> new IllegalArgumentException("Modelo apontado não encontrado com ID: " + modeloApontadoId));
                if (!"ATIVO".equalsIgnoreCase(modelo.estado())) {
                    throw new IllegalArgumentException("Modelo apontado '" + modelo.codigo() + "' está desabilitado (" + modelo.estado() + ").");
                }
            } else if (modeloApontadoCodigo != null && !modeloApontadoCodigo.isBlank()) {
                var modelo = modeloCurvaRepository.buscarPorCodigo(modeloApontadoCodigo)
                        .orElseThrow(() -> new IllegalArgumentException("Modelo apontado não encontrado com código: " + modeloApontadoCodigo));
                if (!"ATIVO".equalsIgnoreCase(modelo.estado())) {
                    throw new IllegalArgumentException("Modelo apontado '" + modelo.codigo() + "' está desabilitado (" + modelo.estado() + ").");
                }
            }
        }

        // 4. Validação de Limites dos Testes de Validação
        if (limitesValidacao != null) {
            for (var l : limitesValidacao) {
                if (l.teste() == null || l.teste().isBlank()) {
                    throw new IllegalArgumentException("Identificador de teste de validação não pode ser vazio.");
                }
                if (l.limite() == null || l.limite().isBlank()) {
                    throw new IllegalArgumentException("O teste '" + l.teste() + "' está habilitado mas seu limite numérico não foi informado.");
                }
                if (!"BLOQUEANTE".equalsIgnoreCase(l.classificacao()) && !"AVISO".equalsIgnoreCase(l.classificacao())) {
                    throw new IllegalArgumentException("Classificação inválida para o teste '" + l.teste() + "': " + l.classificacao());
                }
            }
        }

        // 5. Validação de Dependências e Detecção de Ciclos
        if (dependeDe != null && !dependeDe.isEmpty()) {
            for (String depCodigo : dependeDe) {
                if (depCodigo.equalsIgnoreCase(codigo)) {
                    throw new IllegalArgumentException("A curva '" + codigo + "' não pode depender de si mesma.");
                }
                if (!definicaoCurvaRepository.existeCodigo(depCodigo)) {
                    throw new IllegalArgumentException("A curva dependência declarada '" + depCodigo + "' não existe no catálogo.");
                }
            }
            validarAusenciaDeCiclos(codigo, dependeDe);
        }
    }

    private void validarAusenciaDeCiclos(String codigoAtual, List<String> dependenciasDiretas) {
        Map<String, List<String>> grafo = new HashMap<>();
        grafo.put(codigoAtual, dependenciasDiretas);

        // Carrega dependências de outras curvas para validar o grafo
        List<DefinicaoCurva> todas = definicaoCurvaRepository.listar(null, null, null, null, null, 0, 1000);
        for (DefinicaoCurva d : todas) {
            if (!d.codigo().equalsIgnoreCase(codigoAtual)) {
                var versaoVigente = versaoDefinicaoCurvaRepository.buscarMaisRecente(d.id());
                if (versaoVigente.isPresent() && versaoVigente.get().dependeDeJson() != null) {
                    List<String> deps = jsonPort.paraListaDeString(versaoVigente.get().dependeDeJson());
                    grafo.put(d.codigo(), deps != null ? deps : Collections.emptyList());
                } else {
                    grafo.put(d.codigo(), Collections.emptyList());
                }
            }
        }

        Set<String> visitados = new HashSet<>();
        Set<String> pilhaRecursao = new HashSet<>();
        List<String> caminhoCiclo = new ArrayList<>();

        if (detectarCicloDfs(codigoAtual, grafo, visitados, pilhaRecursao, caminhoCiclo)) {
            throw new IllegalArgumentException(
                    "Ciclo de dependência detectado entre curvas: " + String.join(" -> ", caminhoCiclo));
        }
    }

    private boolean detectarCicloDfs(
            String nodo,
            Map<String, List<String>> grafo,
            Set<String> visitados,
            Set<String> pilhaRecursao,
            List<String> caminho
    ) {
        caminho.add(nodo);
        if (pilhaRecursao.contains(nodo)) {
            return true;
        }
        if (visitados.contains(nodo)) {
            caminho.remove(caminho.size() - 1);
            return false;
        }

        visitados.add(nodo);
        pilhaRecursao.add(nodo);

        List<String> vizinhos = grafo.getOrDefault(nodo, Collections.emptyList());
        for (String vizinho : vizinhos) {
            if (detectarCicloDfs(vizinho, grafo, visitados, pilhaRecursao, caminho)) {
                return true;
            }
        }

        pilhaRecursao.remove(nodo);
        caminho.remove(caminho.size() - 1);
        return false;
    }
}

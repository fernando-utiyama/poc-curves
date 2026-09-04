package com.poccurves.engine.application.service;
import com.poccurves.engine.application.model.Classificacao;
import com.poccurves.engine.application.model.ContextoValidacao;
import com.poccurves.engine.application.model.LimiteValidacao;
import com.poccurves.engine.application.model.ResultadoTeste;
import com.poccurves.engine.application.model.ResultadoValidacao;
import com.poccurves.engine.application.port.ValidacaoCurvaRepositoryPort;
import com.poccurves.engine.application.validator.TesteValidacao;


import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class BateriaValidacaoService {

    private final Map<String, TesteValidacao> porIdentificador;
    private final ValidacaoCurvaRepositoryPort validacaoCurvaRepository;

    public BateriaValidacaoService(List<TesteValidacao> testes, ValidacaoCurvaRepositoryPort validacaoCurvaRepository) {
        this.validacaoCurvaRepository = validacaoCurvaRepository;
        this.porIdentificador = new HashMap<>();
        for (TesteValidacao teste : testes) {
            if (this.porIdentificador.containsKey(teste.identificador())) {
                throw new IllegalArgumentException("Identificador de teste duplicado: " + teste.identificador());
            }
            this.porIdentificador.put(teste.identificador(), teste);
        }
    }

    public record VereditoBateria(List<ResultadoTeste> resultados, boolean aprovadaSemBloqueioReprovado) {}

    /**
     * Executa a bateria de validação para os limites configurados e persiste os resultados.
     * Caso não existam testes configurados (limitesHabilitados vazio), a curva é aprovada
     * trivialmente e nada é persistido.
     */
    public VereditoBateria executar(UUID versaoCurvaId, List<LimiteValidacao> limitesHabilitados, ContextoValidacao contexto) {
        if (limitesHabilitados == null || limitesHabilitados.isEmpty()) {
            return new VereditoBateria(List.of(), true);
        }

        List<ResultadoTeste> resultados = new ArrayList<>();
        boolean aprovadaSemBloqueioReprovado = true;

        for (LimiteValidacao limiteValidacao : limitesHabilitados) {
            String idTeste = limiteValidacao.teste();
            TesteValidacao teste = porIdentificador.get(idTeste);

            if (teste == null) {
                throw new IllegalStateException("Teste de validação desconhecido configurado na curva: " + idTeste);
            }

            ResultadoTeste resultadoExecucao;
            try {
                ResultadoTeste resultadoBruto = teste.executar(contexto, limiteValidacao.limite());
                resultadoExecucao = resultadoBruto.comClassificacao(limiteValidacao.classificacao());
            } catch (Exception e) {
                resultadoExecucao = new ResultadoTeste(
                        idTeste,
                        limiteValidacao.classificacao(),
                        ResultadoValidacao.REPROVADO,
                        null,
                        limiteValidacao.limite(),
                        "Falha interna na execução do teste: " + e.getMessage()
                );
            }

            if (resultadoExecucao.classificacao() == Classificacao.BLOQUEANTE && resultadoExecucao.resultado() == ResultadoValidacao.REPROVADO) {
                aprovadaSemBloqueioReprovado = false;
            }

            resultados.add(resultadoExecucao);
        }

        validacaoCurvaRepository.inserirTodos(versaoCurvaId, resultados);

        return new VereditoBateria(resultados, aprovadaSemBloqueioReprovado);
    }
}

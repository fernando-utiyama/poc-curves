package com.poccurves.engine.domain.validacao;
import com.poccurves.engine.domain.curva.CurvaJuros;

import java.math.BigDecimal;

/**
 * Contrato de um teste de validação de consistência sobre uma curva
 * construída. Testável sem banco nem broker: opera só sobre CurvaJuros,
 * sem depender do código de orquestração do motor.
 * <p>
 * A classificação (BLOQUEANTE/AVISO) e o limite NÃO são propriedade da classe do teste —
 * design.md (D8d) é explícito: "a classificação de cada teste é configuração por curva, não
 * constante de código". Por isso {@link #executar} recebe o limite como parâmetro (vindo de
 * {@code limites_validacao} da versão de definição), e o {@link ResultadoTeste} devolvido carrega
 * uma classificação provisória que quem chama (a bateria) sobrescreve com
 * {@link ResultadoTeste#comClassificacao(Classificacao)} usando a classificação real declarada
 * pela curva antes de persistir — nunca a da própria classe do teste.
 */
public interface TesteValidacao {

    /** Identificador do teste (ex.: "MONOTONICIDADE_FATOR_DESCONTO"), único no catálogo de testes. */
    String identificador();

    /**
     * Executa o teste sobre o contexto (curva construída mais dados de comparação, quando o teste
     * precisar) com o limite declarado pela curva (de {@code limites_validacao}) e produz o
     * resultado, incluindo o caso NAO_APLICAVEL (ex.: sem curva do dia anterior para comparar).
     *
     * @param contexto a curva construída e os dados de comparação disponíveis
     * @param limite   o limite numérico declarado para este teste nesta curva — nunca nulo quando o
     *                 teste está habilitado (tarefa 8.12 garante isso antes de chamar)
     */
    ResultadoTeste executar(ContextoValidacao contexto, BigDecimal limite);
}

package com.poccurves.engine.domain.validacao;

import java.math.BigDecimal;
import java.util.Objects;

/**
 * Resultado de um teste de validação executado sobre uma versão de curva,
 * espelhando os campos relevantes de validacao_curva (db/migration/V4__versao_curva.sql)
 * que o próprio teste produz — id, versao_curva_id e executado_em são
 * atribuídos na persistência, não aqui.
 */
public record ResultadoTeste(
        String identificador,
        Classificacao classificacao,
        ResultadoValidacao resultado,
        BigDecimal medidaObservada,
        BigDecimal limiteAplicado,
        String detalhe
) {
    public ResultadoTeste {
        if (identificador == null || identificador.isBlank()) {
            throw new IllegalArgumentException("identificador não pode ser nulo ou vazio");
        }
        Objects.requireNonNull(classificacao, "classificacao não pode ser nula");
        Objects.requireNonNull(resultado, "resultado não pode ser nulo");
    }

    /**
     * Devolve uma cópia com a classificação substituída — usado pela bateria de validação para
     * sobrescrever a classificação provisória de {@link TesteValidacao#executar} com a classificação
     * real declarada pela curva em {@code limites_validacao} (design.md D8d: classificação é
     * configuração por curva, nunca constante de código).
     */
    public ResultadoTeste comClassificacao(Classificacao novaClassificacao) {
        return new ResultadoTeste(identificador, novaClassificacao, resultado, medidaObservada, limiteAplicado, detalhe);
    }
}

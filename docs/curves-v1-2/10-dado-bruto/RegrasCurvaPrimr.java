package br.com.poc.application.service;

import br.com.poc.application.exception.BusinessException;
import br.com.poc.application.exception.CadastroErrorCode;
import br.com.poc.application.exception.InvalidInputException;
import br.com.poc.domain.aviso.Detalhe;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.List;

/** Regras iguais nas três fontes de dado bruto (B3, ANBIMA e Bloomberg). */
final class RegrasCurvaPrimr {

    static final ZoneId BRASILIA = ZoneId.of("America/Sao_Paulo");

    private RegrasCurvaPrimr() {
    }

    /** Período da listagem: sem 'de', 30 dias antes do 'ate'; sem 'ate', hoje. No máximo 366 dias. */
    record Periodo(LocalDate de, LocalDate ate) {

        static Periodo de(LocalDate de, LocalDate ate) {
            LocalDate fim = ate != null ? ate : LocalDate.now(BRASILIA);
            LocalDate inicio = de != null ? de : fim.minusDays(30);
            if (inicio.isAfter(fim)) {
                throw new InvalidInputException(CadastroErrorCode.PARAMETRO_INVALIDO.getCode(),
                    "O início do período (" + inicio + ") é depois do fim (" + fim + ")");
            }
            if (ChronoUnit.DAYS.between(inicio, fim) > 366) {
                throw new InvalidInputException(CadastroErrorCode.PARAMETRO_INVALIDO.getCode(),
                    "O período de " + inicio + " a " + fim + " passa de 366 dias");
            }
            return new Periodo(inicio, fim);
        }
    }

    static String textoOuNulo(String texto) {
        return texto != null && !texto.isBlank() ? texto.trim() : null;
    }

    static void obrigatorio(String campo, Object valor, List<Detalhe> erros) {
        if (valor == null || (valor instanceof String s && s.isBlank())) {
            erros.add(new Detalhe(campo, null, null, campo + " é obrigatório"));
        }
    }

    /** Confere os limites da coluna decimal; o valor é gravado como veio, sem arredondar (é o dado bruto). */
    static void decimal(String campo, BigDecimal valor, int maxInteiros, int maxEscala, List<Detalhe> erros) {
        if (valor == null) {
            return;
        }
        if (valor.scale() > maxEscala) {
            erros.add(new Detalhe(campo, null, valor.toPlainString(), campo + " deve ter no máximo " + maxEscala + " casas decimais"));
        }
        if (valor.precision() - valor.scale() > maxInteiros) {
            erros.add(new Detalhe(campo, null, valor.toPlainString(), campo + " deve ter no máximo " + maxInteiros + " dígitos inteiros"));
        }
    }

    static void recusarSeHouverErros(List<Detalhe> erros) {
        if (!erros.isEmpty()) {
            throw new BusinessException(CadastroErrorCode.DADOS_INVALIDOS, erros.toArray());
        }
    }
}

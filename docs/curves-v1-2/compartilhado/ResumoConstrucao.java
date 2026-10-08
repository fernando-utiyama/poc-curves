package br.com.poc.domain.cadastro;

import java.time.LocalDate;

/** Resumo das datas já construídas de uma curva numa janela de datas. Sem construção: 0, nulo, nulo. */
public record ResumoConstrucao(int datas, LocalDate primeira, LocalDate ultima) {
}

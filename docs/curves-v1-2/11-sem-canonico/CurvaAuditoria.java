package br.com.poc.domain.cadastro;

import java.util.List;

/** Dados completos da curva para auditoria (JSON e Excel): a curva, os provedores e todas as versões de configuração. */
public record CurvaAuditoria(
    CurvaMercado curva,
    List<CurvaProvedor> provedores,
    List<ConfiguracaoCurva> configuracoes
) {}

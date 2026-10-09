package br.com.poc.domain.cadastro;

import br.com.poc.domain.aviso.AvisoCurva;

import java.util.List;

/** Consulta detalhada da curva com provedores (tCurvaPrvdr) e configuração vigente. */
public record CurvaMercadoDetalhada(
    CurvaMercado curva,
    List<CurvaProvedor> provedores,
    ConfiguracaoCurva configuracaoVigente,
    List<AvisoCurva> avisos
) {}

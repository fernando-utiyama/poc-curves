package br.com.poc.domain.cadastro;

import java.time.LocalDate;

public record CriarConfiguracaoCurvaInput(
    String modeloConstrucao,
    String interpolador,
    ParametrosCalculo parametros,
    LocalDate inicioVigencia
) {}

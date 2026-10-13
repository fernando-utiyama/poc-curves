package br.com.poc.adapter.in.api.rest.dto;

import br.com.poc.domain.cadastro.CurvaMercado;
import com.fasterxml.jackson.annotation.JsonFormat;

import java.time.LocalDate;
import java.util.List;

public record CurvaMercadoResponse(
    String codigo,
    String nome,
    String unidade,
    String dayCounterCotacao,
    String compounding,
    String moeda,
    String pais,
    String classificacao,
    String classeAtivo,
    String situacao,
    LocalDate inicioVigencia,
    LocalDate fimVigencia,
    List<String> provedores,
    String dono,
    UltimaExecucao ultimaExecucao
) {
    public record UltimaExecucao(
        @JsonFormat(pattern = "yyyy-MM-dd") LocalDate dataBase,
        String usuario
    ) {}

    public CurvaMercadoResponse(
        String codigo,
        String nome,
        String unidade,
        String dayCounterCotacao,
        String compounding,
        String moeda,
        String pais,
        String classificacao,
        String classeAtivo,
        String situacao,
        LocalDate inicioVigencia,
        LocalDate fimVigencia
    ) {
        this(codigo, nome, unidade, dayCounterCotacao, compounding, moeda, pais, classificacao, classeAtivo, situacao,
            inicioVigencia, fimVigencia, List.of(), null, null);
    }

    public static CurvaMercadoResponse fromDomain(CurvaMercado c) {
        return fromDomain(c, List.of());
    }

    public static CurvaMercadoResponse fromDomain(CurvaMercado c, List<String> provedores) {
        UltimaExecucao exec = c.dataBaseReft() != null
            ? new UltimaExecucao(c.dataBaseReft(), c.usuarioCalculo())
            : null;

        return new CurvaMercadoResponse(
            c.codigo(),
            c.nome(),
            c.unidade() != null ? c.unidade().name() : null,
            c.dayCounterCotacao() != null ? c.dayCounterCotacao().name() : null,
            c.compounding() != null ? c.compounding().name() : null,
            c.moeda(),
            c.pais(),
            c.classificacao(),
            c.classeAtivo(),
            c.situacao() != null ? c.situacao().name() : null,
            c.inicioVigencia(),
            c.fimVigencia(),
            provedores != null ? provedores : List.of(),
            c.dono(),
            exec
        );
    }
}

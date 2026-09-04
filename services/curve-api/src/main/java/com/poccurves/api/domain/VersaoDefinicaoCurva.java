package com.poccurves.api.domain;

import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;

/**
 * Entidade de domínio que representa uma versão de configuração de uma
 * definição de curva, espelhando db/migration/V1__definicao_curva.sql
 * (tabela versao_definicao_curva). Cada edição gera uma versão nova com
 * número incremental; a versão anterior tem sua vigência encerrada na data
 * de início da nova — a anterior nunca é sobrescrita.
 */
public class VersaoDefinicaoCurva {

    private final UUID id;
    private final UUID definicaoCurvaId;
    private final int numeroVersao;
    private final String contagemDias;
    private final String calendario;
    private final String interpolador;
    private final String politicaExtrapolacao;
    private final String politicaArredondamento;
    private final UUID modeloCurvaId;
    private final Integer orcamentoIngestaoSegundos;
    private final Integer orcamentoConstrucaoSegundos;
    private final Integer orcamentoValidacaoSegundos;
    private final Integer orcamentoPublicacaoSegundos;
    private final Integer janelaBloqueioMinutos;
    private final String vinculosFonteJson;
    private final String dependeDeJson;
    private final String limitesValidacaoJson;
    private final LocalDate vigenciaInicio;
    private LocalDate vigenciaFim;

    public VersaoDefinicaoCurva(
            UUID id,
            UUID definicaoCurvaId,
            int numeroVersao,
            String contagemDias,
            String calendario,
            String interpolador,
            String politicaExtrapolacao,
            String politicaArredondamento,
            UUID modeloCurvaId,
            Integer orcamentoIngestaoSegundos,
            Integer orcamentoConstrucaoSegundos,
            Integer orcamentoValidacaoSegundos,
            Integer orcamentoPublicacaoSegundos,
            Integer janelaBloqueioMinutos,
            String vinculosFonteJson,
            String dependeDeJson,
            String limitesValidacaoJson,
            LocalDate vigenciaInicio,
            LocalDate vigenciaFim
    ) {
        this.id = id != null ? id : UUID.randomUUID();
        this.definicaoCurvaId = definicaoCurvaId;
        this.numeroVersao = numeroVersao;
        this.contagemDias = contagemDias;
        this.calendario = calendario;
        this.interpolador = interpolador;
        this.politicaExtrapolacao = politicaExtrapolacao;
        this.politicaArredondamento = politicaArredondamento;
        this.modeloCurvaId = modeloCurvaId;
        this.orcamentoIngestaoSegundos = orcamentoIngestaoSegundos;
        this.orcamentoConstrucaoSegundos = orcamentoConstrucaoSegundos;
        this.orcamentoValidacaoSegundos = orcamentoValidacaoSegundos;
        this.orcamentoPublicacaoSegundos = orcamentoPublicacaoSegundos;
        this.janelaBloqueioMinutos = janelaBloqueioMinutos;
        this.vinculosFonteJson = vinculosFonteJson;
        this.dependeDeJson = dependeDeJson;
        this.limitesValidacaoJson = limitesValidacaoJson;
        this.vigenciaInicio = vigenciaInicio;
        this.vigenciaFim = vigenciaFim;
    }

    private static void validarCamposObrigatorios(
            UUID definicaoCurvaId,
            String contagemDias,
            String calendario,
            String interpolador,
            String politicaExtrapolacao,
            String politicaArredondamento,
            LocalDate vigenciaInicio
    ) {
        Objects.requireNonNull(definicaoCurvaId, "definicaoCurvaId não pode ser nulo");
        if (contagemDias == null || contagemDias.isBlank()) {
            throw new IllegalArgumentException("contagemDias não pode ser nulo ou vazio");
        }
        if (calendario == null || calendario.isBlank()) {
            throw new IllegalArgumentException("calendario não pode ser nulo ou vazio");
        }
        if (interpolador == null || interpolador.isBlank()) {
            throw new IllegalArgumentException("interpolador não pode ser nulo ou vazio");
        }
        if (politicaExtrapolacao == null || politicaExtrapolacao.isBlank()) {
            throw new IllegalArgumentException("politicaExtrapolacao não pode ser nula ou vazia");
        }
        if (politicaArredondamento == null || politicaArredondamento.isBlank()) {
            throw new IllegalArgumentException("politicaArredondamento não pode ser nula ou vazia");
        }
        Objects.requireNonNull(vigenciaInicio, "vigenciaInicio não pode ser nula");
    }

    /**
     * Cria a primeira versão (numeroVersao = 1) de uma definição, junto com a definição.
     *
     * @throws IllegalArgumentException se contagemDias, calendario, interpolador,
     *                                   politicaExtrapolacao ou politicaArredondamento forem nulos/em branco
     * @throws NullPointerException     se definicaoCurvaId ou vigenciaInicio forem nulos
     */
    public static VersaoDefinicaoCurva primeiraVersao(
            UUID definicaoCurvaId,
            String contagemDias,
            String calendario,
            String interpolador,
            String politicaExtrapolacao,
            String politicaArredondamento,
            UUID modeloCurvaId,
            Integer orcamentoIngestaoSegundos,
            Integer orcamentoConstrucaoSegundos,
            Integer orcamentoValidacaoSegundos,
            Integer orcamentoPublicacaoSegundos,
            Integer janelaBloqueioMinutos,
            String vinculosFonteJson,
            String dependeDeJson,
            String limitesValidacaoJson,
            LocalDate vigenciaInicio
    ) {
        validarCamposObrigatorios(definicaoCurvaId, contagemDias, calendario, interpolador,
                politicaExtrapolacao, politicaArredondamento, vigenciaInicio);

        return new VersaoDefinicaoCurva(
                UUID.randomUUID(), definicaoCurvaId, 1, contagemDias, calendario, interpolador,
                politicaExtrapolacao, politicaArredondamento, modeloCurvaId, orcamentoIngestaoSegundos,
                orcamentoConstrucaoSegundos, orcamentoValidacaoSegundos, orcamentoPublicacaoSegundos,
                janelaBloqueioMinutos, vinculosFonteJson, dependeDeJson, limitesValidacaoJson, vigenciaInicio, null
        );
    }

    public static VersaoDefinicaoCurva proximaVersao(
            VersaoDefinicaoCurva anterior,
            String contagemDias,
            String calendario,
            String interpolador,
            String politicaExtrapolacao,
            String politicaArredondamento,
            UUID modeloCurvaId,
            Integer orcamentoIngestaoSegundos,
            Integer orcamentoConstrucaoSegundos,
            Integer orcamentoValidacaoSegundos,
            Integer orcamentoPublicacaoSegundos,
            Integer janelaBloqueioMinutos,
            String vinculosFonteJson,
            String dependeDeJson,
            String limitesValidacaoJson,
            LocalDate vigenciaInicio
    ) {
        Objects.requireNonNull(anterior, "anterior não pode ser nulo");
        validarCamposObrigatorios(anterior.definicaoCurvaId, contagemDias, calendario, interpolador,
                politicaExtrapolacao, politicaArredondamento, vigenciaInicio);

        anterior.encerrarVigencia(vigenciaInicio);

        return new VersaoDefinicaoCurva(
                UUID.randomUUID(), anterior.definicaoCurvaId, anterior.numeroVersao + 1, contagemDias, calendario, interpolador,
                politicaExtrapolacao, politicaArredondamento, modeloCurvaId, orcamentoIngestaoSegundos,
                orcamentoConstrucaoSegundos, orcamentoValidacaoSegundos, orcamentoPublicacaoSegundos,
                janelaBloqueioMinutos, vinculosFonteJson, dependeDeJson, limitesValidacaoJson, vigenciaInicio, null
        );
    }

    public void encerrarVigencia(LocalDate vigenciaFim) {
        Objects.requireNonNull(vigenciaFim, "vigenciaFim não pode ser nula");
        if (this.vigenciaFim != null) {
            throw new IllegalStateException("vigência já encerrada em " + this.vigenciaFim);
        }
        if (vigenciaFim.isBefore(this.vigenciaInicio)) {
            throw new IllegalArgumentException(
                    "vigenciaFim (" + vigenciaFim + ") não pode ser anterior a vigenciaInicio (" + this.vigenciaInicio + ")");
        }
        this.vigenciaFim = vigenciaFim;
    }

    public UUID id() {
        return id;
    }

    public UUID definicaoCurvaId() {
        return definicaoCurvaId;
    }

    public int numeroVersao() {
        return numeroVersao;
    }

    public String contagemDias() {
        return contagemDias;
    }

    public String calendario() {
        return calendario;
    }

    public String interpolador() {
        return interpolador;
    }

    public String politicaExtrapolacao() {
        return politicaExtrapolacao;
    }

    public String politicaArredondamento() {
        return politicaArredondamento;
    }

    public UUID modeloCurvaId() {
        return modeloCurvaId;
    }

    public Integer orcamentoIngestaoSegundos() {
        return orcamentoIngestaoSegundos;
    }

    public Integer orcamentoConstrucaoSegundos() {
        return orcamentoConstrucaoSegundos;
    }

    public Integer orcamentoValidacaoSegundos() {
        return orcamentoValidacaoSegundos;
    }

    public Integer orcamentoPublicacaoSegundos() {
        return orcamentoPublicacaoSegundos;
    }

    public Integer janelaBloqueioMinutos() {
        return janelaBloqueioMinutos;
    }

    public String vinculosFonteJson() {
        return vinculosFonteJson;
    }

    public String dependeDeJson() {
        return dependeDeJson;
    }

    public String limitesValidacaoJson() {
        return limitesValidacaoJson;
    }

    public LocalDate vigenciaInicio() {
        return vigenciaInicio;
    }

    public LocalDate vigenciaFim() {
        return vigenciaFim;
    }
}


package com.poccurves.orchestrator.domain;

import org.springframework.scheduling.support.CronExpression;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.UUID;

public class Agendamento {

    private final UUID id;
    private final UUID definicaoCurvaId;
    private final String conjuntoDados;
    private final MomentoCurva momentoCurva;
    private Faixa faixa;
    private String expressaoHorario;
    private String fusoHorario;
    private int janelaTentativaMinutos;
    private int intervaloTentativaSegundos;
    private boolean ativo;
    private final String criadoPor;
    private final LocalDateTime criadoEm;
    private LocalDateTime atualizadoEm;

    private Agendamento(
            UUID id,
            UUID definicaoCurvaId,
            String conjuntoDados,
            MomentoCurva momentoCurva,
            Faixa faixa,
            String expressaoHorario,
            String fusoHorario,
            int janelaTentativaMinutos,
            int intervaloTentativaSegundos,
            boolean ativo,
            String criadoPor,
            LocalDateTime criadoEm,
            LocalDateTime atualizadoEm
    ) {
        this.id = id;
        this.definicaoCurvaId = definicaoCurvaId;
        this.conjuntoDados = conjuntoDados;
        this.momentoCurva = momentoCurva;
        this.faixa = faixa;
        this.expressaoHorario = expressaoHorario;
        this.fusoHorario = fusoHorario;
        this.janelaTentativaMinutos = janelaTentativaMinutos;
        this.intervaloTentativaSegundos = intervaloTentativaSegundos;
        this.ativo = ativo;
        this.criadoPor = criadoPor;
        this.criadoEm = criadoEm;
        this.atualizadoEm = atualizadoEm;
    }

    public static Agendamento criar(
            UUID definicaoCurvaId,
            String conjuntoDados,
            MomentoCurva momentoCurva,
            Faixa faixa,
            String expressaoHorario,
            String fusoHorario,
            int janelaTentativaMinutos,
            int intervaloTentativaSegundos,
            String criadoPor
    ) {
        validarExatamenteUmAlvo(definicaoCurvaId, conjuntoDados);
        validarExpressao(expressaoHorario);
        validarFuso(fusoHorario);
        validarTempos(janelaTentativaMinutos, intervaloTentativaSegundos);

        LocalDateTime agora = LocalDateTime.now();
        return new Agendamento(
                UUID.randomUUID(),
                definicaoCurvaId,
                conjuntoDados,
                momentoCurva,
                faixa,
                expressaoHorario,
                fusoHorario,
                janelaTentativaMinutos,
                intervaloTentativaSegundos,
                true,
                criadoPor,
                agora,
                agora
        );
    }
    
    public static Agendamento reconstituir(
            UUID id,
            UUID definicaoCurvaId,
            String conjuntoDados,
            MomentoCurva momentoCurva,
            Faixa faixa,
            String expressaoHorario,
            String fusoHorario,
            int janelaTentativaMinutos,
            int intervaloTentativaSegundos,
            boolean ativo,
            String criadoPor,
            LocalDateTime criadoEm,
            LocalDateTime atualizadoEm
    ) {
        return new Agendamento(
                id, definicaoCurvaId, conjuntoDados, momentoCurva, faixa,
                expressaoHorario, fusoHorario, janelaTentativaMinutos,
                intervaloTentativaSegundos, ativo, criadoPor, criadoEm, atualizadoEm
        );
    }

    public void editar(String expressaoHorario, String fusoHorario, int janelaTentativaMinutos, int intervaloTentativaSegundos, Faixa faixa) {
        validarExpressao(expressaoHorario);
        validarFuso(fusoHorario);
        validarTempos(janelaTentativaMinutos, intervaloTentativaSegundos);
        
        this.expressaoHorario = expressaoHorario;
        this.fusoHorario = fusoHorario;
        this.janelaTentativaMinutos = janelaTentativaMinutos;
        this.intervaloTentativaSegundos = intervaloTentativaSegundos;
        this.faixa = faixa;
        this.atualizadoEm = LocalDateTime.now();
    }

    public void ativar() {
        this.ativo = true;
        this.atualizadoEm = LocalDateTime.now();
    }

    public void desativar() {
        this.ativo = false;
        this.atualizadoEm = LocalDateTime.now();
    }

    private static void validarExatamenteUmAlvo(UUID definicaoCurvaId, String conjuntoDados) {
        boolean temDefinicao = definicaoCurvaId != null;
        boolean temConjunto = conjuntoDados != null && !conjuntoDados.isBlank();
        if (temDefinicao == temConjunto) {
            throw new IllegalArgumentException("Deve ser informado exatamente um alvo: ou definicaoCurvaId ou conjuntoDados.");
        }
    }

    private static void validarExpressao(String expressaoHorario) {
        if (expressaoHorario == null || expressaoHorario.isBlank()) {
            throw new IllegalArgumentException("Expressão cron é obrigatória.");
        }
        try {
            CronExpression.parse(expressaoHorario);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Expressão cron inválida: " + e.getMessage());
        }
    }

    private static void validarFuso(String fusoHorario) {
        if (fusoHorario == null || fusoHorario.isBlank()) {
            throw new IllegalArgumentException("Fuso horário é obrigatório.");
        }
        try {
            ZoneId.of(fusoHorario);
        } catch (Exception e) {
            throw new IllegalArgumentException("Fuso horário inválido: " + e.getMessage());
        }
    }

    private static void validarTempos(int janela, int intervalo) {
        if (janela <= 0) {
            throw new IllegalArgumentException("Janela de tentativa deve ser maior que zero.");
        }
        if (intervalo <= 0) {
            throw new IllegalArgumentException("Intervalo de tentativa deve ser maior que zero.");
        }
    }

    public UUID id() { return id; }
    public UUID definicaoCurvaId() { return definicaoCurvaId; }
    public String conjuntoDados() { return conjuntoDados; }
    public MomentoCurva momentoCurva() { return momentoCurva; }
    public Faixa faixa() { return faixa; }
    public String expressaoHorario() { return expressaoHorario; }
    public String fusoHorario() { return fusoHorario; }
    public int janelaTentativaMinutos() { return janelaTentativaMinutos; }
    public int intervaloTentativaSegundos() { return intervaloTentativaSegundos; }
    public boolean ativo() { return ativo; }
    public String criadoPor() { return criadoPor; }
    public LocalDateTime criadoEm() { return criadoEm; }
    public LocalDateTime atualizadoEm() { return atualizadoEm; }
}

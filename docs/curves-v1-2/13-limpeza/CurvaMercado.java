package br.com.poc.domain.cadastro;

import br.com.poc.domain.CompoundingCotacao;
import br.com.poc.domain.DayCounterCotacao;
import br.com.poc.domain.SituacaoCurva;
import br.com.poc.domain.Unidade;

import java.text.Normalizer;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Locale;

/**
 * Modelo de domínio para Curva de Mercado.
 */
public record CurvaMercado(
    String codigo,
    String nome,
    Unidade unidade,
    DayCounterCotacao dayCounterCotacao,
    CompoundingCotacao compounding,
    String moeda,
    String pais,
    String classificacao,
    String classeAtivo,
    String dono,
    SituacaoCurva situacao,
    LocalDate inicioVigencia,
    LocalDate fimVigencia,
    LocalDateTime dataCriacao,
    LocalDateTime dataUltimaAtualizacao,
    LocalDate dataBaseReft,
    String usuarioCalculo,
    String usuarioAtualizacao
) {

    /** Sem o dono (curvas anteriores ao campo). */
    public CurvaMercado(
        String codigo,
        String nome,
        Unidade unidade,
        DayCounterCotacao dayCounterCotacao,
        CompoundingCotacao compounding,
        String moeda,
        String pais,
        String classificacao,
        String classeAtivo,
        SituacaoCurva situacao,
        LocalDate inicioVigencia,
        LocalDate fimVigencia,
        LocalDateTime dataCriacao,
        LocalDateTime dataUltimaAtualizacao,
        LocalDate dataBaseReft,
        String usuarioCalculo,
        String usuarioAtualizacao
    ) {
        this(
            codigo,
            nome,
            unidade,
            dayCounterCotacao,
            compounding,
            moeda,
            pais,
            classificacao,
            classeAtivo,
            null,
            situacao,
            inicioVigencia,
            fimVigencia,
            dataCriacao,
            dataUltimaAtualizacao,
            dataBaseReft,
            usuarioCalculo,
            usuarioAtualizacao
        );
    }

    /** Mesma curva com outra situação (inativar e reativar); o resto não muda. */
    public CurvaMercado comSituacao(SituacaoCurva nova, LocalDateTime atualizadoEm) {
        return new CurvaMercado(
            codigo, nome, unidade, dayCounterCotacao, compounding, moeda, pais, classificacao, classeAtivo, dono,
            nova, inicioVigencia, fimVigencia, dataCriacao, atualizadoEm, dataBaseReft, usuarioCalculo, usuarioAtualizacao);
    }

    public static String normalizarNome(String nome) {
        if (nome == null) {
            return null;
        }
        return Normalizer.normalize(nome, Normalizer.Form.NFD)
            .replaceAll("\\p{M}", "")
            .toLowerCase(Locale.ROOT)
            .strip();
    }
}

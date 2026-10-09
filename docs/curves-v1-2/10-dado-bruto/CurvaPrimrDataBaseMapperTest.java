package br.com.poc.adapter.out.persistence;

import br.com.poc.adapter.out.persistence.repository.CurvaPrimrDataBaseProjection;
import br.com.poc.application.port.out.CurvaPrvdrRepositoryPort;
import br.com.poc.domain.SituacaoCurva;
import br.com.poc.domain.cadastro.CurvaPrimrDataBase;
import br.com.poc.domain.cadastro.CurvaProvedor;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class CurvaPrimrDataBaseMapperTest {

    private static CurvaPrimrDataBaseProjection linha(String codigo, String nome, String situacao, Long quantidade, Integer construida) {
        CurvaPrimrDataBaseProjection p = mock(CurvaPrimrDataBaseProjection.class);
        when(p.getCodigo()).thenReturn(codigo);
        when(p.getNome()).thenReturn(nome);
        when(p.getSituacao()).thenReturn(situacao);
        when(p.getDataRef()).thenReturn(LocalDate.of(2026, 9, 14));
        when(p.getQuantidade()).thenReturn(quantidade);
        when(p.getConstruida()).thenReturn(construida);
        return p;
    }

    @Test
    void convertePreservandoCurvaSemCodigo() {
        var linhas = List.of(linha(null, "TBD-B3-DIXPRE", "ATIVO     ", 278L, 1));

        List<CurvaPrimrDataBase> res = CurvaPrimrDataBaseMapper.paraDatasBase(linhas, Map.of("TBD-B3-DIXPRE", List.of("PRE")));

        assertThat(res).singleElement().satisfies(r -> {
            assertThat(r.codigo()).isNull();
            assertThat(r.situacao()).isEqualTo(SituacaoCurva.ATIVO);
            assertThat(r.quantidadeVertices()).isEqualTo(278L);
            assertThat(r.tickersProvedor()).containsExactly("PRE");
            assertThat(r.curvaConstruida()).isTrue();
        });
    }

    @Test
    void situacaoDesconhecidaEQuantidadeNulaViramNulaEZero() {
        var linhas = List.of(linha("X", "CURVA", "OUTRA", null, 0));

        List<CurvaPrimrDataBase> res = CurvaPrimrDataBaseMapper.paraDatasBase(linhas, Map.of());

        assertThat(res).singleElement().satisfies(r -> {
            assertThat(r.situacao()).isNull();
            assertThat(r.quantidadeVertices()).isZero();
            assertThat(r.tickersProvedor()).isEmpty();
            assertThat(r.curvaConstruida()).isFalse();
        });
    }

    @Test
    void agrupaOsTickersPorCurva() {
        CurvaPrvdrRepositoryPort port = mock(CurvaPrvdrRepositoryPort.class);
        when(port.buscarCurvasProvedor("B3", "TS", null)).thenReturn(List.of(
            new CurvaProvedor(1L, "DIxPRE", "B3", "TS", "PRE", 1),
            new CurvaProvedor(2L, "DIxPRE", "B3", "TS", "PRE2", 2)));

        assertThat(CurvaPrimrDataBaseMapper.tickersPorCurva(port, "B3", "TS"))
            .containsEntry("DIxPRE", List.of("PRE", "PRE2"));
    }
}

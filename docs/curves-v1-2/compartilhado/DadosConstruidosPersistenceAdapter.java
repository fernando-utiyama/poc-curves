package br.com.poc.adapter.out.persistence;

import br.com.poc.application.port.out.DadosConstruidosPort;
import br.com.poc.domain.cadastro.ConstrucaoApagada;
import br.com.poc.domain.cadastro.LinhasPorTabela;
import br.com.poc.domain.cadastro.ResumoConstrucao;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.List;

/** Tabelas do engine (tDadoVertcCurva e tDadoCurva): a curves só lê o resumo e só apaga por data, nunca grava. */
@Component
public class DadosConstruidosPersistenceAdapter implements DadosConstruidosPort {

    /** Tabelas com chave estrangeira para tCurvaMercd, fora tConfgCurva e tCurvaPrvdr (que a exclusão da curva apaga). */
    private static final List<String> TABELAS_DEPENDENTES = List.of(
        "tDadoVertcCurva", "tDadoCurva",
        "tBtrsCurvaPrimr", "tAnbmaCurvaPrimr", "tBbergCurvaPrimr",
        "tCmeCurvaPrimr", "tLchCurvaPrimr", "tLsegCurvaPrimr", "tMtrizCurva");

    @PersistenceContext
    private EntityManager entityManager;

    @Override
    public ResumoConstrucao resumo(String nomeCurva, LocalDate de, LocalDate ate) {
        Object[] linha = (Object[]) entityManager.createNativeQuery(
                "SELECT COUNT(DISTINCT dBaseReft), MIN(dBaseReft), MAX(dBaseReft) FROM dbo.tDadoVertcCurva "
                    + "WHERE cTickerIndcd = :nome AND dBaseReft >= :de AND dBaseReft <= :ate")
            .setParameter("nome", nomeCurva)
            .setParameter("de", de)
            .setParameter("ate", ate)
            .getSingleResult();

        return new ResumoConstrucao(((Number) linha[0]).intValue(), data(linha[1]), data(linha[2]));
    }

    /** Precisa de transação ativa (o serviço é @Transactional). */
    @Override
    public ConstrucaoApagada apagar(String nomeCurva, LocalDate dataBase) {
        int pontos = apagarDe("dbo.tDadoCurva", nomeCurva, dataBase);
        int vertices = apagarDe("dbo.tDadoVertcCurva", nomeCurva, dataBase);
        return new ConstrucaoApagada(vertices, pontos);
    }

    @Override
    public List<LinhasPorTabela> dependentes(String nomeCurva) {
        String sql = String.join(" UNION ALL ", TABELAS_DEPENDENTES.stream()
            .map(t -> "SELECT '" + t + "', COUNT(*) FROM dbo." + t + " WHERE cTickerIndcd = :nome")
            .toList());

        @SuppressWarnings("unchecked")
        List<Object[]> linhas = entityManager.createNativeQuery(sql)
            .setParameter("nome", nomeCurva)
            .getResultList();

        return linhas.stream()
            .map(l -> new LinhasPorTabela((String) l[0], ((Number) l[1]).intValue()))
            .filter(l -> l.linhas() > 0)
            .toList();
    }

    private int apagarDe(String tabela, String nomeCurva, LocalDate dataBase) {
        return entityManager.createNativeQuery(
                "DELETE FROM " + tabela + " WHERE cTickerIndcd = :nome AND dBaseReft = :data")
            .setParameter("nome", nomeCurva)
            .setParameter("data", dataBase)
            .executeUpdate();
    }

    private static LocalDate data(Object valor) {
        return switch (valor) {
            case null -> null;
            case LocalDate d -> d;
            case java.sql.Date d -> d.toLocalDate();
            default -> throw new IllegalStateException("Tipo de data inesperado: " + valor.getClass());
        };
    }
}

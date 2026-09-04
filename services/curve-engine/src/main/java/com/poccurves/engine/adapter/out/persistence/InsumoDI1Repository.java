package com.poccurves.engine.adapter.out.persistence;
import com.poccurves.engine.application.model.CurvaJuros;
import com.poccurves.engine.application.model.InsumoDI1;
import com.poccurves.engine.application.port.InsumoDI1RepositoryPort;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.sql.Date;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Repository
public class InsumoDI1Repository implements InsumoDI1RepositoryPort {

    private final JdbcTemplate jdbcTemplate;

    /** Espelha Bvbg086PricRptParser.TIPO_COTACAO_TAXA_AJUSTE (curve-processor) */
    private static final String TIPO_COTACAO_TAXA_AJUSTE = "TAXA_AJUSTE";
    /** Espelha Bvbg028CadastroParser.TIPO_COTACAO_DIAS_UTEIS_VENCIMENTO (curve-processor) */
    private static final String TIPO_COTACAO_DIAS_UTEIS_VENCIMENTO = "DIAS_UTEIS_VENCIMENTO";

    public InsumoDI1Repository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public List<InsumoDI1> buscarInsumosDI1(List<String> conjuntosDados, LocalDate dataReferencia) {
        if (conjuntosDados == null || conjuntosDados.isEmpty()) {
            return List.of();
        }

        String inSql = String.join(",", Collections.nCopies(conjuntosDados.size(), "?"));
        // Achado real (tarefa 12.1, teste de integração com dado real da B3): BVBG.086/BVBG.028
        // trazem TODOS os instrumentos de derivativos da B3 no arquivo, não só DI1 — câmbio
        // (DOLU26, WDOU26), índices de país (ARBU26, CHLU26...) etc. também aparecem, e podem por
        // coincidência compartilhar o mesmo prazo em dias úteis de algum contrato DI1 real,
        // estourando a validação de prazo duplicado de CurvaJuros.de. O filtro por prefixo "DI1"
        // é o mesmo usado pelos tickers DI1 reais já circulando nesta base (DI1F26, DI1U26...).
        String sql = String.format("""
            SELECT chave_instrumento, tipo_cotacao, valor, data_vencimento
            FROM ponto_dado_mercado
            WHERE fonte = 'B3'
              AND conjunto_dados IN (%s)
              AND data_referencia = ?
              AND chave_instrumento LIKE 'DI1%%'
        """, inSql);

        List<Object> parametros = new ArrayList<>(conjuntosDados);
        parametros.add(Date.valueOf(dataReferencia));

        List<Map<String, Object>> linhas = jdbcTemplate.queryForList(sql, parametros.toArray());

        Map<String, List<Map<String, Object>>> porTicker = linhas.stream()
                .collect(Collectors.groupingBy(linha -> (String) linha.get("chave_instrumento")));

        List<InsumoDI1> insumos = new ArrayList<>();
        for (Map.Entry<String, List<Map<String, Object>>> entry : porTicker.entrySet()) {
            String ticker = entry.getKey();
            List<Map<String, Object>> linhasDoTicker = entry.getValue();

            BigDecimal taxaAjuste = null;
            Integer diasUteisVencimento = null;
            LocalDate dataVencimento = null;

            for (Map<String, Object> linha : linhasDoTicker) {
                String tipoCotacao = (String) linha.get("tipo_cotacao");
                BigDecimal valor = (BigDecimal) linha.get("valor");

                if (TIPO_COTACAO_TAXA_AJUSTE.equals(tipoCotacao)) {
                    taxaAjuste = valor;
                } else if (TIPO_COTACAO_DIAS_UTEIS_VENCIMENTO.equals(tipoCotacao)) {
                    if (valor != null) {
                        diasUteisVencimento = valor.intValue();
                    }
                    Date dtVenc = (Date) linha.get("data_vencimento");
                    if (dtVenc != null) {
                        dataVencimento = dtVenc.toLocalDate();
                    }
                }
            }

            insumos.add(new InsumoDI1(ticker, taxaAjuste, diasUteisVencimento, dataVencimento));
        }

        return insumos;
    }
}

package br.com.poc.adapter.in.api.rest.excel;

import br.com.poc.domain.cadastro.ConfiguracaoCurva;
import br.com.poc.domain.cadastro.CurvaAuditoria;
import br.com.poc.domain.cadastro.CurvaMercado;
import br.com.poc.domain.cadastro.CurvaProvedor;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import tools.jackson.databind.json.JsonMapper;

import java.io.ByteArrayOutputStream;
import java.io.IOException;

/**
 * Gerador de arquivo XLSX de auditoria cadastral com as abas Curva, Provedores e Configuracoes.
 */
public final class CurvaAuditoriaExcelGenerator {

    private static final JsonMapper JSON = JsonMapper.builder().build();

    private CurvaAuditoriaExcelGenerator() {}

    public static byte[] gerar(CurvaAuditoria auditoria) {
        try (Workbook workbook = new XSSFWorkbook();
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {

            // Aba Curva
            Sheet sheetCurva = workbook.createSheet("Curva");
            String[] headersCurva = {
                "codigo", "nome", "unidade", "dayCounterCotacao", "compounding",
                "moeda", "pais", "classificacao", "classeAtivo", "situacao",
                "inicioVigencia", "fimVigencia", "dataCriacao", "dataUltimaAtualizacao",
                "dataBaseReft", "usuarioCalculo", "usuarioAtualizacao"
            };
            Row headerRowCurva = sheetCurva.createRow(0);
            for (int i = 0; i < headersCurva.length; i++) {
                headerRowCurva.createCell(i).setCellValue(headersCurva[i]);
            }

            CurvaMercado c = auditoria.curva();
            Row rowCurva = sheetCurva.createRow(1);
            setCell(rowCurva, 0, c.codigo());
            setCell(rowCurva, 1, c.nome());
            setCell(rowCurva, 2, c.unidade() != null ? c.unidade().name() : null);
            setCell(rowCurva, 3, c.dayCounterCotacao() != null ? c.dayCounterCotacao().name() : null);
            setCell(rowCurva, 4, c.compounding() != null ? c.compounding().name() : null);
            setCell(rowCurva, 5, c.moeda());
            setCell(rowCurva, 6, c.pais());
            setCell(rowCurva, 7, c.classificacao());
            setCell(rowCurva, 8, c.classeAtivo());
            setCell(rowCurva, 9, c.situacao() != null ? c.situacao().name() : null);
            setCell(rowCurva, 10, c.inicioVigencia() != null ? c.inicioVigencia().toString() : null);
            setCell(rowCurva, 11, c.fimVigencia() != null ? c.fimVigencia().toString() : null);
            setCell(rowCurva, 12, c.dataCriacao() != null ? c.dataCriacao().toString() : null);
            setCell(rowCurva, 13, c.dataUltimaAtualizacao() != null ? c.dataUltimaAtualizacao().toString() : null);
            setCell(rowCurva, 14, c.dataBaseReft() != null ? c.dataBaseReft().toString() : null);
            setCell(rowCurva, 15, c.usuarioCalculo());
            setCell(rowCurva, 16, c.usuarioAtualizacao());

            // Aba Provedores (tCurvaPrvdr)
            Sheet sheetProvedores = workbook.createSheet("Provedores");
            String[] headersProvedores = {"idCurvaProvedor", "provedor", "produto", "tickerProvedor", "prioridade"};
            Row headerRowProvedores = sheetProvedores.createRow(0);
            for (int i = 0; i < headersProvedores.length; i++) {
                headerRowProvedores.createCell(i).setCellValue(headersProvedores[i]);
            }
            int provRowIdx = 1;
            for (CurvaProvedor prov : auditoria.provedores()) {
                Row row = sheetProvedores.createRow(provRowIdx++);
                if (prov.idCurvaProvedor() != null) row.createCell(0).setCellValue(prov.idCurvaProvedor());
                setCell(row, 1, prov.provedor());
                setCell(row, 2, prov.produto());
                setCell(row, 3, prov.tickerProvedor());
                if (prov.prioridade() != null) row.createCell(4).setCellValue(prov.prioridade());
            }

            // Aba Configuracoes
            Sheet sheetConfigs = workbook.createSheet("Configuracoes");
            String[] headersConfigs = {"versao", "inicioVigencia", "fimVigencia", "modeloConstrucao", "interpolador", "parametros"};
            Row headerRowConfigs = sheetConfigs.createRow(0);
            for (int i = 0; i < headersConfigs.length; i++) {
                headerRowConfigs.createCell(i).setCellValue(headersConfigs[i]);
            }
            int cfgRowIdx = 1;
            for (ConfiguracaoCurva cfg : auditoria.configuracoes()) {
                Row row = sheetConfigs.createRow(cfgRowIdx++);
                if (cfg.versao() != null) row.createCell(0).setCellValue(cfg.versao());
                setCell(row, 1, cfg.inicioVigencia() != null ? cfg.inicioVigencia().toString() : null);
                setCell(row, 2, cfg.fimVigencia() != null ? cfg.fimVigencia().toString() : null);
                setCell(row, 3, cfg.modeloConstrucao());
                setCell(row, 4, cfg.interpolador());
                setCell(row, 5, parametrosEmJson(cfg));
            }

            workbook.write(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new IllegalStateException("Erro ao gerar arquivo XLSX de auditoria", e);
        }
    }

    /** Os parâmetros como estão em cModDado (JSON), para a planilha mostrar o mesmo texto do banco. */
    private static String parametrosEmJson(ConfiguracaoCurva cfg) {
        return cfg.parametros() != null ? JSON.writeValueAsString(cfg.parametros()) : null;
    }

    private static void setCell(Row row, int col, String value) {
        if (value != null) {
            row.createCell(col).setCellValue(sanitizeFormula(value));
        }
    }

    private static String sanitizeFormula(String value) {
        if (value == null || value.isEmpty()) {
            return value;
        }
        char first = value.charAt(0);
        if (first == '=' || first == '+' || first == '-' || first == '@' || first == '\t' || first == '\r') {
            return "'" + value;
        }
        return value;
    }
}

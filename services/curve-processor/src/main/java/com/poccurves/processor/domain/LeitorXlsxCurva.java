package com.poccurves.processor.domain;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Leitor de arquivos de carga manual de curva no formato planilha XLSX
 * (docs/leiaute-carga-manual-curva.md).
 * <p>
 * Produz o mesmo {@link ResultadoLeituraCarga} que a carga CSV equivalente,
 * coletando todos os erros encontrados no arquivo antes de rejeitar
 * (nunca aplicação parcial — tarefa 6.4 do backlog).
 */
public final class LeitorXlsxCurva {

    private static final List<String> CABECALHO_ESPERADO = List.of(
            "prazo_dias_uteis",
            "prazo_dias_corridos",
            "data_vencimento",
            "taxa",
            "fator_desconto"
    );

    private LeitorXlsxCurva() {
    }

    public static ResultadoLeituraCarga ler(byte[] conteudo, char separadorDecimal) {
        if (conteudo == null) {
            return new ResultadoLeituraCarga.Falha(List.of(
                    new ErroLinhaCarga(1, "arquivo", "não foi possível abrir a planilha: conteúdo nulo")
            ));
        }

        try (Workbook workbook = new XSSFWorkbook(new ByteArrayInputStream(conteudo))) {
            if (workbook.getNumberOfSheets() == 0) {
                return new ResultadoLeituraCarga.Falha(List.of(
                        new ErroLinhaCarga(1, "arquivo", "planilha sem nenhuma aba")
                ));
            }

            Sheet sheet = workbook.getSheetAt(0);

            Row cabecalhoRow = sheet.getRow(0);
            if (cabecalhoRow == null || !isCabecalhoValido(cabecalhoRow)) {
                return new ResultadoLeituraCarga.Falha(List.of(
                        new ErroLinhaCarga(1, "cabecalho",
                                "cabeçalho divergente do esperado: prazo_dias_uteis;prazo_dias_corridos;data_vencimento;taxa;fator_desconto")
                ));
            }

            if (sheet.getLastRowNum() == 0) {
                return new ResultadoLeituraCarga.Falha(List.of(
                        new ErroLinhaCarga(2, "arquivo", "arquivo vazio: nenhuma linha de dado encontrada")
                ));
            }

            List<ErroLinhaCarga> todosErros = new ArrayList<>();
            List<VerticeComLinha> verticesValidos = new ArrayList<>();

            for (int i = 1; i <= sheet.getLastRowNum(); i++) {
                Row row = sheet.getRow(i);
                if (row == null) {
                    continue;
                }

                int numeroLinha = i + 1;
                List<ErroLinhaCarga> errosLinha = new ArrayList<>();

                // Coluna 0: prazo_dias_uteis (obrigatório)
                Cell c0 = row.getCell(0, Row.MissingCellPolicy.RETURN_BLANK_AS_NULL);
                Integer prazoUteis = null;
                if (isVazio(c0)) {
                    errosLinha.add(new ErroLinhaCarga(numeroLinha, "prazo_dias_uteis", "campo obrigatório vazio"));
                } else if (c0.getCellType() == CellType.NUMERIC) {
                    int valor = (int) c0.getNumericCellValue();
                    if (valor < 0) {
                        errosLinha.add(new ErroLinhaCarga(numeroLinha, "prazo_dias_uteis", "não pode ser negativo: " + valor));
                    } else {
                        prazoUteis = valor;
                    }
                } else {
                    errosLinha.add(new ErroLinhaCarga(numeroLinha, "prazo_dias_uteis", "célula não é numérica"));
                }

                // Coluna 1: prazo_dias_corridos (opcional)
                Cell c1 = row.getCell(1, Row.MissingCellPolicy.RETURN_BLANK_AS_NULL);
                Integer prazoCorridos = null;
                if (!isVazio(c1)) {
                    if (c1.getCellType() == CellType.NUMERIC) {
                        int valor = (int) c1.getNumericCellValue();
                        if (valor < 0) {
                            errosLinha.add(new ErroLinhaCarga(numeroLinha, "prazo_dias_corridos", "não pode ser negativo: " + valor));
                        } else {
                            prazoCorridos = valor;
                        }
                    } else {
                        errosLinha.add(new ErroLinhaCarga(numeroLinha, "prazo_dias_corridos", "célula não é numérica"));
                    }
                }

                // Coluna 2: data_vencimento (opcional)
                Cell c2 = row.getCell(2, Row.MissingCellPolicy.RETURN_BLANK_AS_NULL);
                LocalDate dataVencimento = null;
                if (!isVazio(c2)) {
                    if (c2.getCellType() == CellType.STRING) {
                        String texto = c2.getStringCellValue().trim();
                        try {
                            dataVencimento = LocalDate.parse(texto);
                        } catch (DateTimeParseException e) {
                            errosLinha.add(new ErroLinhaCarga(numeroLinha, "data_vencimento",
                                    "data inválida (esperado YYYY-MM-DD): \"" + texto + "\""));
                        }
                    } else if (c2.getCellType() == CellType.NUMERIC) {
                        errosLinha.add(new ErroLinhaCarga(numeroLinha, "data_vencimento",
                                "célula de data deve ser texto no formato YYYY-MM-DD, não número serial de data"));
                    } else {
                        errosLinha.add(new ErroLinhaCarga(numeroLinha, "data_vencimento",
                                "célula de data deve ser texto no formato YYYY-MM-DD, não número serial de data"));
                    }
                }

                // Coluna 3: taxa (obrigatório)
                Cell c3 = row.getCell(3, Row.MissingCellPolicy.RETURN_BLANK_AS_NULL);
                BigDecimal taxa = null;
                if (isVazio(c3)) {
                    errosLinha.add(new ErroLinhaCarga(numeroLinha, "taxa", "campo obrigatório vazio"));
                } else if (c3.getCellType() == CellType.NUMERIC) {
                    taxa = BigDecimal.valueOf(c3.getNumericCellValue());
                } else if (c3.getCellType() == CellType.STRING) {
                    String texto = c3.getStringCellValue().trim();
                    try {
                        taxa = ConversorDecimal.paraBigDecimal(texto, separadorDecimal);
                    } catch (IllegalArgumentException e) {
                        errosLinha.add(new ErroLinhaCarga(numeroLinha, "taxa", e.getMessage()));
                    }
                } else {
                    errosLinha.add(new ErroLinhaCarga(numeroLinha, "taxa", "célula não é numérica nem texto"));
                }

                // Coluna 4: fator_desconto (opcional)
                Cell c4 = row.getCell(4, Row.MissingCellPolicy.RETURN_BLANK_AS_NULL);
                BigDecimal fatorDesconto = null;
                if (!isVazio(c4)) {
                    if (c4.getCellType() == CellType.NUMERIC) {
                        fatorDesconto = BigDecimal.valueOf(c4.getNumericCellValue());
                    } else if (c4.getCellType() == CellType.STRING) {
                        String texto = c4.getStringCellValue().trim();
                        try {
                            fatorDesconto = ConversorDecimal.paraBigDecimal(texto, separadorDecimal);
                        } catch (IllegalArgumentException e) {
                            errosLinha.add(new ErroLinhaCarga(numeroLinha, "fator_desconto", e.getMessage()));
                        }
                    } else {
                        errosLinha.add(new ErroLinhaCarga(numeroLinha, "fator_desconto", "célula não é numérica nem texto"));
                    }
                }

                if (errosLinha.isEmpty()) {
                    verticesValidos.add(new VerticeComLinha(
                            numeroLinha,
                            new VerticeCurva(prazoUteis, prazoCorridos, dataVencimento, taxa, fatorDesconto)
                    ));
                } else {
                    todosErros.addAll(errosLinha);
                }
            }

            // Verificação de prazo_dias_uteis duplicado
            Set<Integer> prazosVistos = new HashSet<>();
            for (VerticeComLinha vl : verticesValidos) {
                if (!prazosVistos.add(vl.vertice().prazoDiasUteis())) {
                    todosErros.add(new ErroLinhaCarga(
                            vl.numeroLinha(),
                            "prazo_dias_uteis",
                            "prazo duplicado: " + vl.vertice().prazoDiasUteis()
                    ));
                }
            }

            if (todosErros.isEmpty() && verticesValidos.isEmpty()) {
                return new ResultadoLeituraCarga.Falha(List.of(
                        new ErroLinhaCarga(2, "arquivo", "arquivo vazio: nenhuma linha de dado encontrada")
                ));
            }

            if (!todosErros.isEmpty()) {
                return new ResultadoLeituraCarga.Falha(todosErros);
            }

            List<VerticeCurva> vertices = verticesValidos.stream()
                    .map(VerticeComLinha::vertice)
                    .toList();

            return new ResultadoLeituraCarga.Sucesso(vertices);

        } catch (Exception e) {
            String msg = e.getMessage() != null && !e.getMessage().isBlank()
                    ? e.getMessage()
                    : e.toString();
            return new ResultadoLeituraCarga.Falha(List.of(
                    new ErroLinhaCarga(1, "arquivo", "não foi possível abrir a planilha: " + msg)
            ));
        }
    }

    private static boolean isCabecalhoValido(Row cabecalhoRow) {
        for (int c = 0; c < CABECALHO_ESPERADO.size(); c++) {
            Cell cell = cabecalhoRow.getCell(c, Row.MissingCellPolicy.RETURN_BLANK_AS_NULL);
            String valor = "";
            if (cell != null && cell.getCellType() == CellType.STRING) {
                valor = cell.getStringCellValue().trim();
            }
            if (!CABECALHO_ESPERADO.get(c).equals(valor)) {
                return false;
            }
        }
        return true;
    }

    private static boolean isVazio(Cell celula) {
        return celula == null || celula.getCellType() == CellType.BLANK;
    }

    private record VerticeComLinha(int numeroLinha, VerticeCurva vertice) {
    }
}

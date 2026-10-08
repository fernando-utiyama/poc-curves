package br.com.poc.application.port.out;

import br.com.poc.domain.cadastro.ConstrucaoApagada;
import br.com.poc.domain.cadastro.LinhasPorTabela;
import br.com.poc.domain.cadastro.ResumoConstrucao;

import java.time.LocalDate;
import java.util.List;

public interface DadosConstruidosPort {

    /** Quantas datas da curva têm vértices construídos entre {@code de} e {@code ate} (inclusive), com a primeira e a última. */
    ResumoConstrucao resumo(String nomeCurva, LocalDate de, LocalDate ate);

    /** Apaga os vértices construídos (tDadoVertcCurva) e a interpolada (tDadoCurva) da curva na data. */
    ConstrucaoApagada apagar(String nomeCurva, LocalDate dataBase);

    /**
     * Tabelas com linhas da curva que têm chave estrangeira para tCurvaMercd (construído, dado bruto dos provedores
     * e tabelas legadas). Só as que têm linhas; lista vazia = a curva pode ser excluída.
     */
    List<LinhasPorTabela> dependentes(String nomeCurva);
}

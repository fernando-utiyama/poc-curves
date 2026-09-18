package com.poccurves.engine.application.service;
import com.poccurves.engine.application.construcao.CurvaJuros;
import com.poccurves.engine.application.construcao.Vertice;
import com.poccurves.engine.application.model.EstadoModelo;
import com.poccurves.engine.application.model.ModeloCurva;
import com.poccurves.engine.application.model.VerticeBtrs;
import com.poccurves.engine.application.model.VerticeConstruido;
import com.poccurves.engine.application.port.BtrsCurvaPrimrConsultaRepositoryPort;
import com.poccurves.engine.application.port.ConfgCurvaRepositoryPort;
import com.poccurves.engine.application.port.CurvaDataRepositoryPort;
import com.poccurves.engine.application.port.DadoCurvaRepositoryPort;
import com.poccurves.engine.application.port.ModeloCurvaRepositoryPort;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

/**
 * Construção das 5 curvas TS B3 (PRE/DCL/PTX/INP/DPL, openspec/changes/b3-additional-curves) —
 * schema legado (V22), substituindo por completo o antigo pipeline de curva importada
 * (definicao_curva/versao_curva) para essas 5 curvas.
 * <p>
 * A construção em si é despachada para um {@link ModeloCurva} cadastrado (BUILTIN ou GROOVY),
 * resolvido via {@link ModeloConstrucaoResolver} — a mesma infraestrutura de modelo plugável que
 * antes só servia a curva DI1/BOOTSTRAPPED (removida junto com a limpeza de BVBG.086/BVBG.028 no
 * curve-processor): {@code tConfgCurva.cMotorCalc} guarda o {@code codigo} do modelo, não mais um
 * identificador de {@code Interpolador} direto. O modelo padrão de todas as 5 curvas hoje é
 * {@link com.poccurves.engine.adapter.out.construcao.BuiltinModeloConstrucao#CODIGO_TAXA_SWAP_TRANSCRICAO_B3}
 * — montagem direta, sem recálculo (o Manual de Curvas da B3 confirma que os valores em {@code
 * tBtrsCurvaPrimr} já são o resultado final do cálculo da B3) — mas qualquer uma das 5 pode
 * apontar para um modelo GROOVY customizado sem mudança de código, cadastrado via {@code POST
 * /api/v1/modelos/validar-groovy}, se a metodologia real de uma curva for confirmada com a mesa.
 * <p>
 * {@code tDadoCurva} recebe os vértices construídos (saída do modelo); {@code tCurvaData} — "a
 * curva construída e interpolada" — espelha a mesma chave (FK real, {@code
 * FK_tDadoCurva_tCurvaData}) nesta primeira versão. Interpolação de verdade para um prazo fora
 * dos vértices publicados (não pré-materializada aqui — nenhuma outra curva do projeto faz isso)
 * é responsabilidade de um endpoint de consulta on-demand futuro, no mesmo padrão de
 * {@link com.poccurves.engine.application.usecase.InterpolacaoService}.
 */
public class ConstrucaoCurvaB3Service {

    private static final Logger log = LoggerFactory.getLogger(ConstrucaoCurvaB3Service.class);

    private final BtrsCurvaPrimrConsultaRepositoryPort btrsCurvaPrimrRepository;
    private final ConfgCurvaRepositoryPort confgCurvaRepository;
    private final ModeloCurvaRepositoryPort modeloCurvaRepository;
    private final ModeloConstrucaoResolver modeloConstrucaoResolver;
    private final DadoCurvaRepositoryPort dadoCurvaRepository;
    private final CurvaDataRepositoryPort curvaDataRepository;

    public ConstrucaoCurvaB3Service(
            BtrsCurvaPrimrConsultaRepositoryPort btrsCurvaPrimrRepository,
            ConfgCurvaRepositoryPort confgCurvaRepository,
            ModeloCurvaRepositoryPort modeloCurvaRepository,
            ModeloConstrucaoResolver modeloConstrucaoResolver,
            DadoCurvaRepositoryPort dadoCurvaRepository,
            CurvaDataRepositoryPort curvaDataRepository) {
        this.btrsCurvaPrimrRepository = btrsCurvaPrimrRepository;
        this.confgCurvaRepository = confgCurvaRepository;
        this.modeloCurvaRepository = modeloCurvaRepository;
        this.modeloConstrucaoResolver = modeloConstrucaoResolver;
        this.dadoCurvaRepository = dadoCurvaRepository;
        this.curvaDataRepository = curvaDataRepository;
    }

    @Transactional
    public void construir(String tickerIndcd, LocalDate dataReferencia) {
        List<VerticeBtrs> verticesBrutos = btrsCurvaPrimrRepository.buscarVertices(tickerIndcd, dataReferencia);
        if (verticesBrutos.isEmpty()) {
            throw new IllegalStateException(
                    "nenhum vértice em tBtrsCurvaPrimr para cTickerIndcd=" + tickerIndcd + " dBaseReft=" + dataReferencia);
        }

        String codigoModelo = confgCurvaRepository.buscarMotorCalcVigente(tickerIndcd, dataReferencia)
                .orElseThrow(() -> new IllegalStateException(
                        "nenhuma configuração vigente em tConfgCurva para cTickerIndcd=" + tickerIndcd + " dBaseReft=" + dataReferencia));
        ModeloCurva modelo = modeloCurvaRepository.buscarPorCodigo(codigoModelo)
                .orElseThrow(() -> new IllegalStateException(
                        "tConfgCurva.cMotorCalc aponta para um modelo inexistente: " + codigoModelo));
        if (modelo.estado() != EstadoModelo.ATIVO) {
            throw new IllegalStateException("modelo " + codigoModelo + " está " + modelo.estado() + ", esperado ATIVO");
        }

        List<Vertice> entrada = verticesBrutos.stream()
                .map(v -> new Vertice(v.diasUteis(), v.diasCorridos(), dataReferencia.plusDays(v.diasCorridos()), v.taxa(), null))
                .toList();

        CurvaJuros curvaConstruida = modeloConstrucaoResolver.construir(modelo, entrada);

        List<VerticeConstruido> vertices = curvaConstruida.vertices().stream()
                .map(v -> {
                    if (v.dataVencimento() == null) {
                        throw new IllegalStateException(
                                "modelo " + codigoModelo + " retornou vértice sem dataVencimento (obrigatória para tDadoCurva/tCurvaData): prazoDiasUteis=" + v.prazoDiasUteis());
                    }
                    return new VerticeConstruido(v.dataVencimento(), v.taxa());
                })
                .toList();

        // Ordem obrigatória por causa de FK_tDadoCurva_tCurvaData: tCurvaData precisa ser
        // esvaziado ANTES de tDadoCurva ser apagado/reinserido, senão um reprocessamento
        // (mesma cTickerIndcd/dBaseReft de uma execução anterior) viola a FK ao tentar apagar
        // vértices de tDadoCurva que linhas de tCurvaData ainda referenciam.
        curvaDataRepository.excluirPontos(tickerIndcd, dataReferencia);
        dadoCurvaRepository.substituirVertices(tickerIndcd, dataReferencia, vertices);
        curvaDataRepository.inserirPontos(tickerIndcd, dataReferencia, vertices);

        log.info("Construção TS B3 finalizada: cTickerIndcd={} dBaseReft={} modelo={} quantidadeVertices={}",
                tickerIndcd, dataReferencia, codigoModelo, vertices.size());
    }
}

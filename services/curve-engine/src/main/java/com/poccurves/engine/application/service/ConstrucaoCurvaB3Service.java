package com.poccurves.engine.application.service;
import com.poccurves.engine.application.construcao.InterpoladorRegistry;
import com.poccurves.engine.application.model.VerticeBtrs;
import com.poccurves.engine.application.model.VerticeConstruido;
import com.poccurves.engine.application.port.BtrsCurvaPrimrConsultaRepositoryPort;
import com.poccurves.engine.application.port.ConfgCurvaRepositoryPort;
import com.poccurves.engine.application.port.CurvaDataRepositoryPort;
import com.poccurves.engine.application.port.DadoCurvaRepositoryPort;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

/**
 * Construção das 5 curvas TS B3 (PRE/DCL/PTX/INP/DPL, openspec/changes/b3-additional-curves) —
 * schema legado (V22), substituindo por completo o pipeline antigo de curva importada
 * (definicao_curva/versao_curva) para essas 5 curvas.
 * <p>
 * Ao contrário de {@link ConstrucaoCurvaService} (DI1, modo BOOTSTRAPPED — calibra um modelo a
 * partir de insumos de mercado brutos), aqui não há bootstrap: o Manual de Curvas da B3 confirma
 * que os valores em {@code tBtrsCurvaPrimr} já são o resultado final do cálculo da B3 — o engine
 * só valida, resolve a data real de cada vértice (dBaseReft + diasCorridos — dias corridos não
 * depende de calendário de pregão) e persiste.
 * <p>
 * {@code tDadoCurva} recebe os vértices construídos (transcrição validada de {@code
 * tBtrsCurvaPrimr}); {@code tCurvaData} — "a curva construída e interpolada" — espelha a mesma
 * chave (FK real, {@code FK_tDadoCurva_tCurvaData}) nesta primeira versão, já com o interpolador
 * configurado por curva ({@code tConfgCurva.cMotorCalc}) resolvido e validado. Interpolação de
 * verdade para um prazo fora dos vértices publicados (não pré-materializada aqui — nenhuma outra
 * curva do projeto faz isso) é responsabilidade de um endpoint de consulta on-demand futuro, no
 * mesmo padrão de {@link com.poccurves.engine.application.usecase.InterpolacaoService}.
 */
public class ConstrucaoCurvaB3Service {

    private static final Logger log = LoggerFactory.getLogger(ConstrucaoCurvaB3Service.class);

    private final BtrsCurvaPrimrConsultaRepositoryPort btrsCurvaPrimrRepository;
    private final ConfgCurvaRepositoryPort confgCurvaRepository;
    private final InterpoladorRegistry interpoladorRegistry;
    private final DadoCurvaRepositoryPort dadoCurvaRepository;
    private final CurvaDataRepositoryPort curvaDataRepository;

    public ConstrucaoCurvaB3Service(
            BtrsCurvaPrimrConsultaRepositoryPort btrsCurvaPrimrRepository,
            ConfgCurvaRepositoryPort confgCurvaRepository,
            InterpoladorRegistry interpoladorRegistry,
            DadoCurvaRepositoryPort dadoCurvaRepository,
            CurvaDataRepositoryPort curvaDataRepository) {
        this.btrsCurvaPrimrRepository = btrsCurvaPrimrRepository;
        this.confgCurvaRepository = confgCurvaRepository;
        this.interpoladorRegistry = interpoladorRegistry;
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

        String motorCalc = confgCurvaRepository.buscarMotorCalcVigente(tickerIndcd, dataReferencia)
                .orElseThrow(() -> new IllegalStateException(
                        "nenhuma configuração vigente em tConfgCurva para cTickerIndcd=" + tickerIndcd + " dBaseReft=" + dataReferencia));
        // Falha cedo e nomeado se cMotorCalc apontar para um identificador não registrado em
        // InterpoladorRegistry, em vez de deixar isso só aparecer quando um endpoint de
        // interpolação futuro tentar resolvê-lo.
        interpoladorRegistry.resolver(motorCalc);

        List<VerticeConstruido> vertices = verticesBrutos.stream()
                .map(v -> new VerticeConstruido(dataReferencia.plusDays(v.diasCorridos()), v.taxa()))
                .toList();

        // Ordem obrigatória por causa de FK_tDadoCurva_tCurvaData: tCurvaData precisa ser
        // esvaziado ANTES de tDadoCurva ser apagado/reinserido, senão um reprocessamento
        // (mesma cTickerIndcd/dBaseReft de uma execução anterior) viola a FK ao tentar apagar
        // vértices de tDadoCurva que linhas de tCurvaData ainda referenciam.
        curvaDataRepository.excluirPontos(tickerIndcd, dataReferencia);
        dadoCurvaRepository.substituirVertices(tickerIndcd, dataReferencia, vertices);
        curvaDataRepository.inserirPontos(tickerIndcd, dataReferencia, vertices);

        log.info("Construção TS B3 finalizada: cTickerIndcd={} dBaseReft={} motorCalc={} quantidadeVertices={}",
                tickerIndcd, dataReferencia, motorCalc, vertices.size());
    }
}

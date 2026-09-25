package br.com.poc.adapter.out.persistence.jpa;

import br.com.poc.adapter.out.persistence.jpa.entity.*;
import br.com.poc.adapter.out.persistence.jpa.repository.*;
import br.com.poc.application.port.out.CurvaPersistencePort;
import br.com.poc.domain.model.ContextoConstrucaoCurva;
import br.com.poc.domain.model.VerticeCalculado;
import br.com.poc.domain.model.VerticeInsumo;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Component
public class CurvaJpaPersistenceAdapter implements CurvaPersistencePort {

    private final ConfgCurvaJpaRepository confgCurvaRepo;
    private final ParmConfgCurvaJpaRepository parmConfgCurvaRepo;
    private final DadoCurvaJpaRepository dadoCurvaRepo;
    private final DadoVertcCurvaJpaRepository dadoVertcCurvaRepo;
    private final MtrizCurvaJpaRepository mtrizCurvaRepo;

    public CurvaJpaPersistenceAdapter(
        ConfgCurvaJpaRepository confgCurvaRepo,
        ParmConfgCurvaJpaRepository parmConfgCurvaRepo,
        DadoCurvaJpaRepository dadoCurvaRepo,
        DadoVertcCurvaJpaRepository dadoVertcCurvaRepo,
        MtrizCurvaJpaRepository mtrizCurvaRepo
    ) {
        this.confgCurvaRepo = confgCurvaRepo;
        this.parmConfgCurvaRepo = parmConfgCurvaRepo;
        this.dadoCurvaRepo = dadoCurvaRepo;
        this.dadoVertcCurvaRepo = dadoVertcCurvaRepo;
        this.mtrizCurvaRepo = mtrizCurvaRepo;
    }

    @Override
    public Optional<ContextoConstrucaoCurva> obterContextoConfiguracao(String ticker, LocalDate dataBase) {
        return confgCurvaRepo.findByTickerIndcd(ticker).map(config -> {
            Map<String, String> params = new HashMap<>();
            List<ParmConfgCurvaEntity> parms = parmConfgCurvaRepo.findByIdtfdConfg(config.getIdtfdConfg());
            for (ParmConfgCurvaEntity p : parms) {
                if (p.getTpoInstt() != null) {
                    params.put(p.getConfgIdtfd(), p.getTpoInstt());
                } else if (p.getPrecoTx() != null) {
                    params.put(p.getConfgIdtfd(), p.getPrecoTx().toPlainString());
                }
            }

            if (config.getRotnaCalc() != null) {
                params.put("ROTINA_CALC", config.getRotnaCalc());
            }
            if (config.getModDado() != null) {
                params.put("MOD_DADO", config.getModDado());
            }
            return new ContextoConstrucaoCurva(ticker, dataBase, params);
        });
    }

    @Override
    public String obterMotorConfigurado(String ticker) {
        return confgCurvaRepo.findByTickerIndcd(ticker)
            .map(ConfgCurvaEntity::getMotorCalc)
            .orElse(null);
    }

    @Override
    public List<VerticeInsumo> carregarInsumos(String ticker, LocalDate dataBase) {
        List<DadoCurvaEntity> dados = dadoCurvaRepo.findByBaseReftAndTickerIndcdOrderByDiaUtilAsc(dataBase, ticker);
        if (dados.isEmpty()) {
            dados = dadoCurvaRepo.findByBaseReftAndTickerIndcdOrderByQtdDiaReftAsc(dataBase, ticker);
        }

        return dados.stream().map(d -> new VerticeInsumo(
            d.getTickerIndcd(),
            d.getBaseReft(),
            d.getVerticeReferencia(),
            d.getDiaUtil(),
            d.getQtdDiaReft(),
            d.getPrecoTx(),
            d.getDiaFator(),
            "M"
        )).toList();
    }

    @Override
    public List<VerticeCalculado> carregarVerticesConsolidados(String ticker, LocalDate dataBase) {
        List<DadoVertcCurvaEntity> entities = dadoVertcCurvaRepo.findByBaseReftAndTickerIndcdOrderByDiaUtilAsc(dataBase, ticker);
        return entities.stream().map(e -> new VerticeCalculado(
            e.getTickerIndcd(),
            e.getBaseReft(),
            e.getVerticeReferencia(),
            e.getDiaUtil(),
            e.getQtdDiaReft(),
            e.getQtdDiaPer(),
            e.getPrecoTx(),
            e.getFatorDia(),
            e.getFatorAcum()
        )).toList();
    }

    @Override
    @Transactional
    public void salvarVerticesConsolidados(String ticker, LocalDate dataBase, List<VerticeCalculado> vertices) {
        // Idempotência: remove registros anteriores para a mesma chave de negócio
        dadoVertcCurvaRepo.deleteByBaseReftAndTickerIndcd(dataBase, ticker);

        List<DadoVertcCurvaEntity> entities = vertices.stream().map(v -> DadoVertcCurvaEntity.builder()
            .baseReft(v.dataBase())
            .tickerIndcd(v.ticker())
            .verticeReferencia(v.dataVertice())
            .diaUtil(v.diasUteis())
            .qtdDiaReft(v.diasCorridos())
            .qtdDiaPer(v.diasPeriodo())
            .precoTx(v.taxaAnualizada())
            .fatorDia(v.fatorDiario())
            .fatorAcum(v.fatorAcumulado())
            .build()
        ).toList();

        dadoVertcCurvaRepo.saveAll(entities);

        // Atualiza ou insere cabeçalho na matriz
        MtrizCurvaEntity mtriz = mtrizCurvaRepo.findByTickerIndcdAndBaseReft(ticker, dataBase)
            .orElseGet(() -> MtrizCurvaEntity.builder()
                .tickerIndcd(ticker)
                .baseReft(dataBase)
                .formtArq("JSON")
                .modLyoutCarga("ENGINE_UNIFICADO")
                .vrsaoReg(0)
                .criacReg(LocalDateTime.now())
                .build());

        mtriz.setVrsaoReg(mtriz.getVrsaoReg() + 1);
        mtriz.setUltAtulz(LocalDateTime.now());
        mtrizCurvaRepo.save(mtriz);
    }
}

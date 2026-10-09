package br.com.poc.adapter.out.persistence;

import br.com.poc.adapter.out.persistence.entity.BbergCurvaPrimrEntity;
import br.com.poc.adapter.out.persistence.repository.BbergCurvaPrimrRepository;
import br.com.poc.adapter.out.persistence.repository.CurvaPrimrAgregadoProjection;
import br.com.poc.application.port.out.BbergCurvaPrimrRepositoryPort;
import br.com.poc.application.port.out.CurvaPrvdrRepositoryPort;
import br.com.poc.domain.cadastro.BbergCurvaPrimr;
import br.com.poc.domain.cadastro.CurvaPrimrResumo;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Component
public class BbergCurvaPrimrPersistenceAdapter implements BbergCurvaPrimrRepositoryPort {

    private static final String PROVEDOR = "BLOOMBERG";

    private final BbergCurvaPrimrRepository repository;
    private final CurvaPrvdrRepositoryPort curvaPrvdrRepositoryPort;

    public BbergCurvaPrimrPersistenceAdapter(BbergCurvaPrimrRepository repository,
                                             CurvaPrvdrRepositoryPort curvaPrvdrRepositoryPort) {
        this.repository = repository;
        this.curvaPrvdrRepositoryPort = curvaPrvdrRepositoryPort;
    }

    @Override
    public int proximoId() {
        Integer next = repository.proximoId();
        return next != null ? next : 1;
    }

    @Override
    public List<BbergCurvaPrimr> findByNomeCurvaAndDataBase(String nomeCurva, LocalDate dataBase) {
        return repository.findByTickerIndcdAndDataBaseReftOrderByDataVctoContrAscIdtfdUnicAsc(nomeCurva, dataBase).stream()
            .map(this::toVerticeDomain)
            .toList();
    }

    @Override
    public Optional<BbergCurvaPrimr> findByIdAndNomeCurvaAndDataBase(Integer id, String nomeCurva, LocalDate dataBase) {
        return repository.findByIdtfdUnicAndTickerIndcdAndDataBaseReft(id, nomeCurva, dataBase)
            .map(this::toVerticeDomain);
    }

    @Override
    public BbergCurvaPrimr salvar(BbergCurvaPrimr vertice) {
        BbergCurvaPrimrEntity entity = new BbergCurvaPrimrEntity();
        entity.setIdtfdUnic(vertice.id());
        entity.setTickerIndcd(vertice.nomeCurva());
        entity.setDataBaseReft(vertice.dataBase());
        entity.setPrecoLiqdc(vertice.precoLiquidacao());
        entity.setPrecoMed(vertice.precoMedio());
        entity.setPrecoUlt(vertice.precoUltimo());
        entity.setDiaVcto(vertice.diaVencimento());
        entity.setDataLiqdcFincr(vertice.dataLiquidacaoFinanceira());
        entity.setTickerBberg(vertice.tickerBloomberg());
        entity.setFormaLiqdc(vertice.formaLiquidacao());
        entity.setDataVctoContr(vertice.dataVencimentoContrato());
        entity.setDataUltNegoc(vertice.dataUltimoNegocio());

        return toVerticeDomain(repository.save(entity));
    }

    @Override
    public void excluir(Integer id, String nomeCurva, LocalDate dataBase) {
        repository.deleteByIdtfdUnicAndTickerIndcdAndDataBaseReft(id, nomeCurva, dataBase);
    }

    @Override
    public int excluirPorNomeCurvaEDataBase(String nomeCurva, LocalDate dataBase) {
        return repository.deleteByTickerIndcdAndDataBaseReft(nomeCurva, dataBase);
    }

    @Override
    public boolean existsCurvaConstruida(String nomeCurva, LocalDate dataBase) {
        return Integer.valueOf(1).equals(repository.existsCurvaConstruida(nomeCurva, dataBase));
    }

    @Override
    public List<CurvaPrimrResumo> listarAgregado(LocalDate de, LocalDate ate, String codigo, String nome) {
        return paraResumos(repository.listarAgregado(de, ate, codigo, nome));
    }

    @Override
    public List<CurvaPrimrResumo> listarUltimaData(String codigo, String nome) {
        return paraResumos(repository.listarUltimaData(codigo, nome));
    }

    private List<CurvaPrimrResumo> paraResumos(List<CurvaPrimrAgregadoProjection> linhas) {
        // sem filtro de produto, como estava
        return AgregadoPrimr.paraResumos(
            linhas,
            AgregadoPrimr.tickersPorCurva(curvaPrvdrRepositoryPort, PROVEDOR, null));
    }

    private BbergCurvaPrimr toVerticeDomain(BbergCurvaPrimrEntity e) {
        return new BbergCurvaPrimr(
            e.getIdtfdUnic(),
            e.getTickerIndcd(),
            e.getDataBaseReft(),
            e.getPrecoLiqdc(),
            e.getPrecoMed(),
            e.getPrecoUlt(),
            e.getDiaVcto(),
            e.getDataLiqdcFincr(),
            e.getTickerBberg(),
            e.getFormaLiqdc(),
            e.getDataVctoContr(),
            e.getDataUltNegoc()
        );
    }
}

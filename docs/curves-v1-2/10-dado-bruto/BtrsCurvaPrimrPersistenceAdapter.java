package br.com.poc.adapter.out.persistence;

import br.com.poc.adapter.out.persistence.entity.BtrsCurvaPrimrEntity;
import br.com.poc.adapter.out.persistence.repository.BtrsCurvaPrimrRepository;
import br.com.poc.adapter.out.persistence.repository.CurvaPrimrDataBaseProjection;
import br.com.poc.application.port.out.BtrsCurvaPrimrRepositoryPort;
import br.com.poc.application.port.out.CurvaPrvdrRepositoryPort;
import br.com.poc.domain.cadastro.BtrsCurvaPrimr;
import br.com.poc.domain.cadastro.CurvaPrimrDataBase;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Component
public class BtrsCurvaPrimrPersistenceAdapter implements BtrsCurvaPrimrRepositoryPort {

    private static final String PROVEDOR = "B3";
    private static final String PRODUTO = "TS";

    private final BtrsCurvaPrimrRepository repository;
    private final CurvaPrvdrRepositoryPort curvaPrvdrRepositoryPort;

    public BtrsCurvaPrimrPersistenceAdapter(BtrsCurvaPrimrRepository repository,
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
    public List<BtrsCurvaPrimr> findByNomeCurvaAndDataBase(String nomeCurva, LocalDate dataBase) {
        return repository.findByTickerIndcdAndDataBaseReftOrderByDiaCorriAscIdtfdUnicAsc(nomeCurva, dataBase).stream()
            .map(this::toDomain)
            .toList();
    }

    @Override
    public Optional<BtrsCurvaPrimr> findByIdAndNomeCurvaAndDataBase(Integer id, String nomeCurva, LocalDate dataBase) {
        return repository.findByIdtfdUnicAndTickerIndcdAndDataBaseReft(id, nomeCurva, dataBase).map(this::toDomain);
    }

    @Override
    public BtrsCurvaPrimr salvar(BtrsCurvaPrimr ponto) {
        BtrsCurvaPrimrEntity entity = new BtrsCurvaPrimrEntity(
            ponto.id(),
            ponto.nomeCurva(),
            ponto.diasCorridos(),
            ponto.diasUteis(),
            ponto.dataBase(),
            ponto.fatorAcumulado(),
            ponto.valor(),
            ponto.fatorDia()
        );
        return toDomain(repository.save(entity));
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
    public boolean existeVerticeConstruido(String nomeCurva, LocalDate dataBase) {
        return Integer.valueOf(1).equals(repository.existeVerticeConstruido(nomeCurva, dataBase));
    }

    @Override
    public List<CurvaPrimrDataBase> listarDatasBase(LocalDate de, LocalDate ate, String codigo, String nome) {
        return paraDatasBase(repository.listarDatasBase(de, ate, codigo, nome));
    }

    @Override
    public List<CurvaPrimrDataBase> listarUltimaDataBase(String codigo, String nome) {
        return paraDatasBase(repository.listarUltimaDataBase(codigo, nome));
    }

    private List<CurvaPrimrDataBase> paraDatasBase(List<CurvaPrimrDataBaseProjection> linhas) {
        return CurvaPrimrDataBaseMapper.paraDatasBase(
            linhas,
            CurvaPrimrDataBaseMapper.tickersPorCurva(curvaPrvdrRepositoryPort, PROVEDOR, PRODUTO));
    }

    private BtrsCurvaPrimr toDomain(BtrsCurvaPrimrEntity e) {
        return new BtrsCurvaPrimr(
            e.getIdtfdUnic(),
            e.getTickerIndcd(),
            e.getDataBaseReft(),
            e.getDiaCorri(),
            e.getDiaUtil(),
            e.getPrecoTx(),
            e.getFatorAcum(),
            e.getFatorDia()
        );
    }
}

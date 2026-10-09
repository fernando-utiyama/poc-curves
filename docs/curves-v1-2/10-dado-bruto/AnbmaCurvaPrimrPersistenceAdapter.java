package br.com.poc.adapter.out.persistence;

import br.com.poc.adapter.out.persistence.entity.AnbmaCurvaPrimrEntity;
import br.com.poc.adapter.out.persistence.repository.AnbmaCurvaPrimrRepository;
import br.com.poc.adapter.out.persistence.repository.CurvaPrimrAgregadoProjection;
import br.com.poc.application.port.out.AnbmaCurvaPrimrRepositoryPort;
import br.com.poc.application.port.out.CurvaPrvdrRepositoryPort;
import br.com.poc.domain.cadastro.AnbmaCurvaPrimr;
import br.com.poc.domain.cadastro.CurvaPrimrResumo;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Component
public class AnbmaCurvaPrimrPersistenceAdapter implements AnbmaCurvaPrimrRepositoryPort {

    private static final String PROVEDOR = "ANBIMA";

    private final AnbmaCurvaPrimrRepository repository;
    private final CurvaPrvdrRepositoryPort curvaPrvdrRepositoryPort;

    public AnbmaCurvaPrimrPersistenceAdapter(AnbmaCurvaPrimrRepository repository,
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
    public List<AnbmaCurvaPrimr> findByNomeCurvaAndDataBase(String nomeCurva, LocalDate dataBase) {
        return repository.findByTickerIndcdAndDataBaseReftOrderByVertcCurvaAscIdtfdUnicAsc(nomeCurva, dataBase).stream()
            .map(this::toDomain)
            .toList();
    }

    @Override
    public Optional<AnbmaCurvaPrimr> findByIdAndNomeCurvaAndDataBase(Integer id, String nomeCurva, LocalDate dataBase) {
        return repository.findByIdtfdUnicAndTickerIndcdAndDataBaseReft(id, nomeCurva, dataBase).map(this::toDomain);
    }

    @Override
    public AnbmaCurvaPrimr salvar(AnbmaCurvaPrimr ponto) {
        AnbmaCurvaPrimrEntity entity = new AnbmaCurvaPrimrEntity(
            ponto.id(),
            ponto.nomeCurva(),
            ponto.dataBase(),
            ponto.taxa(),
            ponto.vertice()
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

    private AnbmaCurvaPrimr toDomain(AnbmaCurvaPrimrEntity entity) {
        return new AnbmaCurvaPrimr(
            entity.getIdtfdUnic(),
            entity.getTickerIndcd(),
            entity.getDataBaseReft(),
            entity.getPrecoTx(),
            entity.getVertcCurva()
        );
    }
}

package br.com.poc.adapter.out.persistence;

import br.com.poc.adapter.out.persistence.entity.ConfgCurvaEntity;
import br.com.poc.adapter.out.persistence.repository.ConfgCurvaRepository;
import br.com.poc.application.port.out.ConfiguracaoCurvaRepositoryPort;
import br.com.poc.domain.cadastro.ConfiguracaoCurva;
import br.com.poc.domain.cadastro.ParametrosCalculo;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Component
@RequiredArgsConstructor
public class ConfgCurvaPersistenceAdapter implements ConfiguracaoCurvaRepositoryPort {

    private final ConfgCurvaRepository repository;
    private final JsonMapper jsonMapper;

    @Override
    public Optional<String> findModeloConstrucaoVigente(String nomeCurva, LocalDate dataBase) {
        return repository.findModeloConstrucaoVigente(nomeCurva, dataBase);
    }

    @Override
    public List<ConfiguracaoCurva> findByNomeCurva(String nomeCurva) {
        return repository.findByNomeCurvaOrderByVersaoDesc(nomeCurva).stream()
            .map(this::toDomain)
            .toList();
    }

    @Override
    public Optional<ConfiguracaoCurva> findVigente(String nomeCurva, LocalDate data) {
        return repository.findVigente(nomeCurva, data).map(this::toDomain);
    }

    @Override
    public Optional<ConfiguracaoCurva> findUltimaVersao(String nomeCurva) {
        return repository.findFirstByNomeCurvaOrderByVersaoDesc(nomeCurva).map(this::toDomain);
    }

    @Override
    public Optional<ConfiguracaoCurva> findByNomeCurvaEVersao(String nomeCurva, int versao) {
        return repository.findByNomeCurvaAndVersao(nomeCurva, versao).map(this::toDomain);
    }

    @Override
    public ConfiguracaoCurva salvar(ConfiguracaoCurva configuracao) {
        // Na atualização, parte do registro existente para não zerar as colunas que o serviço não gerencia.
        ConfgCurvaEntity entity = configuracao.id() == null
            ? new ConfgCurvaEntity()
            : repository.findById(configuracao.id()).orElseGet(ConfgCurvaEntity::new);
        copiarCampos(configuracao, entity);
        return toDomain(repository.save(entity));
    }

    @Override
    public void excluir(Long id) {
        repository.deleteById(id);
    }

    private ConfiguracaoCurva toDomain(ConfgCurvaEntity entity) {
        return new ConfiguracaoCurva(
            entity.getId(),
            entity.getNomeCurva(),
            entity.getVersao(),
            entity.getModeloConstrucao(),
            entity.getInterpolador(),
            lerParametros(entity.getParametrosJson()),
            entity.getInicioVigencia(),
            entity.getFimVigencia()
        );
    }

    private void copiarCampos(ConfiguracaoCurva domain, ConfgCurvaEntity entity) {
        entity.setId(domain.id());
        entity.setNomeCurva(domain.nomeCurva());
        entity.setModeloConstrucao(domain.modeloConstrucao());
        entity.setInterpolador(domain.interpolador());
        entity.setParametrosJson(domain.parametros() != null ? jsonMapper.writeValueAsString(domain.parametros()) : null);
        entity.setVersao(domain.versao());
        entity.setInicioVigencia(domain.inicioVigencia());
        entity.setFimVigencia(domain.fimVigencia());
    }

    /** cModDado gravado por outro sistema com formato inválido volta como nulo (o engine acusa CADASTRO_INVALIDO). */
    private ParametrosCalculo lerParametros(String json) {
        if (json == null || json.isBlank()) {
            return null;
        }
        try {
            return jsonMapper.readValue(json, ParametrosCalculo.class);
        } catch (RuntimeException e) {
            return null;
        }
    }
}

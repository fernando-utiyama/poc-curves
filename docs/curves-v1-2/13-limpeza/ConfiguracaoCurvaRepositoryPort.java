package br.com.poc.application.port.out;

import br.com.poc.domain.cadastro.ConfiguracaoCurva;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface ConfiguracaoCurvaRepositoryPort {

    List<ConfiguracaoCurva> findByNomeCurva(String nomeCurva);

    Optional<ConfiguracaoCurva> findVigente(String nomeCurva, LocalDate data);

    Optional<ConfiguracaoCurva> findUltimaVersao(String nomeCurva);

    Optional<ConfiguracaoCurva> findByNomeCurvaEVersao(String nomeCurva, int versao);

    ConfiguracaoCurva salvar(ConfiguracaoCurva configuracao);

    void excluir(Long id);
}

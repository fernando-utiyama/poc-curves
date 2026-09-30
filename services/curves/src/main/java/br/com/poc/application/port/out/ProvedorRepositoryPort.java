package br.com.poc.application.port.out;

import br.com.poc.application.model.Provedor;
import java.util.List;
import java.util.Optional;

public interface ProvedorRepositoryPort {

    boolean existsById(String nomeProvedor);

    Provedor save(Provedor provedor);

    Optional<Provedor> findById(String nomeProvedor);

    List<Provedor> findAll();

    void deleteById(String nomeProvedor);

    List<Provedor> filtrarPorTexto(String texto);
}

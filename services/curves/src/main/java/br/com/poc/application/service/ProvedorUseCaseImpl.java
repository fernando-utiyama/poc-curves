package br.com.poc.application.service;

import br.com.poc.application.dto.v1.AtualizarProvedorCommand;
import br.com.poc.application.dto.v1.CriarProvedorCommand;
import br.com.poc.application.dto.v1.ProvedorResult;
import br.com.poc.application.port.in.usecase.ProvedorUseCase;
import br.com.poc.application.exception.ProvedorJaExisteException;
import br.com.poc.application.exception.ProvedorNaoEncontradoException;
import br.com.poc.application.model.Provedor;
import br.com.poc.application.port.out.ProvedorRepositoryPort;
import java.util.List;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class ProvedorUseCaseImpl implements ProvedorUseCase {

    private final ProvedorRepositoryPort provedorRepositoryPort;

    @Override
    public ProvedorResult criar(CriarProvedorCommand command) {
        if (provedorRepositoryPort.existsById(command.nomeProvedor())) {
            throw new ProvedorJaExisteException(command.nomeProvedor());
        }

        Provedor provedor = new Provedor(
            command.nomeProvedor(),
            command.descricao(),
            command.produto(),
            command.nomeCompletoAtivoOuInstrumento()
        );

        Provedor salvo = provedorRepositoryPort.save(provedor);
        return toResult(salvo);
    }

    @Override
    public ProvedorResult buscarPorId(String nomeProvedor) {
        Provedor provedor = provedorRepositoryPort.findById(nomeProvedor)
            .orElseThrow(() -> new ProvedorNaoEncontradoException(nomeProvedor));

        return toResult(provedor);
    }

    @Override
    public List<ProvedorResult> listarTodos() {
        return provedorRepositoryPort.findAll().stream()
            .map(this::toResult)
            .toList();
    }

    @Override
    public ProvedorResult atualizar(String nomeProvedor, AtualizarProvedorCommand command) {
        Provedor existente = provedorRepositoryPort.findById(nomeProvedor)
            .orElseThrow(() -> new ProvedorNaoEncontradoException(nomeProvedor));


        if (!nomeProvedor.equals(command.nomeProvedor()) && provedorRepositoryPort.existsById(command.nomeProvedor())) {
            throw new ProvedorJaExisteException(command.nomeProvedor());
        }

        Provedor atualizado = existente.atualizar(
            command.nomeProvedor(),
            command.descricao(),
            command.produto(),
            command.nomeCompletoAtivoOuInstrumento()
        );

        if(!nomeProvedor.equals(command.nomeProvedor())){
            provedorRepositoryPort.deleteById(nomeProvedor);
        }

        Provedor salvo = provedorRepositoryPort.save(atualizado);
        return toResult(salvo);
    }

    @Override
    public void excluir(String nomeProvedor) {
        if (!provedorRepositoryPort.existsById(nomeProvedor)) {
            throw new ProvedorNaoEncontradoException(nomeProvedor);
        }

        provedorRepositoryPort.deleteById(nomeProvedor);
    }

    @Override
    public List<ProvedorResult> filtrarPorTexto(String texto) {
        return provedorRepositoryPort.filtrarPorTexto(texto)
            .stream()
            .map(this::toResult)
            .toList();
    }

    private ProvedorResult toResult(Provedor provedor) {
        return new ProvedorResult(
            provedor.getNomeProvedor(),
            provedor.getDescricao(),
            provedor.getProduto(),
            provedor.getNomeCompletoAtivoOuInstrumento()
        );
    }
}

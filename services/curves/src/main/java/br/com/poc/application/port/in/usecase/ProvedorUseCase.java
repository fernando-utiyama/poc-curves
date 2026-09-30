package br.com.poc.application.port.in.usecase;

import br.com.poc.application.dto.v1.AtualizarProvedorCommand;
import br.com.poc.application.dto.v1.CriarProvedorCommand;
import br.com.poc.application.dto.v1.ProvedorResult;

import java.util.List;

public interface ProvedorUseCase {

    ProvedorResult criar(CriarProvedorCommand command);

    ProvedorResult buscarPorId(String nomeProvedor);

    List<ProvedorResult> listarTodos();

    ProvedorResult atualizar(String nomeProvedor, AtualizarProvedorCommand command);

    void excluir(String nomeProvedor);

    List<ProvedorResult> filtrarPorTexto(String texto);
}

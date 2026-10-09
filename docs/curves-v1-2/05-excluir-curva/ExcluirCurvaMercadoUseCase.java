package br.com.poc.application.port.in.usecase;

public interface ExcluirCurvaMercadoUseCase {

    /**
     * Exclui a curva com os provedores e as configurações dela. Recusa se ainda há linhas dela em outra tabela
     * (construído ou dado bruto: apague antes, ou use a inativação) ou se é componente de outra curva.
     */
    void excluir(String nome);
}

package br.com.poc.application.exception;

/** A curva ainda tem dados em outra tabela (construído, dado bruto): o banco recusou a exclusão. */
public class CurvaComDadosVinculadosException extends RuntimeException {

    public CurvaComDadosVinculadosException(Throwable causa) {
        super(causa);
    }
}

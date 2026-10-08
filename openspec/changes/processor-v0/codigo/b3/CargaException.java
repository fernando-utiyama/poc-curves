package br.com.poc.application.exception;

/**
 * Erro de carga com o status e o código da spec. Se o processor já tem exceções com status (BusinessException etc.),
 * use as dele no lugar desta: só o status e o código importam.
 */
public class CargaException extends RuntimeException {

    private final int status;
    private final String codigo;

    private CargaException(int status, String codigo, String mensagem) {
        super(mensagem);
        this.status = status;
        this.codigo = codigo;
    }

    /** 503: arquivo ainda não publicado; o orquestrador tenta de novo. */
    public static CargaException indisponivel(String mensagem) {
        return new CargaException(503, "ARQUIVO_INDISPONIVEL", mensagem);
    }

    /** 502: a fonte respondeu com erro. */
    public static CargaException fonteIndisponivel(String mensagem) {
        return new CargaException(502, "FONTE_INDISPONIVEL", mensagem);
    }

    /** 422: o arquivo chegou, mas está fora do leiaute. */
    public static CargaException invalido(String mensagem) {
        return new CargaException(422, "ARQUIVO_INVALIDO", mensagem);
    }

    public int status() {
        return status;
    }

    public String codigo() {
        return codigo;
    }
}

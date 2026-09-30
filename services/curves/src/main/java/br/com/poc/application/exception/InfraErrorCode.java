package br.com.poc.application.exception;

/**
 * Enumeração de códigos de erro específicos para falhas da aplicação ou de infraestrutura e componentes técnicos.
 *
 * <p>Esta enumeração centraliza todos os códigos de erro relacionados a problemas de infraestrutura
 * que podem afetar o funcionamento da aplicação, incluindo falhas de persistência, acesso a dados,
 * conectividade e componentes técnicos subjacentes. Estes erros são distintos dos erros de negócio
 * e representam problemas técnicos que podem requerer intervenção de infraestrutura.</p>
 *
 * <p>A infraestrutura em sistemas bancários é crítica e os seus erros devem ser tratados com alta
 * prioridade, pois podem impactar operações financeiras, conformidade regulatória e experiência
 * do cliente. Esta enumeração facilita a identificação, categorização e resolução destes problemas.</p>
 *
 * <p>Características dos códigos de erro de infraestrutura:</p>
 * <ul>
 *   <li><strong>Padrão de nomenclatura</strong>: INFRAERR + número sequencial (INFRAERR001, INFRAERR002...)</li>
 *   <li><strong>Escopo técnico</strong>: Focados em componentes de infraestrutura e não em regras de negócio</li>
 *   <li><strong>Criticidade alta</strong>: Indicam problemas que podem afetar a disponibilidade do sistema</li>
 *   <li><strong>Monitoramento</strong>: Adequados para alertas automáticos e dashboards de infraestrutura</li>
 * </ul>
 *
 * @see InfrastructureException
 * @see ServiceUnavailableException
 * @since 1.0.0
 */
public enum InfraErrorCode implements ErrorCode {

    JPA_SYSTEM("Erro no sistema JPA"),
    ACCESS_DATABASE("Erro ao acessar o banco de dados"),

    /**
     * Erro indicando que um serviço está temporariamente indisponível.
     */
    SERVICE_UNAVAILABLE("Serviço temporariamente indisponível"),

    /**
     * Erro indicando que uma chamada não foi permitida por políticas de acesso.
     */
    CALL_NOT_PERMITTED("Chamada não permitida"),

    /**
     * Erro indicando que um recurso solicitado não foi encontrado.
     */
    NOT_FOUND("Recurso não encontrado"),

    /**
     * Erro indicando que a entidade não pôde ser processada.
     */
    UNPROCESSABLE_ENTITY("Entidade não processável"),

    /**
     * Erro ao acessar o recurso solicitado.
     */
    BAD_REQUEST("Não é possível acessar o recurso solicitado"),

    /**
     * Erro indicando falha na comunicação com serviços externos.
     *
     * <p>Este erro é utilizado para capturar falhas que ocorrem quando um serviço externo
     * retorna um código de status HTTP na faixa 4xx, indicando um problema do lado do cliente
     * ao fazer a requisição. Essas falhas podem incluir erros de autenticação, autorização,
     * requisições malformadas, ou outros problemas que impedem a conclusão bem-sucedida
     * da requisição.</p>
     */
    EXTERNAL_SERVICE_4XX("Erro na comunicação com serviço externo"),

    /**
     * Erro indicando falha na comunicação com serviços externos.
     *
     * <p>Este erro é utilizado para capturar falhas que ocorrem quando um serviço externo
     * retorna um código de status HTTP na faixa 5xx, indicando um problema do lado do servidor
     * do serviço externo. Essas falhas podem incluir indisponibilidade temporária,
     * erros internos do servidor, ou outros problemas que impedem a conclusão bem-sucedida
     * da requisição.</p>
     */
    EXTERNAL_SERVICE_5XX("Erro na comunicação com serviço externo"),

    /**
     * Erro indicando falha no sistema de cache.
     */
    CACHE_SERVICE("Erro no serviço de cache"),

    /**
     * Erro genérico indicando falha não específica no acesso ao banco de dados.
     *
     * <p>Este erro é utilizado como fallback para situações onde não é possível
     * categorizar especificamente o tipo de falha de infraestrutura, ou quando
     * múltiplos problemas ocorrem simultaneamente.</p>
     */
    INFRA_GENERIC("Erro genérico de infraestrutura");

    private final String code;
    private final String message;

    InfraErrorCode(final String message) {
        this.code = this.name();
        this.message = message;
    }

    @Override
    public String getCode() { return code; }

    @Override
    public String getMessage() { return message; }
}

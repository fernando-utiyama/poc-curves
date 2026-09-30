# Guia de implementação: curves-cadastro-curvas

Guia passo a passo para aplicar esta change com o mínimo de decisões. **A spec manda; este guia diz onde e como.** Siga as seções na ordem. Os testes são escritos só na seção 14, depois de tudo compilar.

## 0. Regras para quem implementa

- **Serviço:** `services/curves` (no sistema real, `acts-srv-curvas`). **Já está transcrito**: não se cria serviço nem se muda a estrutura, acrescenta-se. Pacote `br.com.poc`. O que existe e é reaproveitado:
  - `adapter/in/api/rest/{controller,dto,handler,mapper,openapi}` (controllers, DTOs REST separados dos DTOs da aplicação, `GlobalExceptionHandler`), `adapter/out/persistence/{entity,repository,mapper}` e os adaptadores `*PersistenceAdapter`/`*RepositoryAdapter`;
  - `application/{port/in/usecase,port/out,service,dto,mapper,model,exception}`: portas de saída `<Tabela>RepositoryPort`, casos de uso `<Nome>UseCase` com implementação em `application/service`, exceções `BaseException`/`ErrorCode`;
  - `infrastructure/config/BeanConfig` (os serviços são registrados como `@Bean` ali), `shared/api/util/NormalizadorUtil` e o `ApiErrorResponse` (já existe na develop real, em `application/dto`, com `LocalDateTime timestamp`; a cópia transcrita o tem em `shared/api`: o local e o tipo de `timestamp` existentes **não mudam**, conferir o pacote ao encaixar);
  - entidades já existentes e conferidas contra o `001_SCRIPT_INICIAL.sql`: `CurvaMercdEntity` (todas as colunas), `ProvedorEntity` (CRUD de provedores do outro dev, não tocar) e `BloombergCurvaPrimrEntity`. Não criar outra entidade para a mesma tabela.
- **Classes novas** ficam nos mesmos pacotes e têm o nome da tabela: `CurvaPrvdrEntity`, `ConfgCurvaEntity`, `DadoVertcCurvaEntity`, `DadoCurvaEntity`, `BtrsCurvaPrimrEntity`; portas `CurvaPrvdrRepositoryPort`, `ConfgCurvaRepositoryPort`, `DadoVertcCurvaRepositoryPort`, `DadoCurvaRepositoryPort`, `BtrsCurvaPrimrRepositoryPort`; a `CurvaMercdRepositoryPort` existente ganha os métodos novos. Nomes de infraestrutura (cliente do engine, filtro, planilha) podem ser em inglês.
- **Dois modelos, sem misturar:** `application/model` (`CurvaMercd`, `Provedor`, `BloombergCurvaPrimr`) é do código existente (beans mutáveis com Lombok) e não é reescrito. O código novo põe as regras em **`br.com.poc.domain`**, Java puro, com `record`s; as portas novas recebem e devolvem esses `record`s. As conversões entidade ↔ `record` ficam no adaptador de persistência.
- **Mesma base do engine** (guia do `engine-modelos-curva`, seção 0):
  - **hexagonal:** `domain` em Java puro (regras de campo, vigência, `ETag`, validador de parâmetros, `hashPontos`, 30/360, situação do painel), sem Spring, JPA, Jackson ou POI; `application/port/in/usecase` (um caso de uso por área) e `application/port/out`, uma porta por tabela mais `EnginePort`, `PlanilhaPort` e `EventosPort`; `application/service` implementa os casos de uso; os adaptadores implementam as portas. O serviço conhece só as portas. Dependências: `adapter → application → domain`, nunca o contrário (conferido na seção 14.1);
  - **Java 21 nativo:** `record` para todo dado novo (domínio, comandos, resultados, eventos, linhas de planilha), com `List.copyOf` no construtor compacto para imutabilidade; `sealed interface` e `switch` com pattern matching onde a variação é fechada (resultado de linha da importação, situação do painel, resposta do engine), **sem `default`**, para o compilador apontar o caso esquecido; `java.time`, `RoundingMode`, `HexFormat`, `MessageDigest`, `Normalizer`, `Currency`, `Locale`; `Stream` e `Collectors.groupingBy`/`teeing`; `Optional` só como retorno de busca (nunca parâmetro nem campo); `var` onde o tipo é óbvio; text blocks para SQL; `String.formatted`. Nada de tipo próprio de data, relógio, arredondamento ou "utils" (o `NormalizadorUtil` existente é reaproveitado para o nome normalizado, se servir);
  - **virtual threads:** `spring.threads.virtual.enabled: true` (mudança deliberada do `application.yml`, que hoje traz `false`; seção 1.2); sem `synchronized`. As chamadas independentes ao engine e as regravações interpoladas depois da importação rodam em `Executors.newVirtualThreadPerTaskExecutor()`, com `try-with-resources` (o `StructuredTaskScope` é preview no Java 21 e não se usa);
  - **Lombok e MapStruct:** o código existente usa os dois (entidades, modelos, mapeadores) e continua usando. O código novo usa Lombok **só nas entidades JPA** (`@Getter`, `@Setter`, `@NoArgsConstructor`, como a `ProvedorEntity`) e **nenhum MapStruct novo**: a conversão é um método `de(...)`/`para(...)` no próprio `record` ou adaptador. Injeção por construtor (`final`), nunca `@Autowired` em campo;
  - **transação:** `@Transactional` nos métodos de `application/service` que gravam (é o que o código existente faz); o domínio não conhece transação.
- Não invente rota, código de erro, aviso, coluna ou tabela fora deste guia e das specs. Não crie tabela, sequência, índice nem tópico. O serviço não usa o Blob.
- Número de curva é sempre `BigDecimal`, nunca `double` (a única exceção é a célula numérica da planilha, seção 9).
- Fuso: a JVM inteira roda em `America/Sao_Paulo` (seção 1.4); `LocalDate.now()` e `OffsetDateTime.now()` são usados direto. Leitura em `READ COMMITTED`, nunca `NOLOCK`.
- Mensagens em pt-BR com acentuação, UTF-8. Códigos (enums, `codigoErro`, avisos) não se traduzem.
- Arquivos de configuração que já existem: não reescrever; conferir e acrescentar só o que faltar.
- O serviço roda em no mínimo 2 instâncias: nenhum estado de negócio em memória local, além do cache de valores aceitos (seção 5, item 3).

### 0.1 Tabelas que o serviço usa

| Tabela | O que o serviço faz |
|---|---|
| `tCurvaMercd` | cria e altera a curva; nunca escreve `dBaseReft`, `cUsuarCalc` nem as colunas sem uso da spec |
| `tCurvaPrvdr` | CRUD das ligações; `cldtfdUnic` por `MAX + 1` com trava |
| `tConfgCurva` | cria e exclui versões de configuração (`cldtfdConfg` é identity) |
| `tPrvdrDadoMercd` | só lê (provedores, do CRUD do outro dev) |
| `tDadoVertcCurva` | **pontos da curva** (curva construída): edição manual |
| `tDadoCurva` | curva interpolada: só **apaga** a da data quando os pontos da data são apagados; quem grava é o engine |
| `tBtrsCurvaPrimr` | **dado bruto da B3**: listagem geral e CRUD por linha (seção 11); `cldtfdUnic` por `MAX + 1` com trava, como o processor |

### 0.2 Vetores de teste reais

| Item | Valor esperado |
|---|---|
| `ETag` do cadastro da `PRE` do `exemplo-cadastro-7-curvas.txt` (curva criada, ligação `idLigacao` 1, versão 1 com os parâmetros do exemplo) | `63ebcddb87ec554ffb1890b6361789425b6cd8bc9c5fbda181ef518c7c21009d` |
| JSON canônico desse `ETag` | ver seção 2.3 |
| `hashPontos` do vetor comum com o engine (`2026-09-15;13.9\n2026-09-16;-117.96`) | `8dcff432fa5271ff16bdaef72940792811802e0a5ae59e8c2bf5cead818d5544` |
| `hashPontos` da `PRE` de `2026-09-14` construída pelo engine (278 pontos) | `7c4982b34ca35f784863118902e63f28e7d49362d940cc27eb77961d526fca20` |
| ponto da `PRE` em `2027-01-04` (data-base `2026-09-14`) | valor 13.589, 75 dias úteis publicados, 112 dias corridos, 110 dias 30/360 |
| `PUT` com esse ponto e `"diasUteis": 76` | aviso `DIAS_UTEIS_DIFERENTES_DO_CALENDARIO` (calendário: 75); linha com `cDiaUtil` 76, `cQtdDiaPer` 112, `cQtdDiaReft` 110, fatores nulos |
| `13.123456789` na `PRE` (7 casas, `HALF_UP`) | gravado `13.1234568`, aviso `VALOR_ARREDONDADO` |

---

## 1. Base

### 1.1 `pom.xml`: o mínimo de coisas novas

O `pom.xml` do `services/curves` já tem `data-jpa`, `mssql-jdbc`, `validation`, `springdoc`, `actuator`, `logstash-logback-encoder`, `openfeign` e `spring-boot-starter-test`. Acrescentar **só**:

| Dependência | Por quê |
|---|---|
| `org.springframework.boot:spring-boot-starter-oauth2-resource-server` (versão do Spring Boot) | JWT do Entra ID |
| `com.azure:azure-identity`, pelo `com.azure:azure-sdk-bom` **1.3.8** em `dependencyManagement` | token de serviço (Managed Identity) para chamar o engine |
| `org.apache.poi:poi-ooxml` **5.5.1** | planilhas |

Nada mais: o cliente do engine usa o `java.net.http.HttpClient` do JDK (o `openfeign` do pom fica para as integrações que já o usam), sem ArchUnit e sem dependência de teste além do `spring-boot-starter-test` (seção 14).

### 1.2 `application.yml` (conferir e acrescentar)

```yaml
curves:
  engine:
    url: ${CURVES_ENGINE_URL}
    escopo: ${CURVES_ENGINE_ESCOPO}              # api://{app do engine}/.default
    timeout-segundos: 10
    timeout-situacao-segundos: 60
    timeout-interpolada-segundos: 60
    cache-valores-minutos: 5
  painel:
    horario-esperado:                            # sem padrão: provedor sem horário → atrasada=false hoje
      B3: ${CURVES_HORARIO_B3:}
      ANBIMA: ${CURVES_HORARIO_ANBIMA:}
      BLOOMBERG: ${CURVES_HORARIO_BLOOMBERG:}
  timeout:
    requisicao-segundos: 60
    importacao-pontos-segundos: 120
spring:
  threads:
    virtual:
      enabled: true                              # troca o false atual: requisições em virtual threads
  datasource:
    hikari:
      transaction-isolation: TRANSACTION_READ_COMMITTED
  jpa:
    open-in-view: false
    properties:
      hibernate:
        jdbc:
          batch_size: 500
        order_inserts: true
        order_updates: true
  security:
    oauth2:
      resourceserver:
        jwt:
          issuer-uri: ${CURVES_JWT_EMISSOR}
          audiences: ${CURVES_JWT_AUDIENCIA}     # o mesmo registro de aplicação do engine
```

Conferir no `application.yml` que já existe, ajustando só o que conflita (o resto fica):

| Chave existente | Ajuste |
|---|---|
| `spring.threads.virtual.enabled: false` | `true` (acima); trocar o comentário de cautela por "JDBC do mssql-jdbc 12+ não fixa a thread; sem `synchronized` no código novo" |
| `spring.jackson.default-property-inclusion: NON_NULL` | manter (é do CRUD de provedores); todo `record` de resposta **novo** leva `@JsonInclude(JsonInclude.Include.ALWAYS)` na classe, porque as specs exigem nulo presente, nunca omitido (painel, pontos, curva) e `avisos` sempre presente |
| `spring.jackson.time-zone: UTC` e `date-format` | os instantes da resposta saem `-03:00` (seção 1.4): o `record` leva `OffsetDateTime` já no fuso de Brasília, e o `time-zone` não se aplica a `OffsetDateTime` |
| `spring.jpa.show-sql: true` | manter em `default`; nos demais perfis `false` (a escrita de pontos tem centenas de `INSERT`) |
| `spring.jpa.properties.hibernate.dialect` | manter |
| `spring.mvc.problemdetails.enabled: true` | `false`: um só formato de erro, o `ApiErrorResponse`; os erros do próprio Spring MVC (405, 415, 406) passam pelo `GlobalExceptionHandler` |

### 1.3 Erros, avisos e enums: `domain/`

Erros **no padrão que o `services/curves` já tem** (`application/exception` + `GlobalExceptionHandler` + `ApiErrorResponse`):

```java
// application/exception/CadastroErrorCode.java: o mesmo molde de BusinessErrorCode/InfraErrorCode (código = name(), mensagem em pt-BR no construtor)
public enum CadastroErrorCode implements ErrorCode {
  PARAMETRO_INVALIDO("Parâmetro inválido"), NAO_AUTENTICADO("Token ausente ou inválido"), SEM_PERMISSAO("Sem permissão"),
  NAO_ENCONTRADO("Recurso não encontrado"), CODIGO_EM_USO("Código em uso"), NOME_EM_USO("Nome em uso"),
  LIGACAO_DUPLICADA("Ligação duplicada"), PRIORIDADE_EM_USO("Prioridade em uso"), ALTERADO_POR_OUTRO("Alterado por outra pessoa"),
  DADOS_INVALIDOS("Dados inválidos"), PONTOS_INVALIDOS("Pontos inválidos"), IF_MATCH_AUSENTE("Cabeçalho If-Match ausente"), ERRO_INTERNO("Erro interno");
  // campos code/message, getCode()/getMessage() como os enums existentes
}
// domain/aviso: dados puros, sem Spring
public enum CodigoAvisoCurva { /* os 19 códigos da tabela "Avisos do serviço" da spec cadastro-curva-mercado, na ordem da tabela */ }
public record Detalhe(String campo, Integer linha, String valor, String motivo) {}     // usado em erros (detalhes) e em avisos
public record AvisoCurva(CodigoAvisoCurva codigo, String mensagem, List<Detalhe> detalhes) {
  public AvisoCurva { detalhes = List.copyOf(detalhes); }
}
```

Exceções, reaproveitando as existentes (o status vem do `GlobalExceptionHandler`, não da classe): `InvalidInputException` (`PARAMETRO_INVALIDO`, 400), `NotFoundException` (`NAO_ENCONTRADO`, 404), `BusinessException` (`DADOS_INVALIDOS`, `PONTOS_INVALIDOS`, 422), `InfrastructureException` (`ERRO_INTERNO`, 500). **Novas**, no mesmo molde (`extends BaseException`, construtores por `ErrorCode`): `ConflictException` (409: `CODIGO_EM_USO`, `NOME_EM_USO`, `LIGACAO_DUPLICADA`, `PRIORIDADE_EM_USO`), `PreconditionFailedException` (412: `ALTERADO_POR_OUTRO`) e `PreconditionRequiredException` (428: `IF_MATCH_AUSENTE`). A `BaseException` carrega o código e os `additionalDetails`; os `Detalhe` de 400/422 vão ali.

O `GlobalExceptionHandler` (acrescentar, sem reescrever o que existe): um método por exceção nova e um para `InvalidInputException`, `InfrastructureException`, `HttpMessageNotReadableException` (JSON malformado → 400 `PARAMETRO_INVALIDO`) e `MissingServletRequestParameterException`/`MethodArgumentTypeMismatchException` (400). O corpo é o `ApiErrorResponse` existente, que ganha **dois componentes no fim** (o resto do record, inclusive o tipo de `timestamp`, fica como está) e um construtor de 6 argumentos para o que já o usa:

```java
public record ApiErrorResponse(LocalDateTime timestamp, int status, String error, String message, String path,
                               List<String> details, String correlationId, List<Detalhe> detalhes) {
  public ApiErrorResponse(LocalDateTime timestamp, int status, String error, String message, String path, List<String> details) {
    this(timestamp, status, error, message, path, details, MDC.get("correlationId"), List.of());
  }
}
```

`error` = o código (`DADOS_INVALIDOS`); `message` em pt-BR; `details` = um texto `campo: motivo` por detalhe (o que já serve ao cliente simples); `detalhes` = a estrutura completa. `Detalhe` mora em `br.com.poc.domain` (é um `record` sem dependência) e o `ApiErrorResponse` o importa; o sentido contrário (domínio importando `shared`) é proibido. 401 e 403 saem do Spring Security (`AuthenticationEntryPoint` e `AccessDeniedHandler` escrevendo o mesmo `ApiErrorResponse` com `NAO_AUTENTICADO`/`SEM_PERMISSAO`). Sem stack trace, sem `@RestControllerAdvice` novo e sem `ExcecaoNegocio` no código novo (a existente é do CRUD de provedores e continua).

Enums da tabela "Enums do serviço" da spec `cadastro-curva-mercado` (`Unidade`, `DayCounterCotacao`, `CompoundingCotacao`, `SituacaoCurva`, `SituacaoPainel`, `MotivoPainel`, `TipoAlteracao`, `OperacaoAlteracao`, `OperacaoPontos`, `OrigemPontos`, `ModoImportacao`, `ResultadoLinha`): enums simples em `domain`, com rótulo e descrição em `messages.properties` (`poc.valores.<Enum>.<CONSTANTE>.rotulo` e `.descricao`), lidos pelo `MessageSource` no adaptador da API (o `application.yml` já tem `spring.messages`, hoje sem `basename`: definir `basename: messages`). O catálogo de `GET /curvas-mercado/valores` lista cada enum de forma explícita (`itens(Unidade.class)`). Teste obrigatório: toda constante tem as suas chaves. Os enums de resultado fechado (`ResultadoLinha`, `SituacaoPainel`) são lidos com `switch` por pattern, sem `default`.

Filtro de correlação (`OncePerRequestFilter`): `X-Correlation-Id` recebido ou `UUID.randomUUID()`, no MDC (`correlationId`) e em toda resposta (inclusive `xlsx` e erro); limpar o MDC no `finally`.

Serialização: `BigDecimal` como string plana, datas `AAAA-MM-DD`, instantes `yyyy-MM-dd'T'HH:mm:ss.SSSXXX`, `avisos` sempre presente. O Spring Boot 4 do serviço usa **Jackson 3** (`tools.jackson.core:jackson-databind` 3.1.6, conferido com `mvn dependency:tree`); o `jackson-databind` 2.22.2 do pom é só transitivo (CVE) e não serve o MVC. Usar `tools.jackson` (`JsonMapper`, `withConfigOverride(BigDecimal.class, o -> o.setFormat(JsonFormat.Value.forShape(STRING)))`, `StreamWriteFeature.WRITE_BIGDECIMAL_AS_PLAIN`) e as anotações `com.fasterxml.jackson.annotation` (`@JsonInclude`, `@JsonFormat`), que o Jackson 3 continua usando. Nunca importar `com.fasterxml.jackson.databind`.

Enum de entrada com caixa diferente (`taxa`) → 422 `DADOS_INVALIDOS` com o campo e os valores aceitos: ler enums como `String` no `record` de entrada e converter com `Enum.valueOf` em `try/catch`, nunca com a desserialização tolerante do Jackson (`READ_UNKNOWN_ENUM_VALUES_AS_NULL` e semelhantes ficam desligados).

### 1.4 Fuso da JVM

Igual ao engine (seção 1.4 do guia do engine): o `main` (`Application.java`) faz `TimeZone.setDefault(TimeZone.getTimeZone("America/Sao_Paulo"))` antes do Spring, e um `ApplicationRunner` impede a subida se o fuso for outro. Sem classe de relógio própria. `dCriacReg` e `dUltAtulz` (`datetime`) recebem `LocalDateTime.now()` (hora de Brasília).

### 1.5 Segurança

```java
.requestMatchers("/actuator/health/**").permitAll()
.requestMatchers(HttpMethod.GET, "/api/v1/**").hasAnyAuthority("Curvas.Leitura", "Curvas.Cadastro", "Curvas.Operador")
.requestMatchers("/api/v1/curvas-mercado/*/pontos/**", "/api/v1/curvas-mercado/pontos/importacao").hasAuthority("Curvas.Operador")
.requestMatchers(HttpMethod.POST, "/api/v1/curvas-mercado/*/configuracoes/validacao").hasAnyAuthority("Curvas.Leitura", "Curvas.Cadastro")
.requestMatchers("/api/v1/**").hasAuthority("Curvas.Cadastro")
// papéis pelo claim "roles", sem prefixo (JwtGrantedAuthoritiesConverter com authoritiesClaimName "roles" e prefixo "")
```

A ordem importa: as regras de `GET` e de pontos vêm antes da regra geral de escrita. Usuário: `preferred_username`, senão `appid`.

### 1.6 Cliente do engine: porta `EnginePort` e adaptador `adapter/out/client/engine/EngineHttpClient.java`

`EnginePort` (em `application/port/out`) devolve um `sealed interface RespostaEngine<T>` com `Disponivel<T>(T valor)` e `Indisponivel<T>(String motivo)` (records). O adaptador usa o **`java.net.http.HttpClient`** do JDK (uma instância, `Version.HTTP_1_1`, `connectTimeout` 5 s), com `HttpRequest.timeout(...)` por chamada, `X-Correlation-Id` do MDC e `Authorization: Bearer` do token de serviço: `new DefaultAzureCredentialBuilder().build().getTokenSync(new TokenRequestContext().addScopes(escopo))`, guardado em um `volatile record Token(String valor, Instant expira)` e renovado 5 minutos antes de expirar (renovação em `ReentrantLock`, sem `synchronized`). O JSON do engine é lido com o `ObjectMapper` do Spring para `record`s do adaptador. Chamadas e tempos:

| Método | Rota do engine | Tempo |
|---|---|---|
| `valoresCadastro()` | `GET /api/v1/valores-cadastro` | 10 s |
| `situacao(dataBase)` | `GET /api/v1/curvas/situacao?dataBase=` | 60 s |
| `feriados(nome, mercado, anoIni, anoFim)` | `GET /api/v1/calendarios/{nome}?mercado=&anoInicial=&anoFinal=&formato=json` | 10 s |
| `regravarInterpolada(codigo, dataBase)` | `POST /api/v1/curvas/{codigo}/{dataBase}/interpolada` | 60 s |

Toda falha (rede, tempo, 4xx, 5xx, JSON ilegível) vira `Indisponivel` com o motivo, nunca uma exceção que derrube a operação do curves. Quem consome usa `switch` por pattern sobre `RespostaEngine`. No painel, `situacao` e `feriados` são independentes e rodam juntas em virtual threads.

### 1.7 Log

JSON com `correlationId`. `REQUISICAO_CONCLUIDA` (usuário, rota com molde, status, `codigoErro`, duração) em toda requisição. `CADASTRO_ALTERADO` (seção 2.5), `PONTOS_EDITADOS` (seção 10.5) e `CURVA_PRIMARIA_EDITADA` (seção 11) depois do commit (`TransactionSynchronizationManager.registerSynchronization` com `afterCommit`), emitidos pelo adaptador de `EventosPort` com a API fluente do SLF4J (`log.atWarn().addKeyValue("codigo", ...).log("CADASTRO_ALTERADO")`), que o `LogstashEncoder` do `logback-spring.xml` já serializa em JSON nos perfis de container. Nunca token nem corpo inteiro.

---

## 2. Curva de mercado

### 2.1 Entidade `CurvaMercdEntity` (já existe)

A `CurvaMercdEntity` do serviço mapeia todas as colunas de `tCurvaMercd` e está conferida contra o `001_SCRIPT_INICIAL.sql` (`vFatorMultiAtivo` em `BigDecimal` 28,12). Não criar outra. Acrescentar apenas, nela:
- `dBaseReft` e `cUsuarCalc` com `@Column(insertable = false, updatable = false)`: são do engine, o curves nunca os escreve;
- os getters das colunas `CHAR` (`cNormaDia`, `cPaisInstt`, `cSitReg`, `cTpoJuro`, `cTpoCotac`, `cTpoVlr`) devolvendo `stripTrailing()`, escritos à mão (o Lombok não gera um getter que já existe). Gravar sem espaços.

O adaptador `CurvaMercdPersistenceAdapter` (hoje só `existsByTicker`) implementa os métodos novos de `CurvaMercdRepositoryPort` e converte entidade ↔ `record` do domínio `CurvaMercado`; o `CurvaMercdMapper` (MapStruct) e o modelo `CurvaMercd` continuam a serviço do código existente. Consulta com `WITH (UPDLOCK, ROWLOCK)` é `@Query(nativeQuery = true)` no `CurvaMercdRepository`.

### 2.2 Regras (`application/service/CurvaMercadoService.java`, caso de uso `CurvaMercadoUseCase`)

Tabela "Campos da curva de mercado" da spec, campo a campo:
- `codigo`: `^[A-Z0-9_]{1,50}$`; único (`SELECT COUNT(*) FROM tCurvaMercd WHERE cTickerIdtfdUnic = ? AND cTickerIndcd <> ?`) → 409 `CODIGO_EM_USO`.
- `nome`: 1–50; único normalizado (`Normalizer.normalize(s, NFD).replaceAll("\\p{M}", "").toLowerCase(Locale.ROOT).strip()`, comparando em Java contra todos os nomes) → 409 `NOME_EM_USO`; no `PUT`, nome diferente do atual → 422 `DADOS_INVALIDOS` ("o nome é imutável").
- `unidade` obrigatória; `dayCounterCotacao` e `compounding` obrigatórios só com `TAXA` e nulos nos demais (preenchido com `PRECO`/`PONTOS` → 422 no campo).
- `moeda` ISO 4217 (`Currency.getInstance`), `pais` ISO 3166-1 alfa-2 (`Locale.getISOCountries()`).
- `fimVigencia` ≥ `inicioVigencia`.
- Todos os problemas de campo juntos numa única 422 `DADOS_INVALIDOS`, um `Detalhe` por campo.
- Criação: `cSitReg = ATIVO`, `dCriacReg` e `dUltAtulz` = agora, `cUsuarAtulz` = usuário. Inativação e reativação só trocam `cSitReg` (com `ETag` e log).
- Linhas com `cTickerIdtfdUnic` nulo nunca aparecem (`WHERE cTickerIdtfdUnic IS NOT NULL` em toda consulta).
- Coerência com a configuração (spec `configuracao-calculo-curva`): mudança de `unidade`, `dayCounterCotacao` ou `compounding` que invalide a versão vigente ou futura → 422 citando a versão.
- Inativar uma curva componente de derivada ativa → aviso `CURVA_COM_FILHAS` (filhas: ligações `TCEN` com `cTickerPrvdr` = nome desta curva).

Listagem: filtros `nome` (trecho normalizado), `codigo` (exato), `unidade`, `situacao`; paginação `pagina` (≥ 0) e `tamanho` (padrão 50, máximo 500); ordem por código.

### 2.3 `ETag` (`domain/cadastro/EtagCurvaMercado.java`, Java puro; o JSON canônico é montado pelo adaptador com o Jackson e passado como texto)

SHA-256 hexa minúsculo do JSON canônico: chaves em ordem alfabética em todos os níveis, sem espaços, nulos presentes como `null`, strings UTF-8 sem escape de não ASCII. Estrutura:

```json
{"configuracoes":[{"fimVigencia":null,"inicioVigencia":"2026-01-01","interpolador":"LogLinear","modeloConstrucao":"PRONTA_TS_B3","parametros":{"BASE_INTERPOLACAO":"Discount","BUSINESS_DAY_CONVENTION":"Following","CALENDARIO":"Brazil","CASAS_DECIMAIS":7,"DAY_COUNTER_TEMPO":"Business252","EXTRAPOLACAO_FIM":"FlatForward","EXTRAPOLACAO_INICIO":"Disabled","FREQUENCY":"Annual","HORIZONTE":"10Y","MERCADO_CALENDARIO":"Settlement","MODO_ARREDONDAMENTO":"HALF_UP"},"versao":1}],"curva":{"classeAtivo":null,"classificacao":null,"codigo":"PRE","compounding":"Compounded","dayCounterCotacao":"Business252","fimVigencia":null,"inicioVigencia":"2026-01-01","moeda":"BRL","nome":"DIxPRE","pais":"BR","situacao":"ATIVO","unidade":"TAXA"},"ligacoes":[{"codigoNaFonte":"PRE","idLigacao":1,"prioridade":1,"produto":"TS","provedor":"B3"}]}
```

Esse texto dá `63ebcddb87ec554ffb1890b6361789425b6cd8bc9c5fbda181ef518c7c21009d`. Ligações ordenadas por `idLigacao`, configurações por `versao`. Fora do cálculo: `dBaseReft`, `cUsuarCalc`, `cUsuarAtulz`, `dCriacReg`, `dUltAtulz`. Com Jackson: `JsonMapper.builder().enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS).enable(MapperFeature.SORT_PROPERTIES_ALPHABETICALLY)` e montar o documento com `Map`/`record` só com esses campos.

Toda alteração (curva, inativação, reativação, ligação, configuração): sem `If-Match` → 428 `IF_MATCH_AUSENTE`; `If-Match` diferente do `ETag` calculado dentro da transação, depois de travar a curva (`SELECT ... WITH (UPDLOCK, ROWLOCK)`) → 412 `ALTERADO_POR_OUTRO`, nada gravado. Resposta de sucesso traz o `ETag` novo.

### 2.4 Rotas (`adapter/in/api/rest/CurvaMercadoController`)

As da tabela "Rotas da curva de mercado" da spec. `GET /curvas-mercado/{codigo}` devolve a curva, as ligações por prioridade e a configuração vigente hoje, com `ETag`.

### 2.5 Auditoria

`CADASTRO_ALTERADO` (nível `AVISO`), depois do commit: `idAuditoria` (UUID), `codigo`, `nome`, `tipo`, `operacao`, `usuario`, `instante`, `correlationId`, `idLote` (planilha), `estadoAnterior` e `estadoNovo` completos (o mesmo documento do `ETag`, mais os campos de controle). Commit que falha não gera o evento.

`GET /curvas-mercado/{codigo}/auditoria?formato=json|xlsx`: montado na hora, com todos os campos da curva (inclusive controle e os do engine), todas as ligações, todas as versões com parâmetros e o `ETag`. Arquivo `{codigo}_CADASTRO_AUDITORIA_{AAAAMMDDHHmmss}.xlsx`, abas `Curva`, `Ligacoes`, `Configuracoes`.

---

## 3. Ligações (`CurvaProvedorService`)

Entidade nova `CurvaPrvdrEntity` (`tCurvaPrvdr`, `@Id cldtfdUnic`) e porta `CurvaPrvdrRepositoryPort`: `cldtfdUnic`, `cTickerIndcd`, `iPrvdrDados`, `cPrvdrMercd`, `cTickerPrvdr`, `cPriorCsumo`. Provedor: usar a `ProvedorEntity` que já existe no serviço (CRUD de provedores), só para leitura: `@Table(name = "tPrvdrDadoMercd")`, `@Id` `iPrvdrDados` → `nomeProvedor` (o identificador: `B3`, `ANBIMA`, `BLOOMBERG`, `TCEN`), `cInfoProdt` → `descricao`, `cProdt` → `produto`, `iCoplt` → `nomeCompletoAtivoOuInstrumento`. Não criar outra entidade para a mesma tabela. `tCurvaPrvdr.iPrvdrDados` tem FK para `tPrvdrDadoMercd.iPrvdrDados`: conferir a existência antes (404 `NAO_ENCONTRADO`) para responder com o erro certo em vez da violação de FK. O produto da ligação (`cPrvdrMercd`) não é conferido contra `cProdt`: `tPrvdrDadoMercd` tem uma linha por provedor (PK em `iPrvdrDados`), e uma fonte pode ter vários produtos (a ANBIMA tem `MS` e, no futuro, `CZ`).

`idLigacao` (na mesma transação da inserção):
```sql
SELECT ISNULL(MAX(cldtfdUnic), 0) + 1 FROM tCurvaPrvdr WITH (UPDLOCK, HOLDLOCK);
```

Regras: provedor inexistente → 404 `NAO_ENCONTRADO`; (curva, provedor, produto) repetido → 409 `LIGACAO_DUPLICADA`; prioridade repetida na curva → 409 `PRIORIDADE_EM_USO`; trocar provedor não existe (excluir e incluir). `TCEN`: `codigoNaFonte` = nome de curva existente, diferente da própria, sem ciclo (DFS pelas ligações `TCEN` a partir da curva componente; achando a curva atual → 422 com o caminho `B → A → B`).

Avisos depois da alteração: `CURVA_SEM_ORIGEM` (nenhuma ligação); `ORIGEM_INCOMPATIVEL_COM_MODELO` (a de menor prioridade não bate com o modelo nativo da configuração vigente: `PRONTA_TS_B3` = `B3`/`TS`, `NTNB_BOOTSTRAP_ANBIMA` = `ANBIMA`/`MS`, `SOFR_ZERO_BLOOMBERG` = `BLOOMBERG`/`BLC2`); `MODELO_POR_ORIGEM_SEM_LIGACAO` (chave de `MODELOS_POR_ORIGEM` da vigente ou futura sem ligação).

`GET /ligacoes?provedor=&produto=&codigoNaFonte=`: curvas ligadas, com código, nome e prioridade.

---

## 4. Configuração de cálculo (`ConfiguracaoCurvaService`)

Entidade `ConfgCurvaEntity` (`tConfgCurva`, `@Id @GeneratedValue(IDENTITY) cldtfdConfg`): `cTickerIndcd`, `cMotorCalc`, `cRotnaCalc`, `cModDado`, `cVrsaoReg`, `dInicVgcia`, `dValidAte`; as demais colunas nulas.

### 4.1 Validador de parâmetros (`ValidadorParametros`, cópia da regra do engine)

Uma tabela única em código (a mesma usada na cópia embutida de valores, seção 5, item 3):

| Chave | Tipo | Obrigatória | Valores |
|---|---|---|---|
| `BASE_INTERPOLACAO` | texto | sim | `Discount`, `CompoundFactor`, `ZeroYield`, `Price` |
| `DAY_COUNTER_TEMPO` | texto | sim | `Business252`, `Actual360`, `Actual365Fixed`, `Thirty360` |
| `FREQUENCY` | texto | só com `compounding` = `Compounded` (e proibida nos demais) | `Annual`, `Semiannual`, `EveryFourthMonth`, `Quarterly`, `Bimonthly`, `Monthly`, `EveryFourthWeek`, `Biweekly`, `Weekly`, `Daily` |
| `CALENDARIO` | texto | sim | qualquer nome (aviso `MODELO_NAO_NATIVO` fora de `Brazil`/`UnitedStates`) |
| `MERCADO_CALENDARIO` | texto | sim | `Settlement` com `Brazil`, `FederalReserve` com `UnitedStates`; calendário Groovy: qualquer |
| `BUSINESS_DAY_CONVENTION` | texto | sim | `Following`, `ModifiedFollowing`, `Preceding`, `ModifiedPreceding`, `Unadjusted`, `HalfMonthModifiedFollowing`, `Nearest` |
| `EXTRAPOLACAO_INICIO`, `EXTRAPOLACAO_FIM` | texto | não (padrão `Disabled`) | `Disabled`, `FlatForward`, `FlatValue` |
| `HORIZONTE` | texto | sim | `^[1-9][0-9]*[DWMY]$` |
| `CASAS_DECIMAIS` | inteiro | sim | 0 a 12 |
| `MODO_ARREDONDAMENTO` | texto | sim | `HALF_UP`, `HALF_EVEN`, `DOWN` |
| `VERSAO_SCRIPT_CONSTRUCAO`, `VERSAO_SCRIPT_INTERPOLACAO`, `VERSAO_SCRIPT_CALENDARIO` | inteiro | não | ≥ 1 |
| `MODELOS_POR_ORIGEM` | objeto | não | chave `^[^/]+/[^/]+$`, valor texto 1–100 |

Combinações: `Price` só com `PRECO`/`PONTOS`, e as outras bases de interpolação só com `TAXA`; `FlatForward` só com interpolador `Linear` ou `LogLinear`. Chave desconhecida, tipo errado, valor fora da lista (com caixa) ou obrigatório ausente → 422 `DADOS_INVALIDOS`, um `Detalhe` por problema. Avisos: `MODELO_NAO_NATIVO` (modelo, interpolador ou calendário fora dos nativos), `ORIGEM_INCOMPATIVEL_COM_MODELO`, `MODELO_POR_ORIGEM_SEM_LIGACAO`.

Gravação de `cModDado`: JSON compacto, chaves na **ordem da tabela acima** (não alfabética), `EXTRAPOLACAO_*` gravadas mesmo quando `Disabled`; mais de 1.024 caracteres → 422.

### 4.2 Versões e vigência

Na mesma transação, com a curva travada e o `If-Match` conferido:
- primeira versão: `cVrsaoReg = 1`; `inicioVigencia` ≥ `dInicVgcia` da curva (pode ser no passado);
- versão nova: `inicioVigencia` > o da última **e** ≥ `LocalDate.now()`, senão 422; fecha a última (`dValidAte = inicio da nova − 1 dia`); `cVrsaoReg = última + 1`; nova com `dValidAte` nulo;
- exclusão: só a última e só se `inicioVigencia` > hoje; a anterior volta a `dValidAte` nulo; senão 422;
- não há alteração de versão.

Rotas: as da tabela "Rotas da configuração" da spec. `validacao` roda as regras sem gravar e devolve erros e avisos. `vigente?data=` (padrão hoje): `dInicVgcia <= data AND (dValidAte IS NULL OR dValidAte >= data)`.

---

## 5. Valores aceitos (`GET /api/v1/curvas-mercado/valores`)

1. Engine: `valoresCadastro()` (seção 1.6), com cache local de 5 minutos.
2. Acrescentar os provedores de `tPrvdrDadoMercd` (pela `ProvedorEntity`: `nomeProvedor` e `descricao`) e os enums e catálogos do serviço (seção 1.3), cada valor com o `rotulo` e a `descricao` do `messages.properties`.
3. Engine fora: devolver a **cópia embutida** (a tabela da seção 4.1, com os modelos nativos e os rótulos em código) com o aviso `VALORES_SEM_ENGINE`. Nunca falha por causa do engine.
4. OpenAPI: `enum` em `unidade`, `dayCounterCotacao`, `compounding`, `situacao` e em cada chave de `parametros`; `modeloConstrucao`, `interpolador` e `CALENDARIO` como `string` com os nativos na descrição.

---

## 6. Painel (`PainelService`, `GET /api/v1/curvas-mercado/painel`)

1. `dataBase`: a informada; sem ela, hoje se for útil no `Brazil`/`Settlement` (feriados do engine, seção 1.6), senão o dia útil anterior. Engine fora: só sábado e domingo recuam.
2. Uma chamada a `situacao(dataBase)` e uma a `feriados` por consulta. Engine fora → todas as linhas `SITUACAO_INDISPONIVEL` e aviso `ENGINE_INDISPONIVEL`; nunca falha.
3. Por curva com código (ativas ou não), montar a linha com os campos da tabela "Colunas de cada linha" da spec: curva, origem principal, `origensSecundarias` (ligações de prioridade maior, com o modelo de `MODELOS_POR_ORIGEM` ou `modeloConstrucao`), configuração vigente na data, `ultimaDataPublicada`/`calculadoPor` (`dBaseReft`/`cUsuarCalc`), `quantidadePontos` e `hashPontos` (de `tDadoVertcCurva`), `insumo`, `interpolada` e `conferencia` (do engine).
4. Situação: a **primeira** regra da tabela "Situação na data-base" da spec que se aplica, nesta ordem: `NAO_E_DIA_UTIL`, `IGNORADA`, `SITUACAO_INDISPONIVEL`, `INTERPOLADA_DESATUALIZADA`, `CONSTRUIDA`, `DIVERGENTE_DA_FONTE`, `AGUARDANDO_COMPONENTES`, `AGUARDANDO_CARGA`, `COM_ERRO`, `NAO_CONSTRUIDA`, com `motivo` e `atencao` da tabela.
5. `atrasada`: situação `AGUARDANDO_CARGA` ou `NAO_CONSTRUIDA` e (data-base passada, ou data-base hoje e `LocalTime.now()` depois de `curves.painel.horario-esperado.{provedor}`; provedor sem horário → `false` hoje).
6. Contadores sobre todas as curvas antes dos filtros; depois aplicar `situacao`, `provedor`, `nome`, `somenteAtencao`; ordenar por código.

---

## 7. Planilha do cadastro (`CadastroPlanilhaService`)

### 7.1 Estrutura e exportação

Abas e colunas exatamente como na spec `cadastro-curvas-planilha` (`Curvas`, `Ligacoes`, `Configuracoes`, `Valores`). Na aba `Configuracoes`, uma coluna por chave de parâmetro, na ordem da tabela 4.1; `MODELOS_POR_ORIGEM` como texto `B3/TS=PRONTA_TS_B3;...` em ordem alfabética da chave. Datas: célula de data `dd/mm/yyyy`; números: célula numérica. `Controle` = `ETag` da curva. Listas suspensas (`DataValidationHelper.createFormulaListConstraint` apontando para intervalos da aba `Valores`): restritivas em `Unidade`, `DayCounterCotacao`, `Compounding`, `Situacao`, `Provedor` e parâmetros com lista; só com aviso (`setErrorStyle(WARNING)`) em `ModeloConstrucao`, `Interpolador` e `CALENDARIO`. Arquivo `cadastro-curvas_{AAAAMMDDHHmmss}.xlsx`.

### 7.2 Importação (`modo=SIMULACAO|APLICACAO`, `formato=json|xlsx`)

1. Ler as abas (até 5 MB, 1.000 curvas). Data em texto `dd/mm/aaaa` ou `aaaa-mm-dd`; número em texto com vírgula **ou** ponto, sem milhar; os dois juntos → erro na célula.
2. Para cada curva da aba `Curvas`, montar o **estado desejado** (curva, ligações, versões) e comparar com o banco:
   - curva nova → `INCLUSAO`; existente com campo diferente → `ALTERACAO` (inclusive código); `Controle` ≠ `ETag` atual → erro `ALTERADO_POR_OUTRO` na linha;
   - ligações por (`Provedor`, `Produto`): nova → inclusão; mudou `CodigoNaFonte`/`Prioridade` → alteração; ausente da aba → exclusão;
   - versões: `Versao` preenchida tem de ser igual à existente em tudo (senão erro "versões existentes não se alteram"); `Versao` vazia → versão nova (regras da seção 4.2, em ordem de `InicioVigencia`); última versão futura ausente → exclusão;
   - linha de `Ligacoes`/`Configuracoes` de curva fora da aba `Curvas` → erro.
3. Todas as regras das seções 2, 3 e 4 valem linha a linha.
4. `SIMULACAO`: nada gravado, sem trava. `APLICACAO`: uma transação; qualquer erro → 422 com os mesmos erros e nada aplicado; sucesso → `CADASTRO_ALTERADO` com o mesmo `idLote` (UUID) por mudança.
5. `formato=xlsx`: a planilha enviada com a coluna `Resultado` em cada aba e a aba `Resumo`.

Exportar e importar sem editar MUST dar zero mudanças e nenhuma escrita.

---

## 8. Pontos: consulta e exclusão (`DadoVerticeCurvaService`)

Entidades: `DadoVertcCurvaEntity` (`tDadoVertcCurva`, `@IdClass` com `dBaseReft`, `cTickerIndcd`, `dVertcReft`; `cDiaUtil`, `vFatorDia`, `vFatorAcum`, `cQtdDiaPer`, `cQtdDiaReft`, `vPrecoTx`) e `DadoCurvaEntity` (`tDadoCurva`, só para o `DELETE`).

- `GET .../pontos?de=&ate=` (até 366 dias): datas-base com pontos, quantidade e `hashPontos` de cada uma.
- `GET .../pontos/{dataBase}`: pontos (`data` = `dVertcReft`, `valor` = `vPrecoTx`, `diasUteis` = `cDiaUtil` ou nulo) e `hashPontos`.
- `DELETE .../pontos/{dataBase}`: trava (seção 10.2), apaga `tDadoVertcCurva` e `tDadoCurva` da curva e data, log `PONTOS_EDITADOS` (`EXCLUSAO`).

`hashPontos` (igual ao engine):
```java
String texto = pontos.stream().sorted(comparing(Ponto::data))
  .map(p -> p.data() + ";" + (p.valor().signum() == 0 ? "0" : p.valor().stripTrailingZeros().toPlainString()))
  .collect(joining("\n"));
return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(texto.getBytes(UTF_8)));
```

---

## 9. Planilha de pontos (`DadoVerticeCurvaPlanilhaService`)

Aba única `Pontos`, colunas `Curva`, `DataBase`, `DataPonto`, `Valor`, `DiasUteis`, ordenadas por curva, data-base e data. `Valor`: célula numérica se tiver até 15 dígitos significativos (`v.precision() <= 15`), senão texto com vírgula decimal. `DiasUteis`: `cDiaUtil` gravado, célula vazia se nulo. Exportação: até 50 códigos, 366 dias, 100.000 pontos (acima → 400); arquivo `pontos-curvas_{AAAAMMDDHHmmss}.xlsx`.

Importação: planilha sem a coluna `DiasUteis` é aceita como vazia; colunas extras ignoradas. Cada par (`Curva`, `DataBase`) é a lista completa, com o mesmo efeito do `PUT` (seção 10). `APLICACAO`: uma transação, travas em ordem alfabética de nome, 120 s; recusa por consistência de banco em qualquer par → 422 e nada aplicado. Depois do commit, `regravarInterpolada` por par presente; falha → aviso `INTERPOLADA_DESATUALIZADA` nas linhas do par. `formato=xlsx`: colunas `Resultado` e `ValorGravado`, abas `Resumo` e `Exclusoes`.

---

## 10. Edição manual dos pontos (`PUT .../pontos/{dataBase}`)

### 10.1 Validação (recusa só por consistência de banco → 422 `PONTOS_INVALIDOS`, um `Detalhe` por ponto)

Lista vazia; ponto sem data ou valor; data inválida; valor não decimal; `diasUteis` não inteiro; datas repetidas; valor arredondado que não cabe em `DECIMAL(28,12)` (mais de 16 dígitos inteiros), ou, sem configuração vigente, com mais de 12 casas. Curva inexistente → 404 `NAO_ENCONTRADO`.

### 10.2 Gravação (uma transação; até 60 s para obter a trava)

Os 60 s são só para obter a trava, no próprio comando: `query.setHint("jakarta.persistence.query.timeout", 60000)` (vai ao JDBC como `Statement.setQueryTimeout` e não fica na conexão do pool). O estouro desse comando (`jakarta.persistence.QueryTimeoutException`) responde `ERRO_INTERNO` com a mensagem "trava da curva não obtida em 60 segundos" (a spec `pontos-curva-manual` não tem código próprio para isso); outra falha de banco, `ERRO_INTERNO` com a própria mensagem. **Proibido** `SET LOCK_TIMEOUT` e `@Transactional(timeout = ...)` (a mesma regra do engine, spec `curve-engine-resilience`).

```sql
SELECT cTickerIndcd FROM tCurvaMercd WITH (UPDLOCK, ROWLOCK) WHERE cTickerIndcd = ?;     -- a mesma trava do engine; tempo limite do comando: 60 s
SELECT dVertcReft, vPrecoTx, cDiaUtil FROM tDadoVertcCurva WHERE cTickerIndcd = ? AND dBaseReft = ?;
```
1. Arredondar cada valor pela configuração vigente na data-base (`CASAS_DECIMAIS`, `MODO_ARREDONDAMENTO`); mudou → aviso `VALOR_ARREDONDADO` (enviado e gravado). Sem configuração → grava como enviado, aviso `SEM_CONFIGURACAO`.
2. Comparar com o gravado: um ponto muda se o valor (`compareTo`) ou os `diasUteis` mudarem. Nada mudou → aviso `SEM_MUDANCA`, sem escrita e sem log (mas segue para o passo 5).
3. Escrever só a diferença em `tDadoVertcCurva`: `DELETE` dos ausentes da lista; para cada novo ou alterado, gravar `dBaseReft`, `cTickerIndcd` (nome), `dVertcReft`, `vPrecoTx` (arredondado), `cDiaUtil` = `diasUteis` (nulo se não informado), `cQtdDiaPer` = dias corridos da data-base ao ponto, `cQtdDiaReft` = 30/360 (Bond Basis: `d1 = min(dia1,30)`; `d2 = (dia2 == 31 && d1 == 30) ? 30 : dia2`; `360·Δano + 30·Δmês + (d2 − d1)`), `vFatorAcum` e `vFatorDia` nulos. Pontos que não mudaram: intocados.
4. Reler os pontos da data e conferir o `hashPontos` contra o da lista arredondada; diferente → desfaz tudo, 500 `ERRO_INTERNO`.
5. Depois do commit (também com `SEM_MUDANCA`): `regravarInterpolada(codigo, dataBase)`; falha → aviso `INTERPOLADA_DESATUALIZADA` com o motivo. A edição já está gravada.
6. Log `PONTOS_EDITADOS` (seção 10.5) se algo mudou.

Nunca escrever `dBaseReft`/`cUsuarCalc` de `tCurvaMercd`, nem `tDadoCurva` (exceto o `DELETE` da seção 8). Sem `If-Match`: quem salva por último vence.

### 10.3 Avisos de regra de negócio (nunca recusam)

Feriados: `feriados(CALENDARIO, MERCADO_CALENDARIO, anos das datas)` da configuração vigente; falha ou sem configuração → aviso `CALENDARIO_NAO_VERIFICADO` e pular as checagens de calendário.

| Aviso | Quando |
|---|---|
| `PONTO_ANTES_DA_DATA_BASE` | data ≤ data-base |
| `PONTO_EM_FIM_DE_SEMANA` | sábado ou domingo |
| `PONTO_EM_FERIADO` | feriado do calendário da configuração vigente |
| `VALOR_NAO_POSITIVO` | unidade `PRECO`/`PONTOS` e valor ≤ 0 |
| `DIAS_UTEIS_DIFERENTES_DO_CALENDARIO` | `diasUteis` ≠ dias úteis do calendário em (data-base, data] |
| `DIAS_UTEIS_INCOERENTES` | `diasUteis` < 1 ou > dias corridos |
| `DIAS_UTEIS_FORA_DE_ORDEM` | em ordem de data, `diasUteis` ≤ o de um ponto anterior com `diasUteis` |

Taxa negativa: sem aviso.

### 10.4 Resposta

200 com os pontos relidos (valor como string na escala gravada), `hashPontos` novo e `avisos`.

### 10.5 `PONTOS_EDITADOS`

Nível `AVISO`: `correlationId`, `usuario`, `codigo`, `nome`, `dataBase`, `operacao` (`SUBSTITUICAO`/`EXCLUSAO`), `origem` (`API`/`PLANILHA`), `idLote`, quantidade de pontos antes e depois, `hashPontos` antes e depois, instante de Brasília. Sem registro de auditoria.

---

## 11. Curva primária B3 (`BtrsCurvaPrimrService`, spec `curva-primaria-b3`)

Entidade `BtrsCurvaPrimrEntity` (`tBtrsCurvaPrimr`: `cldtfdUnic` `@Id` sem geração, `cTickerIndcd`, `dBaseReft`, `cDiaCorri`, `cDiaUtil`, `vPrecoTx`, `vFatorAcum`, `vFatorDia`), porta `BtrsCurvaPrimrRepositoryPort`. Record do domínio `BtrsCurvaPrimr(Integer id, int diasCorridos, int diasUteis, BigDecimal valor, BigDecimal fatorAcumulado, BigDecimal fatorDia)`; o resultado da validação de coluna é um `sealed interface` (`Valida(BtrsCurvaPrimr linha)` ou `Invalida(List<Detalhe> erros)`), lido com `switch` por pattern.

Listagem geral (uma consulta; `codigosNaFonte` numa segunda consulta só para as curvas da página):
```sql
SELECT m.cTickerIdtfdUnic, m.cTickerIndcd, m.cSitReg, b.dBaseReft, COUNT(*) AS quantidade,
       CASE WHEN EXISTS (SELECT 1 FROM tDadoVertcCurva v WHERE v.cTickerIndcd = b.cTickerIndcd AND v.dBaseReft = b.dBaseReft) THEN 1 ELSE 0 END AS construida
  FROM tBtrsCurvaPrimr b JOIN tCurvaMercd m ON m.cTickerIndcd = b.cTickerIndcd
 WHERE b.dBaseReft BETWEEN ? AND ? AND m.cTickerIdtfdUnic IS NOT NULL   -- + filtros de código e nome
 GROUP BY m.cTickerIdtfdUnic, m.cTickerIndcd, m.cSitReg, b.dBaseReft
 ORDER BY b.dBaseReft DESC, m.cTickerIdtfdUnic
OFFSET ? ROWS FETCH NEXT ? ROWS ONLY;
SELECT cTickerIndcd, cTickerPrvdr FROM tCurvaPrvdr WHERE iPrvdrDados = 'B3' AND cPrvdrMercd = 'TS' AND cTickerIndcd IN (...);
```

Escrita (uma transação, trava como na seção 10.2, com o mesmo tempo limite de 60 s no comando e a mesma proibição de `SET LOCK_TIMEOUT` e `@Transactional(timeout)`):
```sql
SELECT cTickerIndcd FROM tCurvaMercd WITH (UPDLOCK, ROWLOCK) WHERE cTickerIndcd = ?;
SELECT ISNULL(MAX(cldtfdUnic), 0) FROM tBtrsCurvaPrimr WITH (UPDLOCK, HOLDLOCK);          -- só no POST
INSERT INTO tBtrsCurvaPrimr (cldtfdUnic, cTickerIndcd, dBaseReft, cDiaCorri, cDiaUtil, vPrecoTx, vFatorAcum, vFatorDia) VALUES (?, ?, ?, ?, ?, ?, ?, ?);
UPDATE tBtrsCurvaPrimr SET cDiaCorri = ?, cDiaUtil = ?, vPrecoTx = ?, vFatorAcum = ?, vFatorDia = ? WHERE cldtfdUnic = ? AND cTickerIndcd = ? AND dBaseReft = ?;
DELETE FROM tBtrsCurvaPrimr WHERE cldtfdUnic = ? AND cTickerIndcd = ? AND dBaseReft = ?;   -- 0 linhas → 404
DELETE FROM tBtrsCurvaPrimr WHERE cTickerIndcd = ? AND dBaseReft = ?;                       -- data inteira
SELECT cldtfdUnic, cDiaCorri, cDiaUtil, vPrecoTx, vFatorAcum, vFatorDia FROM tBtrsCurvaPrimr WHERE cTickerIndcd = ? AND dBaseReft = ? ORDER BY cDiaCorri, cldtfdUnic;
```
`PUT` e `DELETE` de linha filtram por id, nome e data-base: linha de outra curva ou data dá 0 linhas → 404 `NAO_ENCONTRADO`.

1. Validação de coluna (422 `DADOS_INVALIDOS`, um `Detalhe` por campo): `diasCorridos`, `diasUteis` e `valor` obrigatórios; inteiros em `INT`; `valor` com `precision() - scale() <= 16` e `scale() <= 12`; fatores com `precision() - scale() <= 12` e `scale() <= 16`. Nada é arredondado.
2. Avisos (seção 1.3, `AvisoCurva`), sobre as linhas relidas da data: `DIAS_CORRIDOS_NAO_POSITIVO` (< 1), `DIAS_UTEIS_INCOERENTES` (< 1 ou > dias corridos), `DIAS_CORRIDOS_REPETIDOS` (agrupar por dias corridos, um aviso por grupo com mais de uma linha, citando os ids), `CURVA_SEM_LIGACAO_B3` (nenhuma ligação `B3`/`TS`), `CURVA_JA_CONSTRUIDA` (`EXISTS` em `tDadoVertcCurva` na data). A consulta calcula os mesmos avisos.
3. Depois do commit: log `CURVA_PRIMARIA_EDITADA` (nível `AVISO`: `correlationId`, `usuario`, `fonte` = `B3`, `codigo`, `nome`, `dataBase`, `operacao` `INCLUSAO`/`ALTERACAO`/`EXCLUSAO`/`EXCLUSAO_DATA`, `linhaAntes`, `linhaDepois` ou `linhasApagadas`, `quantidadeAntes`, `quantidadeDepois`). Sem auditoria, sem chamada ao engine, sem escrita em `tDadoVertcCurva`, `tDadoCurva` ou `tCurvaMercd`.
4. Resposta: `POST` 201 com a linha gravada e `avisos`; `PUT` 200 com a linha e `avisos`; `DELETE` 200 com `avisos` da data (vazio se a data ficou sem linhas).

---

## 12. Remover e conferir

Nada a remover no serviço (é novo ou é do outro dev). Conferir que nenhum código do curves grava `dBaseReft`, `cUsuarCalc`, `tDadoCurva` (fora do `DELETE`) nem usa Blob.

## 13. Ordem de implementação (uma tarefa de `tasks.md` por vez; `mvn -q compile` ao fim de cada uma)

1. Seção 1 (tarefas 1.x).
2. Seção 2 (2.x), seção 3 (3.x), seção 4 (4.x), seção 5 (4.4).
3. Seção 7 (5.x).
4. Seções 8 e 10 (7.x), seção 9 (8.x), origens e dias úteis (9.x).
5. Seção 6 (6.1).
6. Seção 11 (10.x).
7. Seção 14 (testes), seção 15 (homologação).

## 14. Testes (ao final)

Ordem: **verificar, adaptar, criar, rodar**. Só `spring-boot-starter-test` (JUnit 5 com testes parametrizados, Mockito, AssertJ, MockMvc); sem ArchUnit. **Sem banco nem engine reais**: portas de saída (repositórios e `EnginePort`) com Mockito. Fuso dos testes como no `main`: `TimeZoneExtension` registrada por autodetecção do JUnit (`junit-platform.properties` + `META-INF/services`), com `TimeZone.setDefault` no `beforeAll`. Quem depende de "hoje" (vigência, atraso do painel) recebe a data ou a hora por parâmetro do método testado.

### 14.1 Verificar

`mvn -q compile` limpo. Buscas sem resultado em `src/main`: `class Relogio`, `NOLOCK`, `synchronized`, `BlobServiceClient`, `double`/`Double` em valor de curva, gravação de `dBaseReft` ou `cUsuarCalc`; e em `src/main/java/br/com/poc/domain`: `import org.springframework`, `import jakarta`, `import com.fasterxml.jackson.databind`, `import tools.jackson`, `import org.apache.poi`, `import br.com.poc.adapter`, `import br.com.poc.application`.

### 14.2 Criar

| Teste | Casos |
|---|---|
| `EtagCurvaMercadoTest` | vetor da seção 0.2; ordem de chaves; `dBaseReft` alterado não muda o `ETag`; `CHAR` com espaços dá o mesmo `ETag` |
| `CurvaMercadoServiceTest` | cenários da spec `cadastro-curva-mercado` (criação da DIxPRE, nome que colide, preço com cotação, renomear, inativação, duas pessoas editando → 412, sem `If-Match` → 428, enum em caixa errada) |
| `CurvaProvedorServiceTest` | cenários da spec `ligacao-curva-provedor` (TaxaSwap, provedor inexistente, `TCEN`, ciclo de 2 e de 3 curvas, última ligação excluída, avisos); o SQL do `MAX + 1` com `UPDLOCK, HOLDLOCK` enviado ao repositório |
| `ValidadorParametrosTest` | um caso por regra da tabela 4.1 (os mesmos da spec do engine); ordem das chaves em `cModDado`; 1.024 caracteres |
| `ConfiguracaoCurvaServiceTest` | cenários da spec `configuracao-calculo-curva` (troca a partir de amanhã, correção retroativa, desistência, vigente em data antiga, unidade que invalida, `MODELOS_POR_ORIGEM` com e sem ligação) |
| `ValoresServiceTest` | engine respondendo; engine fora com `VALORES_SEM_ENGINE`; todo valor com `rotulo` e `descricao`; tabela embutida igual à parte fixa da resposta do engine (resposta gravada em `src/test/resources/valores-engine.json`) |
| `CadastroPlanilhaServiceTest` | exportar e importar sem editar → zero mudanças; 30 prioridades trocadas; ligação removida; versão existente editada → erro; erro impede o lote; `Controle` antigo → `ALTERADO_POR_OUTRO`; vírgula e ponto juntos → erro; `MODELOS_POR_ORIGEM` malformado |
| `PainelServiceTest` | todos os cenários da spec `painel-curvas`, uma linha por situação e por `motivo`, engine fora, feriado americano, carga atrasada, origem secundária |
| `DadoVerticeCurvaServiceTest` | vetores da seção 0.2 (`hashPontos`, arredondamento, `diasUteis` 76 com 30/360 = 110); cenários da spec `pontos-curva-manual` (um valor, ponto retirado, casas a mais, lista igual sem escrita, conferência divergente desfaz, data sem construção, engine fora com `INTERPOLADA_DESATUALIZADA`, feriado, sábado, repetida); regravação chamada também com `SEM_MUDANCA` |
| `BtrsCurvaPrimrServiceTest` | cenários da spec `curva-primaria-b3` (listagem da carga, consulta da PRE, linha de outra data → 404, repetidos, valor com 13 casas → 422, correção depois da construção, data sem carga digitada); nenhuma chamada à `EnginePort`; `MAX + 1` com `UPDLOCK, HOLDLOCK` enviado ao repositório; log sem o evento quando o commit falha |
| `DadoVerticeCurvaPlanilhaServiceTest` | cenários da spec `pontos-curva-planilha`; planilha sem `DiasUteis`; `DiasUteis` apagado → `ALTERACAO`; valor da `PTX` 56,3772259 numérico |
| `ApiContratoTest` (MockMvc) | um teste por rota com 401, 403 e papel certo; um por `codigoErro`; `X-Correlation-Id` em sucesso, erro e `xlsx`; decimais como string |
| `FusoTest` | `OffsetDateTime.ofInstant(Instant.parse("2026-09-15T01:30:00Z"), ZoneId.systemDefault())` → `2026-09-14T22:30-03:00`; subida recusada com outro fuso |

### 14.3 Rodar

`mvn verify` passa sem banco nem engine. Teste que falha e reflete a spec → corrigir o código.

## 15. Conferido na homologação (não é teste automatizado)

Com o banco, o engine e o Entra ID do projeto:
- as 7 curvas do `exemplo-cadastro-7-curvas.txt` cadastradas pela API e pela planilha, e a simulação do engine sem `CADASTRO_INVALIDO` em nenhuma;
- `ETag` real depois da criação da `PRE` igual ao vetor da seção 0.2 (se o `idLigacao` for 1);
- duas inclusões de ligação simultâneas com `idLigacao` diferentes;
- edição de pontos enquanto o engine constrói a mesma curva: espera e grava por cima;
- tarefa 11.2 do `tasks.md` (construir, editar, interpolar, carga sem sobrescrever, painel `DIVERGENTE_DA_FONTE`, recálculo forçado);
- `openspec validate curves-cadastro-curvas --strict`.

# Guia de implementação: curves-cadastro-curvas

> **Jackson 3 (Spring Boot 4):** para injetar o mapper do Spring, use `tools.jackson.databind.ObjectMapper` (ou `tools.jackson.databind.json.JsonMapper`), com `tools.jackson.core.type.TypeReference` e `JsonNode` de `tools.jackson.databind`. **Nunca** `com.fasterxml.jackson.databind.ObjectMapper`/`JsonMapper`: o Boot 4 não cria esse bean, e a aplicação não sobe ("required a bean of type 'com.fasterxml.jackson.databind.ObjectMapper' that could not be found"). Só as anotações continuam em `com.fasterxml.jackson.annotation`. No Jackson 3, `asText()` virou `asString()`.

> **Parâmetros de rota em objeto:** controller não recebe uma fila de `@RequestParam`. Com mais de dois parâmetros, agrupe num record: `@ModelAttribute FiltroX filtro` para consultas `GET` (o Spring preenche os campos pelos nomes da query) e `@RequestBody PedidoX pedido` para comandos `POST`/`PUT`. `@PathVariable` (identificadores como `codigo` e `dataBase`) continua separado.

> **Tipagem forte (Java 21):** dentro do domínio e das portas, nada de `Map<String, Object>`, `Object[]`, `Object` genérico ou `String` com JSON dentro. Valores fechados viram `enum`; dados viram `record`; variantes viram `sealed interface` com records. JSON cru só na borda (controller, cliente HTTP, coluna `cModDado`), desserializado direto num record; consultas nativas devolvem projeção em record.

> **Esta change é de conferência: boa parte do curves já foi desenvolvida no repositório real**, muitas vezes com outro nome (por exemplo, `Ponto` ou `Linha` no lugar de `Vertice`, ou em inglês). Esta change já foi aplicada no repositório real numa versão anterior, antes de ser separada em partes e renomeada; por isso há muita coisa feita, com nomes antigos e com peças que hoje estão em outra change (ex.: cliente do engine, vértices manuais, `/primaria-b3`) ou saíram. Cada tarefa SHALL seguir este roteiro:
> 1. **Conferir:** procurar pela **funcionalidade** (o que faz, que tabela lê ou grava, que rota expõe), nunca só pelo nome da spec.
> 2. Decidir uma de três saídas:
>    - **já existe e cumpre a spec → deixar como está** (não reescrever, não "melhorar"; só o nome segue o passo 3);
>    - **existe e diverge → alterar** só o que diverge, no código que já existe;
>    - **não existe → criar**, com os nomes desta change.
> 3. Nunca criar uma segunda versão do que já está pronto. **Nome diferente do desta change é divergência:** renomeie para o nome da change (classe, arquivo, método, campo interno e todas as referências, até compilar), menos o que outro serviço ou o banco já usa (rota exposta, campo de JSON de resposta, coluna, tópico): isso fica como está e vai para a anotação como `nome-real → nome-da-change`.
> 4. Ao marcar a tarefa, anotar ao lado a saída e o arquivo: `[conferido: pronto | alterado | renomeado de X | criado] caminho/Arquivo.java`. Na dúvida se é a mesma coisa, `// TODO(revisao): <dúvida>` e siga.
> 5. Código que a spec atual não pede (sobra da versão anterior): não apagar; anotar como `[sobra] caminho/Arquivo.java` no resumo, para a revisão decidir.

Guia passo a passo para aplicar esta change com o mínimo de decisões. **A spec manda; este guia diz onde e como.** Siga as seções na ordem. Os testes são escritos só na seção 14, depois de tudo compilar.

> **O código real manda nos detalhes.** Este guia foi escrito sobre a cópia transcrita do serviço (`services/curves`, pacote `br.com.poc`). No repositório real, o pacote raiz, as classes de exceção, o tratador de erro que de fato responde, o record de erro, a biblioteca de log, a configuração do Jackson, o cache e o registro de beans **são os que o serviço já tem**: onde este guia cita uma classe ou configuração do código, leia "a equivalente do serviço" e confira antes de usar. O que não muda é o comportamento das specs (rotas, códigos de erro, avisos, regras, formatos). Divergência entre o guia e o código real não é motivo para parar: siga o código real e cumpra a spec.

## 0. Regras para quem implementa

- **Serviço:** no poc, `services/curves`; no sistema real, `acts-srv-curvas` (a raiz do repositório). Já existe: não se cria serviço nem se muda a estrutura, acrescenta-se. Os caminhos abaixo são relativos ao pacote raiz do serviço (no poc, `br.com.poc`). O que existe e é reaproveitado (nomes do poc; no real, conferir):
  - `adapter/in/api/rest/...` (controllers, DTOs REST, o tratador de erro), `adapter/out/persistence/{entity,repository,mapper}` e os adaptadores de persistência;
  - `application/{port/in/usecase,port/out,service,dto,mapper,model,exception}`: portas de saída `<Tabela>RepositoryPort`, casos de uso `<Nome>UseCase` com implementação em `application/service`, exceções com `ErrorCode`;
  - a forma de registrar os serviços (no poc, `@Bean` em `infrastructure/config/BeanConfig`): os serviços novos são registrados **do mesmo jeito** que os existentes; o utilitário de normalização de nome, se houver;
  - entidades já existentes: `CurvaMercdEntity`, `ProvedorEntity` (CRUD de provedores do outro dev, não tocar) e `BloombergCurvaPrimrEntity`. Não criar outra entidade para a mesma tabela; conferir as colunas e os tipos contra o `001_SCRIPT_INICIAL.sql` (seção 2.1).
- **Classes novas** ficam nos mesmos pacotes e têm o nome da tabela: `CurvaPrvdrEntity`, `ConfgCurvaEntity`, `DadoVertcCurvaEntity`, `DadoCurvaEntity`, `BtrsCurvaPrimrEntity`; portas `CurvaPrvdrRepositoryPort`, a porta de configuração (no real já existe como `ConfiguracaoCurvaRepositoryPort` e mantém o nome), `DadoVertcCurvaRepositoryPort`, `DadoCurvaRepositoryPort`, `BtrsCurvaPrimrRepositoryPort`; a `CurvaMercdRepositoryPort` existente ganha os métodos novos. Nomes de infraestrutura (cliente do engine, filtro, planilha) podem ser em inglês.
- **Onde fica cada tipo de classe** (vale para todo código novo; conferir antes de cada commit):

| Tipo | Camada | Exemplos |
|---|---|---|
| regra, `record` de dado, enum, validador, `Input` de caso de uso, resultado | `domain` | `CurvaMercado`, `CurvaMercadoInput`, `CriarCurvaProvedorInput`, `ValidadorParametros`, `CurvaAuditoria` (o dado) |
| caso de uso (interface) | `application/port/in/usecase` | `CurvaMercadoUseCase`, `CurvaProvedorUseCase` |
| porta de saída | `application/port/out` | `CurvaMercdRepositoryPort`, `CurvaPrvdrRepositoryPort`, a porta de configuração que já existir, `BtrsCurvaPrimrRepositoryPort`, `EventosPort` (a `EnginePort` é da fase 2, seção 1.6) |
| implementação do caso de uso | `application/service` | `CurvaMercadoService`, `CurvaProvedorService`, `ConfiguracaoCurvaService`, `BtrsCurvaPrimrService`, **um só** `ValoresService` |
| exceção e códigos de erro | `application/exception` | `CadastroErrorCode`, `ConflictException` |
| controller, `Request`, `Response`, filtro, tratador de erro, gerador da planilha de resposta (POI) | `adapter/in/api/rest` | `CurvaMercadoController`, `CriarCurvaProvedorRequest`, `CorrelationIdFilter`, `CurvaAuditoriaExcelGenerator` |
| entidade JPA, repositório Spring Data, adaptador de persistência | `adapter/out/persistence` | `CurvaPrvdrEntity`, `CurvaPrvdrRepository`, `CurvaPrvdrPersistenceAdapter` |
| log de eventos (e, na fase 2, o cliente do engine) | `adapter/out/...` | o adaptador de log (`EngineHttpClient` só na fase 2) |

  Regras que não se quebram: entidade JPA nunca fora de `adapter/out/persistence`; porta nunca em `adapter`; nada de POI, Jackson, Spring ou JPA no `domain`; `Request`/`Response` só no adaptador de entrada, e o `Input` correspondente no domínio. Portas de saída novas levam o nome da tabela; uma porta que já existia no serviço mantém o nome que tem (ex.: `ConfiguracaoCurvaRepositoryPort`). Casos de uso e serviços levam o nome de negócio. Nenhum resquício do nome antigo "ligação" (ex.: `LigacaoCanonicoState` → `CurvaProvedorCanonicoState`).
- **Dois modelos, sem misturar:** `application/model` (`CurvaMercd`, `Provedor`, `BloombergCurvaPrimr`) é do código existente (beans mutáveis com Lombok) e não é reescrito. O código novo põe as regras em **`br.com.poc.domain`**, Java puro, com `record`s; as portas novas recebem e devolvem esses `record`s. As conversões entidade ↔ `record` ficam no adaptador de persistência.
- **Mesma base do engine** (guia do `engine-construcao-curvas`, seção 0):
  - **hexagonal:** `domain` em Java puro (regras de campo, vigência, validador de parâmetros, `hashPontos`, 30/360, situação do painel), sem Spring, JPA, Jackson ou POI; `application/port/in/usecase` (um caso de uso por área) e `application/port/out`, uma porta por tabela mais `PlanilhaPort` e `EventosPort` (a `EnginePort` entra na fase 2, na change `curves-operacao-curvas`); `application/service` implementa os casos de uso; os adaptadores implementam as portas. O serviço conhece só as portas. Dependências: `adapter → application → domain`, nunca o contrário (conferido na seção 14.1);
  - **Java 21 nativo:** `record` para todo dado novo (domínio, comandos, resultados, eventos, linhas de planilha), com `List.copyOf` no construtor compacto para imutabilidade; `sealed interface` e `switch` com pattern matching onde a variação é fechada (resultado de linha da importação, situação do painel, resposta do engine), **sem `default`**, para o compilador apontar o caso esquecido; `java.time`, `RoundingMode`, `HexFormat`, `MessageDigest`, `Normalizer`, `Currency`, `Locale`; `Stream` e `Collectors.groupingBy`/`teeing`; `Optional` só como retorno de busca (nunca parâmetro nem campo); `var` onde o tipo é óbvio; text blocks para SQL; `String.formatted`. Nada de tipo próprio de data, relógio, arredondamento ou "utils" (o `NormalizadorUtil` existente é reaproveitado para o nome normalizado, se servir);
  - **virtual threads:** a flag `spring.threads.virtual.enabled` do `application.yml` **não é alterada**; o código novo não usa `synchronized`. (Fase 2:) as chamadas independentes ao engine e as regravações interpoladas depois da importação rodam em `Executors.newVirtualThreadPerTaskExecutor()`, com `try-with-resources` (o `StructuredTaskScope` é preview no Java 21 e não se usa);
  - **Lombok e MapStruct:** o código existente usa os dois (entidades, modelos, mapeadores) e continua usando. O código novo usa Lombok **só nas entidades JPA** (`@Getter`, `@Setter`, `@NoArgsConstructor`, como a `ProvedorEntity`) e **nenhum MapStruct novo**: a conversão é um método `de(...)`/`para(...)` no próprio `record` ou adaptador. Injeção por construtor (`final`), nunca `@Autowired` em campo; a classe tem um único construtor, ou, se tiver mais de um, `@Autowired` no que o Spring usa (sem isso o Spring exige construtor sem argumentos);
  - **transação:** `@Transactional` nos métodos de `application/service` que gravam (é o que o código existente faz); o domínio não conhece transação.
- Não invente rota, código de erro, aviso, coluna ou tabela fora deste guia e das specs. Não crie tabela, sequência, índice nem tópico. O serviço não usa o Blob.
- Número de curva é sempre `BigDecimal`, nunca `double` (a única exceção é a célula numérica da planilha, seção 9).
- Fuso: a JVM inteira roda em `America/Sao_Paulo` (seção 1.4); `LocalDate.now()` e `OffsetDateTime.now()` são usados direto. Leitura em `READ COMMITTED`, nunca `NOLOCK`.
- Mensagens em pt-BR com acentuação, UTF-8. Códigos (enums, `codigoErro`, avisos) não se traduzem.
- Arquivos de configuração que já existem: não reescrever; conferir e acrescentar só o que faltar.
- O serviço roda em no mínimo 2 instâncias: nenhum estado de negócio em memória local. Na fase 1 não há cache de valores aceitos (seção 5); o cache de 5 minutos da consulta ao engine é da fase 2.

### 0.1 Tabelas que o serviço usa

| Tabela | O que o serviço faz |
|---|---|
| `tCurvaMercd` | cria e altera a curva; nunca escreve `dBaseReft`, `cUsuarCalc` nem as colunas sem uso da spec |
| `tCurvaPrvdr` | CRUD dos provedores da curva; `cIdtfdUnic` por `MAX + 1` com trava |
| `tConfgCurva` | cria e exclui versões de configuração (`cIdtfdConfg` é identity) |
| `tPrvdrDadoMercd` | só lê (provedores, do CRUD do outro dev) |
| `tDadoVertcCurva` | **vértices da curva** (curva construída): edição manual |
| `tDadoCurva` | curva interpolada: só **apaga** a da data quando os vértices da data são apagados; quem grava é o engine |
| `tBtrsCurvaPrimr` | **dado bruto da B3**: listagem geral e CRUD por linha (seção 11); `cIdtfdUnic` por `MAX + 1` com trava, como o processor |

### 0.2 Vetores de teste reais

| Item | Valor esperado |
|---|---|
| `hashPontos` do vetor comum com o engine (`2026-09-15;13.9\n2026-09-16;-117.96`) | `8dcff432fa5271ff16bdaef72940792811802e0a5ae59e8c2bf5cead818d5544` |
| `hashPontos` da `PRE` de `2026-09-14` construída pelo engine (278 vértices) | `7c4982b34ca35f784863118902e63f28e7d49362d940cc27eb77961d526fca20` |
| vértice da `PRE` em `2027-01-04` (data-base `2026-09-14`) | valor 13.589, 75 dias úteis publicados, 112 dias corridos, 110 dias 30/360 |
| `PUT` com esse vértice e `"diasUteis": 76` | aviso `DIAS_UTEIS_DIFERENTES_DO_CALENDARIO` (calendário: 75); linha com `cDiaUtil` 76, `cQtdDiaPer` 112, `cQtdDiaReft` 110, fatores nulos |
| `13.123456789` na `PRE` (7 casas, `HALF_UP`) | gravado `13.1234568`, aviso `VALOR_ARREDONDADO` |

---

## 1. Base

### 1.1 `pom.xml`: o mínimo de coisas novas

> **Adiado:** a autenticação do curves (Entra ID, papéis, 401/403) entra numa change própria, depois. O engine não exige autenticação, então não há token de serviço para ele. Até lá, não acrescentar `spring-boot-starter-oauth2-resource-server` nem `azure-identity`.

O `pom.xml` do serviço já tem JPA, o driver do SQL Server, validação, springdoc, actuator, a biblioteca de log JSON do serviço e `spring-boot-starter-test`. Conferir e acrescentar **só** o que faltar:

| Dependência | Por quê |
|---|---|
| `org.springframework.boot:spring-boot-starter-oauth2-resource-server` (versão do Spring Boot) | **adiada:** JWT do Entra ID; fica como alvo da change de autenticação |
| `com.azure:azure-identity`, pelo `com.azure:azure-sdk-bom` **1.3.8** em `dependencyManagement` | **adiada:** só se a change de autenticação do curves precisar (o engine não exige token) |
| `org.apache.poi:poi-ooxml` **5.5.1** | planilhas |

Nada mais: o cliente do engine (fase 2) usa o `java.net.http.HttpClient` do JDK (o `openfeign` do pom fica para as integrações que já o usam), sem ArchUnit e sem dependência de teste além do `spring-boot-starter-test` (seção 14).

### 1.2 `application.yml` (conferir e acrescentar)

> **Adiado:** a autenticação do curves (Entra ID, papéis, 401/403) entra numa change própria, depois. Até lá, fica de fora `spring.security`; o engine não exige autenticação, então não existe `curves.engine.escopo`. **Fase 1: do bloco `curves.engine`, só `url`** (para o repasse da seção 16); o resto entra na fase 2, na change `curves-operacao-curvas`, e está abaixo só como referência.

```yaml
curves:
  engine:                                        # FASE 2 (curves-operacao-curvas): não acrescentar na fase 1
    url: ${CURVES_ENGINE_URL}
    timeout-segundos: 10
    timeout-situacao-segundos: 60
    timeout-interpolada-segundos: 60
    cache-valores-minutos: 5
  timeout:
    requisicao-segundos: 60
    importacao-pontos-segundos: 120
spring:
  security:                                      # ADIADO (autenticação): fora até a change própria
    oauth2:
      resourceserver:
        jwt:
          issuer-uri: ${CURVES_JWT_EMISSOR}
          audiences: ${CURVES_JWT_AUDIENCIA}     # o mesmo registro de aplicação do engine
```

Conferir no `application.yml` que já existe, ajustando só o que conflita (o resto fica):

| Chave existente | Ajuste |
|---|---|
| `spring.threads.virtual.enabled`, `spring.datasource.hikari.*`, `spring.jpa.open-in-view` e `hibernate.jdbc.*` | **não mexer**: o `application.yml` existente fica como está nesses blocos (mínimo de mudança no serviço); só se acrescenta o bloco `curves.timeout` acima |
| `spring.jackson.default-property-inclusion: NON_NULL` | manter (é do CRUD de provedores); todo `record` de resposta **novo** leva `@JsonInclude(JsonInclude.Include.ALWAYS)` na classe, porque as specs exigem nulo presente, nunca omitido (painel, vértices, curva) e `avisos` sempre presente |
| `spring.jackson.time-zone: UTC` e `date-format` | os instantes da resposta saem `-03:00` (seção 1.4): o `record` leva `OffsetDateTime` já no fuso de Brasília, e o `time-zone` não se aplica a `OffsetDateTime` |
| `spring.jpa.show-sql: true` | manter em `default`; nos demais perfis `false` (a escrita de vértices tem centenas de `INSERT`) |
| `spring.jpa.properties.hibernate.dialect` | manter |
| tratadores de erro | o serviço pode ter mais de um (no real há um tratador em Problem Details além do que usa o record de erro, e `spring.mvc.problemdetails.enabled` não desliga um tratador próprio). Deixar **um formato só** para as rotas desta change: decidir qual tratador responde (`@Order` ou retirar o outro) e conferir com um teste de rota que 400, 404, 409, 422 e os erros do próprio Spring MVC saem nesse formato |

### 1.3 Erros, avisos e enums: `domain/`

Erros **no padrão que o serviço já tem**. O que a spec exige, e o que o teste confere, é só o conteúdo da resposta: o código da spec (`DADOS_INVALIDOS`, não um código genérico), a mensagem em português, a rota, o `correlationId` e, quando houver, os `detalhes` (`campo`, `linha`, `valor`, `motivo`). Como chegar lá depende do código real:

```java
// um enum de códigos no molde dos enums de erro que o serviço já tem (código = name(), mensagem em pt-BR)
public enum CadastroErrorCode implements ErrorCode {
  PARAMETRO_INVALIDO("Parâmetro inválido"),
  NAO_ENCONTRADO("Recurso não encontrado"), CODIGO_EM_USO("Código em uso"), NOME_EM_USO("Nome em uso"),
  PROVEDOR_DUPLICADO("Provedor da curva duplicado"), PRIORIDADE_EM_USO("Prioridade em uso"),
  DADOS_INVALIDOS("Dados inválidos"), VERTICES_INVALIDOS("Vértices inválidos"), ERRO_INTERNO("Erro interno");
}
// domain: dados puros, sem Spring
public enum CodigoAvisoCurva { /* os 23 códigos da tabela "Avisos do serviço" da spec cadastro-curva-mercado, na ordem da tabela */ }
public record Detalhe(String campo, Integer linha, String valor, String motivo) {}     // usado em erros (detalhes) e em avisos
public record AvisoCurva(CodigoAvisoCurva codigo, String mensagem, List<Detalhe> detalhes) {
  public AvisoCurva { detalhes = List.copyOf(detalhes); }
}
```

- **Exceções:** lançar as exceções do serviço **com o código da spec**. Se uma exceção existente só produz um código genérico (ex.: uma de "não encontrado" que sempre responde `NOT_FOUND`), acrescentar a ela um construtor que recebe `ErrorCode`, ou lançar a exceção que já aceita `ErrorCode`; não criar uma segunda hierarquia. Os status: 400 `PARAMETRO_INVALIDO`, 404 `NAO_ENCONTRADO`, 409 `CODIGO_EM_USO`/`NOME_EM_USO`/`PROVEDOR_DUPLICADO`/`PRIORIDADE_EM_USO`, 422 `DADOS_INVALIDOS`/`VERTICES_INVALIDOS`, 500 `ERRO_INTERNO`. Para 409, criar a exceção que faltar no mesmo molde das existentes.
- **Tratador:** no tratador que responde (tabela da seção 1.2), acrescentar o que faltar para esses status e para JSON malformado e parâmetro inválido (400 `PARAMETRO_INVALIDO`), sem reescrever o que existe.
- **Record de erro:** o que o tratador **realmente usa** (conferir; pode haver cópias sem uso) ganha `correlationId` e `detalhes` no fim, com um construtor no formato antigo para o código que já o chama. Os demais componentes, inclusive o tipo do `timestamp`, ficam como estão. `detalhes` usa o `record Detalhe` do domínio; o domínio nunca importa o pacote do record de erro.
- Sem stack trace na resposta.

Enums da tabela "Enums do serviço" da spec `cadastro-curva-mercado` (`Unidade`, `DayCounterCotacao`, `CompoundingCotacao`, `SituacaoCurva`, `SituacaoPainel`, `MotivoPainel`, `TipoAlteracao`, `OperacaoAlteracao`, `OperacaoVertices`, `OrigemVertices`, `ModoImportacao`, `ResultadoLinha`): enums simples em `domain`, com rótulo e descrição em `messages.properties` (`poc.valores.<Enum>.<CONSTANTE>.rotulo` e `.descricao`), lidos pelo `MessageSource` no adaptador da API (o `application.yml` já tem `spring.messages`, hoje sem `basename`: definir `basename: messages`). O catálogo de `GET /curvas-mercado/valores` lista cada enum de forma explícita (`itens(Unidade.class)`). Teste obrigatório: toda constante tem as suas chaves. Os enums de resultado fechado (`ResultadoLinha`, `SituacaoPainel`) são lidos com `switch` por pattern, sem `default`.

Filtro de correlação (`OncePerRequestFilter`): `X-Correlation-Id` recebido ou `UUID.randomUUID()`, no MDC (`correlationId`) e em toda resposta (inclusive `xlsx` e erro); limpar o MDC no `finally`.

Serialização: `BigDecimal` como string plana, datas `AAAA-MM-DD`, instantes `yyyy-MM-dd'T'HH:mm:ss.SSSXXX`, `avisos` sempre presente. A configuração tem de estar **no mapper que o Spring MVC usa**: o Spring Boot 4 usa Jackson 3 (`tools.jackson`), e um `Module` ou customizador de Jackson 2 (`com.fasterxml.jackson.databind`) que o serviço já tenha é ignorado pelo MVC. Conferir onde o serviço configura o Jackson e pôr ali, no pacote do Jackson que o MVC usa, o formato `STRING` para `BigDecimal` e a escrita sem notação científica; as anotações (`@JsonInclude`, `@JsonFormat`) continuam em `com.fasterxml.jackson.annotation`. Teste de rota: um decimal sai como `"13.900000000000"`.

Enum de entrada com caixa diferente (`taxa`) → 422 `DADOS_INVALIDOS` com o campo e os valores aceitos: ler enums como `String` no `record` de entrada e converter com `Enum.valueOf` em `try/catch`, nunca com a desserialização tolerante do Jackson (`READ_UNKNOWN_ENUM_VALUES_AS_NULL` e semelhantes ficam desligados).

### 1.4 Fuso da JVM

Igual ao engine (seção 1.4 do guia do engine): o `main` (`Application.java`) faz `TimeZone.setDefault(TimeZone.getTimeZone("America/Sao_Paulo"))` antes do Spring, e um `ApplicationRunner` impede a subida se o fuso for outro. Sem classe de relógio própria. `dCriacReg` e `dUltAtulz` (`datetime`) recebem `LocalDateTime.now()` (hora de Brasília).

### 1.5 Segurança

**Sem autenticação na v0 e na v1:** sem Spring Security, sem Resource Server, sem papéis, sem 401 e sem 403. Usuário: o cabeçalho opcional `X-Usuario` (`@RequestHeader(name = "X-Usuario", required = false)`), gravado em `cUsuarAtulz` e nos eventos de log; ausente, fica nulo (a coluna aceita nulo). A autenticação entra numa change própria, depois.

### 1.6 Cliente do engine: porta `EnginePort` e adaptador `adapter/out/client/engine/EngineHttpClient.java`

> **Fase 2 (change `curves-operacao-curvas`, tarefa 1.1): não implementar na fase 1.** Na primeira parte o curves não chama o engine em lugar nenhum; esta seção fica aqui como guia para quando a segunda parte chegar. O engine não exige autenticação: o cliente não envia `Authorization` e não usa token de serviço.

`EnginePort` (em `application/port/out`) devolve um `sealed interface RespostaEngine<T>` com `Disponivel<T>(T valor)` e `Indisponivel<T>(String motivo)` (records). O adaptador usa o **`java.net.http.HttpClient`** do JDK (uma instância, `Version.HTTP_1_1`, `connectTimeout` 5 s), com `HttpRequest.timeout(...)` por chamada, e `X-Correlation-Id` do MDC. O JSON do engine é lido com o `ObjectMapper` do Spring para `record`s do adaptador. Chamadas e tempos:

| Método | Rota do engine | Tempo |
|---|---|---|
| `valoresCadastro()` | `GET /api/v1/valores-cadastro` | 10 s |
| `situacao(dataBase)` | `GET /api/v1/curvas/situacao?dataBase=` | 60 s |
| `feriados(nome, mercado, anoIni, anoFim)` | `GET /api/v1/calendarios/{nome}?mercado=&anoInicial=&anoFinal=&formato=json` | 10 s |
| `regravarInterpolada(codigo, dataBase)` | `POST /api/v1/curvas/{codigo}/{dataBase}/interpolada` | 60 s |

Toda falha (rede, tempo, 4xx, 5xx, JSON ilegível) vira `Indisponivel` com o motivo, nunca uma exceção que derrube a operação do curves. Quem consome usa `switch` por pattern sobre `RespostaEngine`. No painel, `situacao` e `feriados` são independentes e rodam juntas em virtual threads.

### 1.7 Log

JSON com `correlationId`. Enquanto a autenticação está adiada, o campo `usuario` de todos os eventos sai nulo (ausente). `REQUISICAO_CONCLUIDA` (usuário, rota com molde, status, `codigoErro`, duração) em toda requisição. `CADASTRO_ALTERADO` (seção 2.5), `VERTICES_EDITADOS` (seção 10.5) e `CURVA_PRIMARIA_EDITADA` (seção 11) depois do commit (`TransactionSynchronizationManager.registerSynchronization` com `afterCommit`), emitidos pelo adaptador de `EventosPort` com a API fluente do SLF4J (`log.atWarn().addKeyValue("codigo", ...).log("CADASTRO_ALTERADO")`), pela biblioteca de log JSON que o serviço já usa (no poc, `LogstashEncoder`; no real, a do serviço). Conferir que os pares chave-valor saem como campos do JSON; se a biblioteca não os levar, usar o mecanismo dela para campos estruturados. Nunca token nem corpo inteiro.

---

## 2. Curva de mercado

### 2.1 Entidade `CurvaMercdEntity` (já existe)

A `CurvaMercdEntity` do serviço já existe; não criar outra. Conferir contra o `001_SCRIPT_INICIAL.sql` antes de usar (no poc foram corrigidos: coluna inexistente mapeada, nome de coluna errado em outra entidade e `Double` em coluna `DECIMAL`); no real, corrigir o que faltar, em especial `vFatorMultiAtivo` como `BigDecimal` (`DECIMAL(28,12)`). Se a entidade usar `@Data`, os getters escritos à mão continuam valendo. Acrescentar, nela:
- `dBaseReft` e `cUsuarCalc` com `@Column(insertable = false, updatable = false)`: são do engine, o curves nunca os escreve;
- os getters das colunas `CHAR` (`cNormaDia`, `cPaisInstt`, `cSitReg`, `cTpoJuro`, `cTpoCotac`, `cTpoVlr`) devolvendo `stripTrailing()`, escritos à mão (o Lombok não gera um getter que já existe). Gravar sem espaços.

O adaptador `CurvaMercdPersistenceAdapter` (hoje só `existsByTicker`) implementa os métodos novos de `CurvaMercdRepositoryPort` e converte entidade ↔ `record` do domínio `CurvaMercado`; o `CurvaMercdMapper` (MapStruct) e o modelo `CurvaMercd` continuam a serviço do código existente. Consulta com `WITH (UPDLOCK, ROWLOCK)` é `@Query(nativeQuery = true)` no `CurvaMercdRepository`, com `@QueryHints(@QueryHint(name = "jakarta.persistence.query.timeout", value = "60000"))` (espera de 60 s pela trava; esgotada, o erro vira 500 `ERRO_INTERNO`, sem gravar). O mesmo `@QueryHints` vai em toda consulta de trava e em toda de `MAX + 1` do serviço.

### 2.2 Regras (`application/service/CurvaMercadoService.java`, caso de uso `CurvaMercadoUseCase`)

Tabela "Campos da curva de mercado" da spec, campo a campo:
- `codigo`: `^[A-Z0-9_]{1,50}$`; único (`SELECT COUNT(*) FROM tCurvaMercd WHERE cTickerIdtfdUnic = ? AND cTickerIndcd <> ?`) → 409 `CODIGO_EM_USO`.
- `nome`: 1–50; único normalizado (`Normalizer.normalize(s, NFD).replaceAll("\\p{M}", "").toLowerCase(Locale.ROOT).strip()`, comparando em Java contra os nomes de **todas** as curvas, inclusive as sem código) → 409 `NOME_EM_USO`, antes do `save`; no `PUT`, nome diferente do atual → 422 `DADOS_INVALIDOS` ("o nome é imutável").
- `unidade` obrigatória; `dayCounterCotacao` e `compounding` obrigatórios só com `TAXA` e nulos nos demais (preenchido com `PRECO`/`PONTOS` → 422 no campo).
- `moeda` ISO 4217 (`Currency.getInstance`), `pais` ISO 3166-1 alfa-2 (`Locale.getISOCountries()`).
- `inicioVigencia` obrigatória; campo obrigatório ausente é recusado (422 no campo) **antes** de comparar datas, para a comparação nunca receber nulo.
- `fimVigencia` ≥ `inicioVigencia` (só se `inicioVigencia` veio).
- Todos os problemas de campo juntos numa única 422 `DADOS_INVALIDOS`, um `Detalhe` por campo.
- Criação: `cSitReg = ATIVO`, `dCriacReg` e `dUltAtulz` = agora, `cUsuarAtulz` = usuário. Inativação e reativação só trocam `cSitReg` (com log).
- Linhas com `cTickerIdtfdUnic` nulo nunca aparecem nas rotas (`WHERE cTickerIdtfdUnic IS NOT NULL` em toda consulta de listagem e consulta). A exceção é a checagem de nome único: ela lê os nomes de todas as curvas, inclusive as sem código.
- Coerência com a configuração (spec `configuracao-calculo-curva`): mudança de `unidade`, `dayCounterCotacao` ou `compounding` que invalide a versão vigente ou futura → 422 citando a versão.
- Inativar uma curva componente de derivada ativa → aviso `CURVA_COM_FILHAS` (filhas: provedores da curva `TCEN` com `cTickerPrvdr` = nome desta curva).

Listagem: filtros `nome` (trecho normalizado), `codigo` (exato), `unidade`, `situacao`, `provedor` (`EXISTS` em `tCurvaPrvdr` com `iPrvdrDados` = o valor, depois de `trim`) e `dono` (trecho de `cPprioDado`, normalizado); paginação `pagina` (≥ 0) e `tamanho` (padrão 50, máximo 500); ordem por código. Resposta `{ itens, pagina, tamanho, total }`; cada item com `provedores` (de `tCurvaPrvdr`, ordem de prioridade, sem repetição, numa consulta só para a página inteira, `WHERE cTickerIndcd IN (...)`, nunca uma por curva), `dono` (`cPprioDado`) e `ultimaExecucao` (`dBaseReft` + `cUsuarCalc`, nulo sem `dBaseReft`).

### 2.3 Sem controle de concorrência

Nenhuma rota faz controle de versão da curva: quem salva por último vence, e o estado anterior fica no `CADASTRO_ALTERADO` (seção 2.5). Toda alteração (curva, inativação, reativação, provedor da curva, configuração) trava a linha da curva (`SELECT ... WITH (UPDLOCK, ROWLOCK)`, com o `@QueryHints` de 60 s da seção 2.1) só para serializar gravações simultâneas, sem recusar nenhuma. O controle de concorrência otimista está descrito nos extras no fim deste guia.

### 2.4 Rotas (`adapter/in/api/rest/CurvaMercadoController`)

As da tabela "Rotas da curva de mercado" da spec. `GET /curvas-mercado/{codigo}` devolve a curva, os provedores da curva por prioridade e a configuração vigente hoje.

### 2.5 Auditoria

`CADASTRO_ALTERADO` (nível `AVISO`), depois do commit: `idAuditoria` (UUID), `codigo`, `nome`, `tipo`, `operacao`, `usuario`, `instante`, `correlationId`, `idLote` (planilha), `estadoAnterior` e `estadoNovo` completos (a curva com todos os campos da API, os provedores da curva ordenados por `idCurvaProvedor` e as versões ordenadas por `versao`, mais os campos de controle). Commit que falha não gera o evento.

`GET /curvas-mercado/{codigo}/auditoria?formato=json|xlsx`: montado na hora, com todos os campos da curva (inclusive controle e os do engine), todos os provedores da curva, todas as versões com parâmetros. Arquivo `{codigo}_CADASTRO_AUDITORIA_{AAAAMMDDHHmmss}.xlsx`, abas `Curva`, `Provedores`, `Configuracoes`.

---

## 3. Provedores da curva (`CurvaProvedorService`)

Entidade nova `CurvaPrvdrEntity` (`tCurvaPrvdr`, `@Id cIdtfdUnic`) e porta `CurvaPrvdrRepositoryPort`: `cIdtfdUnic`, `cTickerIndcd`, `iPrvdrDados`, `cPrvdrMercd`, `cTickerPrvdr`, `cPriorCsumo`. Provedor: usar a `ProvedorEntity` que já existe no serviço (CRUD de provedores), só para leitura: `@Table(name = "tPrvdrDadoMercd")`, `@Id` `iPrvdrDados` → `nomeProvedor` (o identificador: `B3`, `ANBIMA`, `BLOOMBERG`, `TCEN`), `cInfoProdt` → `descricao`, `cProdt` → `produto`, `iCoplt` → `nomeCompletoAtivoOuInstrumento`. Não criar outra entidade para a mesma tabela. `tCurvaPrvdr.iPrvdrDados` tem FK para `tPrvdrDadoMercd.iPrvdrDados`: conferir a existência antes (404 `NAO_ENCONTRADO`) para responder com o erro certo em vez da violação de FK. O produto do provedor da curva (`cPrvdrMercd`) não é conferido contra `cProdt`: `tPrvdrDadoMercd` tem uma linha por provedor (PK em `iPrvdrDados`), e uma fonte pode ter vários produtos (a ANBIMA tem `MS` e, no futuro, `CZ`).

`idCurvaProvedor` (na mesma transação da inserção):
```sql
SELECT ISNULL(MAX(cIdtfdUnic), 0) + 1 FROM tCurvaPrvdr WITH (UPDLOCK, HOLDLOCK);
```
A consulta leva o mesmo `@QueryHints` de 60 s (`jakarta.persistence.query.timeout`); esgotado, 500 `ERRO_INTERNO`, sem gravar.

Regras: provedor inexistente → 404 `NAO_ENCONTRADO`; (curva, provedor, produto) repetido → 409 `PROVEDOR_DUPLICADO`; prioridade repetida na curva → 409 `PRIORIDADE_EM_USO`; trocar provedor não existe (excluir e incluir). `TCEN`: `tickerProvedor` = nome de curva existente, diferente da própria, sem ciclo (DFS pelos provedores da curva `TCEN` a partir da curva componente; achando a curva atual → 422 com o caminho `B → A → B`).

Avisos depois da alteração: `CURVA_SEM_ORIGEM` (nenhum provedor); `ORIGEM_INCOMPATIVEL_COM_MODELO` (a de menor prioridade não bate com o modelo nativo da configuração vigente: `PRONTA_TS_B3` = `B3`/`TS`, `NTNB_BOOTSTRAP_ANBIMA` = `ANBIMA`/`MS`, `SOFR_ZERO_BLOOMBERG` = `BLOOMBERG`/`BLC2`); `MODELO_POR_ORIGEM_SEM_PROVEDOR` (chave de `MODELOS_POR_ORIGEM` da vigente ou futura sem provedor).

`GET /curvas-mercado/provedores?provedor=&produto=&tickerProvedor=`: curvas ligadas, com código, nome e prioridade.

---

## 4. Configuração de cálculo (`ConfiguracaoCurvaService`)

Entidade `ConfgCurvaEntity` (`tConfgCurva`, `@Id @GeneratedValue(IDENTITY) cIdtfdConfg`): `cTickerIndcd`, `cMotorCalc`, `cRotnaCalc`, `cModDado`, `cVrsaoReg`, `dInicVgcia`, `dValidAte`; as demais colunas nulas.

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

Combinações: `Price` só com `PRECO`/`PONTOS`, e as outras bases de interpolação só com `TAXA`; a extrapolação `FlatForward` só com interpolador `Linear` ou `FlatForward`. Chave desconhecida, tipo errado, valor fora da lista (com caixa) ou obrigatório ausente → 422 `DADOS_INVALIDOS`, um `Detalhe` por problema. Avisos: `MODELO_NAO_NATIVO` (modelo, interpolador ou calendário fora dos nativos), `ORIGEM_INCOMPATIVEL_COM_MODELO`, `MODELO_POR_ORIGEM_SEM_PROVEDOR`.

Gravação de `cModDado`: JSON compacto, chaves na **ordem da tabela acima** (não alfabética), `EXTRAPOLACAO_*` gravadas mesmo quando `Disabled`; mais de 1.024 caracteres → 422.

### 4.2 Versões e vigência

Na mesma transação, com a curva travada:
- primeira versão: `cVrsaoReg = 1`; `inicioVigencia` ≥ `dInicVgcia` da curva (pode ser no passado);
- versão nova: `inicioVigencia` obrigatória (ausente → 422 no campo, antes de qualquer comparação de datas); `inicioVigencia` > o da última **e** ≥ `LocalDate.now()`, senão 422; fecha a última carregando a entidade da versão e mudando **só** o `dValidAte` (`inicio da nova − 1 dia`), sem `save` de um objeto novo (que anularia as colunas que não estão no modelo); `cVrsaoReg = última + 1`; nova com `dValidAte` nulo;
- exclusão: só a última e só se `inicioVigencia` > hoje; a anterior volta a `dValidAte` nulo; senão 422;
- não há alteração de versão.

Rotas: as da tabela "Rotas da configuração" da spec. `validacao` roda as regras sem gravar e devolve erros e avisos. `vigente?data=` (padrão hoje): `dInicVgcia <= data AND (dValidAte IS NULL OR dValidAte >= data)`.

---

## 5. Valores aceitos (`GET /api/v1/curvas-mercado/valores`)

**Fase 1: a rota é servida só pelo próprio serviço.** Sem chamada ao engine, sem cache, sem token, sem o aviso `VALORES_SEM_ENGINE` e sem `EnginePort` no `ValoresService`; responde 200 mesmo com o engine fora do ar.

1. Partir da tabela embutida (seção 4.1: os modelos de construção, interpoladores e calendários nativos e os valores de cada chave de `parametros`, com os rótulos em código).
2. Acrescentar os provedores de `tPrvdrDadoMercd` (pela `ProvedorEntity`: `nomeProvedor` e `descricao`) e os enums e catálogos do serviço (seção 1.3), cada valor com o `rotulo` e a `descricao` do `messages.properties`.
3. OpenAPI: `enum` em `unidade`, `dayCounterCotacao`, `compounding`, `situacao` e em cada chave de `parametros`; `modeloConstrucao`, `interpolador` e `CALENDARIO` como `string` com os nativos na descrição.

**Fase 2 (change `curves-operacao-curvas`, tarefa 1.1):** o `ValoresService` passa a chamar `valoresCadastro()` (seção 1.6) com cache de 5 minutos (cache distribuído, se o serviço já tiver um; senão, local, aceitável porque o dado é o mesmo em todas as instâncias) para acrescentar os scripts Groovy ativos; engine fora → devolve a tabela embutida com o aviso `VALORES_SEM_ENGINE`, sem falhar; e entra o teste de contrato que compara a tabela embutida com a parte fixa da resposta do engine.

---

## 6. Painel (`PainelService`, `GET /api/v1/curvas-mercado/painel`) — fase 2 (`curves-operacao-curvas`)

1. `dataBase`: a informada; sem ela, hoje se for útil no `Brazil`/`Settlement` (feriados do engine, seção 1.6), senão o dia útil anterior. Engine fora: só sábado e domingo recuam.
2. Uma chamada a `situacao(dataBase)` e uma a `feriados` por consulta. Engine fora → todas as linhas `SITUACAO_INDISPONIVEL` e aviso `ENGINE_INDISPONIVEL`; nunca falha.
3. Por curva com código (ativas ou não), montar a linha com os campos da tabela "Colunas de cada linha" da spec: curva, origem principal, `origensSecundarias` (provedores da curva de prioridade maior, com o modelo de `MODELOS_POR_ORIGEM` ou `modeloConstrucao`), configuração vigente na data, `ultimaDataPublicada`/`calculadoPor` (`dBaseReft`/`cUsuarCalc`), `quantidadePontos` e `hashPontos` (de `tDadoVertcCurva`), `insumo`, `interpolada` e `conferencia` (do engine).
4. Situação: a **primeira** regra da tabela "Situação na data-base" da spec que se aplica, nesta ordem: `NAO_E_DIA_UTIL`, `IGNORADA`, `SITUACAO_INDISPONIVEL`, `INTERPOLADA_DESATUALIZADA`, `CONSTRUIDA`, `DIVERGENTE_DA_FONTE`, `AGUARDANDO_COMPONENTES`, `AGUARDANDO_CARGA`, `COM_ERRO`, `NAO_CONSTRUIDA`, com `motivo` e `atencao` da tabela.
5. `atrasada`: situação `AGUARDANDO_CARGA` ou `NAO_CONSTRUIDA` e `dataBase.isBefore(LocalDate.now())`; na data de hoje, sempre `false`. Sem configuração de horário.
6. Contadores sobre todas as curvas antes dos filtros; depois aplicar `situacao`, `provedor`, `nome`, `somenteAtencao`; ordenar por código.

---

## 7. Planilha do cadastro (`CadastroPlanilhaService`)

### 7.1 Estrutura e exportação

Abas e colunas exatamente como na spec `cadastro-curvas-planilha` (`Curvas`, `Provedores`, `Configuracoes`, `Valores`). Na aba `Configuracoes`, uma coluna por chave de parâmetro, na ordem da tabela 4.1; `MODELOS_POR_ORIGEM` como texto `B3/TS=PRONTA_TS_B3;...` em ordem alfabética da chave. Datas: célula de data `dd/mm/yyyy`; números: célula numérica. Listas suspensas (`DataValidationHelper.createFormulaListConstraint` apontando para intervalos da aba `Valores`): restritivas em `Unidade`, `DayCounterCotacao`, `Compounding`, `Situacao`, `Provedor` e parâmetros com lista; só com aviso (`setErrorStyle(WARNING)`) em `ModeloConstrucao`, `Interpolador` e `CALENDARIO`. Arquivo `cadastro-curvas_{AAAAMMDDHHmmss}.xlsx`.

### 7.2 Importação (`modo=SIMULACAO|APLICACAO`, `formato=json|xlsx`)

1. Ler as abas (até 5 MB, 1.000 curvas). Data em texto `dd/mm/aaaa` ou `aaaa-mm-dd`; número em texto com vírgula **ou** ponto, sem milhar; os dois juntos → erro na célula.
2. Para cada curva da aba `Curvas`, montar o **estado desejado** (curva, provedores da curva, versões) e comparar com o banco:
   - curva nova → `INCLUSAO`; existente com campo diferente → `ALTERACAO` (inclusive código);
   - provedores da curva por (`Provedor`, `Produto`): novo → inclusão; mudou `TickerProvedor`/`Prioridade` → alteração; ausente da aba → exclusão;
   - versões: `Versao` preenchida tem de ser igual à existente em tudo (senão erro "versões existentes não se alteram"); `Versao` vazia → versão nova (regras da seção 4.2, em ordem de `InicioVigencia`); última versão futura ausente → exclusão;
   - linha de `Provedores`/`Configuracoes` de curva fora da aba `Curvas` → erro.
3. Todas as regras das seções 2, 3 e 4 valem linha a linha.
4. `SIMULACAO`: nada gravado, sem trava. `APLICACAO`: uma transação; qualquer erro → 422 com os mesmos erros e nada aplicado; sucesso → `CADASTRO_ALTERADO` com o mesmo `idLote` (UUID) por mudança.
5. `formato=xlsx`: a planilha enviada com a coluna `Resultado` em cada aba e a aba `Resumo`.

Exportar e importar sem editar MUST dar zero mudanças e nenhuma escrita.

---

## 8. Vértices: consulta e exclusão (`DadoVerticeCurvaService`)

Entidades: `DadoVertcCurvaEntity` (`tDadoVertcCurva`, `@IdClass` com `dBaseReft`, `cTickerIndcd`, `dVertcReft`; `cDiaUtil`, `vFatorDia`, `vFatorAcum`, `cQtdDiaPer`, `cQtdDiaReft`, `vPrecoTx`) e `DadoCurvaEntity` (`tDadoCurva`, só para o `DELETE`).

- `GET .../vertices?de=&ate=` (até 366 dias): datas-base com vértices, quantidade e `hashPontos` de cada uma.
- `GET .../vertices/{dataBase}`: vértices (`data` = `dVertcReft`, `valor` = `vPrecoTx`, `diasUteis` = `cDiaUtil` ou nulo) e `hashPontos`.
- `DELETE .../vertices/{dataBase}`: trava (seção 10.2), apaga `tDadoVertcCurva` e `tDadoCurva` da curva e data, log `VERTICES_EDITADOS` (`EXCLUSAO`).

`hashPontos` (igual ao engine):
```java
String texto = pontos.stream().sorted(comparing(Vertice::data))
  .map(p -> p.data() + ";" + (p.valor().signum() == 0 ? "0" : p.valor().stripTrailingZeros().toPlainString()))
  .collect(joining("\n"));
return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(texto.getBytes(UTF_8)));
```

---

## 9. Planilha de vértices (`DadoVerticeCurvaPlanilhaService`)

Aba única `Vertices`, colunas `Curva`, `DataBase`, `DataVertice`, `Valor`, `DiasUteis`, ordenadas por curva, data-base e data. `Valor`: célula numérica se tiver até 15 dígitos significativos (`v.precision() <= 15`), senão texto com vírgula decimal. `DiasUteis`: `cDiaUtil` gravado, célula vazia se nulo. Exportação: até 50 códigos, 366 dias, 100.000 vértices (acima → 400); arquivo `pontos-curvas_{AAAAMMDDHHmmss}.xlsx`.

Importação: planilha sem a coluna `DiasUteis` é aceita como vazia; colunas extras ignoradas. Cada par (`Curva`, `DataBase`) é a lista completa, com o mesmo efeito do `PUT` (seção 10). `APLICACAO`: uma transação, travas em ordem alfabética de nome, 120 s; recusa por consistência de banco em qualquer par → 422 e nada aplicado. Depois do commit, `regravarInterpolada` por par presente; falha → aviso `INTERPOLADA_DESATUALIZADA` nas linhas do par. `formato=xlsx`: colunas `Resultado` e `ValorGravado`, abas `Resumo` e `Exclusoes`.

---

## 10. Edição manual dos vértices (`PUT .../vertices/{dataBase}`)

### 10.1 Validação (recusa só por consistência de banco → 422 `VERTICES_INVALIDOS`, um `Detalhe` por vértice)

Lista vazia; vértice sem data ou valor; data inválida; valor não decimal; `diasUteis` não inteiro; datas repetidas; valor arredondado que não cabe em `DECIMAL(28,12)` (mais de 16 dígitos inteiros), ou, sem configuração vigente, com mais de 12 casas. Curva inexistente → 404 `NAO_ENCONTRADO`.

### 10.2 Gravação (uma transação; até 60 s para obter a trava)

Os 60 s são só para obter a trava, no próprio comando: `query.setHint("jakarta.persistence.query.timeout", 60000)` (vai ao JDBC como `Statement.setQueryTimeout` e não fica na conexão do pool). O estouro desse comando (`jakarta.persistence.QueryTimeoutException`) responde `ERRO_INTERNO` com a mensagem "trava da curva não obtida em 60 segundos" (a spec `vertices-curva-manual` não tem código próprio para isso); outra falha de banco, `ERRO_INTERNO` com a própria mensagem. **Proibido** `SET LOCK_TIMEOUT` e `@Transactional(timeout = ...)` (a mesma regra do engine, spec `curve-engine-resilience`).

```sql
SELECT cTickerIndcd FROM tCurvaMercd WITH (UPDLOCK, ROWLOCK) WHERE cTickerIndcd = ?;     -- a mesma trava do engine; tempo limite do comando: 60 s
SELECT dVertcReft, vPrecoTx, cDiaUtil FROM tDadoVertcCurva WHERE cTickerIndcd = ? AND dBaseReft = ?;
```
1. Arredondar cada valor pela configuração vigente na data-base (`CASAS_DECIMAIS`, `MODO_ARREDONDAMENTO`); mudou → aviso `VALOR_ARREDONDADO` (enviado e gravado). Sem configuração → grava como enviado, aviso `SEM_CONFIGURACAO`.
2. Comparar com o gravado: um vértice muda se o valor (`compareTo`) ou os `diasUteis` mudarem. Nada mudou → aviso `SEM_MUDANCA`, sem escrita e sem log (mas segue para o passo 5).
3. Escrever só a diferença em `tDadoVertcCurva`: `DELETE` dos ausentes da lista; para cada novo ou alterado, gravar `dBaseReft`, `cTickerIndcd` (nome), `dVertcReft`, `vPrecoTx` (arredondado), `cDiaUtil` = `diasUteis` (nulo se não informado), `cQtdDiaPer` = dias corridos da data-base ao vértice, `cQtdDiaReft` = 30/360 (Bond Basis: `d1 = min(dia1,30)`; `d2 = (dia2 == 31 && d1 == 30) ? 30 : dia2`; `360·Δano + 30·Δmês + (d2 − d1)`), `vFatorAcum` e `vFatorDia` nulos. Vértices que não mudaram: intocados.
4. Reler os vértices da data e conferir o `hashPontos` contra o da lista arredondada; diferente → desfaz tudo, 500 `ERRO_INTERNO`.
5. Depois do commit (também com `SEM_MUDANCA`): `regravarInterpolada(codigo, dataBase)`; falha → aviso `INTERPOLADA_DESATUALIZADA` com o motivo. A edição já está gravada.
6. Log `VERTICES_EDITADOS` (seção 10.5) se algo mudou.

Nunca escrever `dBaseReft`/`cUsuarCalc` de `tCurvaMercd`, nem `tDadoCurva` (exceto o `DELETE` da seção 8). Sem conferência de versão: quem salva por último vence.

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
| `DIAS_UTEIS_FORA_DE_ORDEM` | em ordem de data, `diasUteis` ≤ o de um vértice anterior com `diasUteis` |

Taxa negativa: sem aviso.

### 10.4 Resposta

200 com os vértices relidos (valor como string na escala gravada), `hashPontos` novo e `avisos`.

### 10.5 `VERTICES_EDITADOS`

Nível `AVISO`: `correlationId`, `usuario`, `codigo`, `nome`, `dataBase`, `operacao` (`SUBSTITUICAO`/`EXCLUSAO`), `origem` (`API`/`PLANILHA`), `idLote`, quantidade de vértices antes e depois, `hashPontos` antes e depois, instante de Brasília. Sem registro de auditoria.

---

## 11. Vértices brutos dos provedores (`/dados-mercado`, spec `vertices-brutos-provedor`)

Os CRUDs dos três provedores **já existem**: reaproveite os serviços, entidades e repositórios deles (`BtrsCurvaPrimr*`, `AnbmaCurvaPrimr*`, `BbergCurvaPrimr*`); as classes abaixo são a camada que a tela usa. Onde houver `Linha` no nome, trocar por `Vertice`. As rotas atuais desses CRUDs podem ficar.

### 11.1 Contrato

Base da curves: `{urlAPI}/api/v1/dados-mercado` (proxy `/api`). Upload: `{urlAPI}/api/v1/cargas/upload` (proxy `/api/v1/cargas` → bff).

| Ação | Chamada | Tempo front |
|---|---|---|
| Tickers | `GET /dados-mercado/{provedor}/tickers` | 3 s |
| Consultar | `GET /dados-mercado/{provedor}?tickerProvedor=PRE&dataBase=2026-09-14` | 3 s |
| Incluir / alterar / excluir vértice | `POST /dados-mercado/{provedor}/{codigo}/{dataBase}/vertices`, `PUT .../vertices/{id}`, `DELETE .../vertices/{id}` | 3 s |
| Excluir todos da data | `DELETE /dados-mercado/{provedor}/{codigo}/{dataBase}` | 3 s |
| Baixar planilha | `GET .../planilha` (`blob`) | 3 s |
| Importar planilha | `POST .../planilha?modo=SIMULACAO\|APLICACAO` (`FormData`: `arquivo`) | 70 s |
| Enviar arquivo da fonte | `POST /api/v1/cargas/upload` (`FormData`: `fonte`, `arquivo`) | 130 s |

`{provedor}` = `B3`, `ANBIMA` ou `BLOOMBERG`. Campos do vértice por provedor (decimais sempre string):

| Provedor | Corpo do POST/PUT | Só leitura na resposta |
|---|---|---|
| `B3` | `{ "diasCorridos": 112, "diasUteis": 75, "valor": "13.589", "fatorAcumulado": null, "fatorDia": null }` | `id`, `dataVertice` |
| `ANBIMA` | `{ "prazoDiasCorridos": 229, "taxa": "5.5415" }` | `id`, `vencimento` |
| `BLOOMBERG` | `{ "ticker": "S0490Z 15M BLC2 Curncy", "valor": "4.31" }` | `id` |

```jsonc
// GET /dados-mercado/B3/tickers
[ { "tickerProvedor": "PRE", "produto": "TS", "curvas": [ { "codigo": "PRE", "nome": "DIxPRE" } ] } ]
// GET /dados-mercado/B3?tickerProvedor=PRE&dataBase=2026-09-14
{ "provedor": "B3", "tickerProvedor": "PRE", "dataBase": "2026-09-14",
  "curvas": [ { "codigo": "PRE", "nome": "DIxPRE", "curvaConstruida": true,
                "avisos": [ { "codigo": "CURVA_JA_CONSTRUIDA", "mensagem": "...", "detalhes": [] } ],
                "vertices": [ { "id": 101, "dataVertice": "2026-09-15", "diasCorridos": 1, "diasUteis": 1,
                                "valor": "13.900000000000", "fatorAcumulado": null, "fatorDia": null } ] } ] }
// POST/PUT vértice (201/200): o vértice gravado + "avisos" da curva na data
// planilha (200): { "modo": "SIMULACAO", "mudancas": [ { "linha": 3, "tipo": "ALTERACAO", "id": 101 } ],
//                   "erros": [ { "linha": 12, "coluna": "valor", "motivo": "..." } ], "avisos": [],
//                   "contagens": { "INCLUSAO": 1, "ALTERACAO": 1, "EXCLUSAO": 0, "SEM_MUDANCA": 276 } }
// upload (200): { "idCarga": "B3-TS-20260914-46a249c60bec", "dataBase": "2026-09-14", "origem": "UPLOAD",
//                 "verticesPorCodigo": { "PRE": 278 }, "usuario": "maria", "correlationId": "..." }
// erro: curves no formato de erro dela (o do CRUD de provedores), com detalhes [ { campo, linha, valor, motivo } ];
//       upload pelo bff no formato do processor { codigoErro, mensagem, correlationId }. O front lê os dois pelo lerErro.
```


### 11.2 Estratégia por provedor

```java
// application/service/verticebruto/VerticeBrutoProvedor.java
public interface VerticeBrutoProvedor {
    String provedor();                                    // "B3" | "ANBIMA" | "BLOOMBERG" (= iPrvdrDados)
    List<VerticeBruto> listar(String nomeCurva, LocalDate dataBase);   // já na ordem da spec
    VerticeBruto incluir(String nomeCurva, LocalDate dataBase, Map<String, Object> campos, int id);
    VerticeBruto alterar(String nomeCurva, LocalDate dataBase, int id, Map<String, Object> campos);
    void excluir(String nomeCurva, LocalDate dataBase, Integer id);   // id nulo = todos da data
    int proximoId();                                      // MAX(cIdtfdUnic) + 1 com UPDLOCK, HOLDLOCK
    List<DetalheErro> validar(Map<String, Object> campos);            // só regra de coluna → 422
    List<AvisoCurva> avisos(List<VerticeBruto> verticesDaData);       // avisos próprios do provedor
    List<String> colunasPlanilha();                       // "id" + campos editáveis
}
public record VerticeBruto(int id, Map<String, Object> campos) {}     // campos em ordem, decimais como String
```

Uma implementação por provedor (`VerticeBrutoB3`, `VerticeBrutoAnbima`, `VerticeBrutoBloomberg`) sobre as entidades e repositórios que já existem. Regras de coluna (422 `DADOS_INVALIDOS`): obrigatórios (B3 `diasCorridos`, `diasUteis`, `valor`; ANBIMA `prazoDiasCorridos`; Bloomberg `ticker`, `valor`), inteiro em `INT`, `prazoDiasCorridos` sem fração, decimal que cabe em `DECIMAL(28,12)` (16 inteiros, 12 casas) ou `DECIMAL(28,16)` nos fatores (`new BigDecimal(texto)`, `precision() - scale()` e `scale()`), `ticker` até 50. Avisos próprios: B3 `DIAS_CORRIDOS_NAO_POSITIVO`, `DIAS_UTEIS_INCOERENTES`, `DIAS_CORRIDOS_REPETIDOS`; ANBIMA `PRAZO_NAO_POSITIVO`, `PRAZO_REPETIDO`, `TAXA_AUSENTE`; Bloomberg `TICKER_REPETIDO`.

### 11.3 Serviço comum (`VerticeBrutoService`)

- `Map<String, VerticeBrutoProvedor>` montado das implementações; provedor fora → `InvalidInputException(PARAMETRO_INVALIDO)`.
- Curva: pelo código, com `CurvaMercdRepositoryPort` (sem código ou inexistente → 404 `NAO_ENCONTRADO`); a gravação usa o **nome** (`cTickerIndcd`).
- Gravação em `@Transactional`: `SELECT cTickerIndcd FROM tCurvaMercd WITH (UPDLOCK, ROWLOCK) WHERE cTickerIndcd = ?` (60 s) → `validar` → `proximoId` → `incluir`/`alterar`/`excluir` → `listar` → avisos.
- Avisos comuns: `CURVA_SEM_PROVEDOR` (`SELECT COUNT(*) FROM tCurvaPrvdr WHERE cTickerIndcd = ? AND iPrvdrDados = ?` = 0); `CURVA_JA_CONSTRUIDA` (`SELECT COUNT(*) FROM tDadoVertcCurva WHERE cTickerIndcd = ? AND dBaseReft = ?` > 0).
- Tickers: `SELECT cTickerPrvdr, cPrvdrMercd, cTickerIndcd FROM tCurvaPrvdr WHERE iPrvdrDados = ?` agrupado por código na fonte, com código e nome de cada curva de `tCurvaMercd`; `trim()` nas colunas `CHAR`.
- Nunca escrever em `tCurvaMercd`, `tCurvaPrvdr`, `tDadoVertcCurva`, `tDadoCurva`; nunca chamar o engine.

### 11.4 Planilha (POI, já no projeto) — parte 2 (`curves-operacao-curvas`), fora da v1

- Exportar: aba `Vertices`, linha 1 = `colunasPlanilha()`, uma linha por vértice; números como célula numérica, texto como célula de texto.
- Importar: ler a aba `Vertices` pelo nome das colunas da linha 1 (faltou coluna → 422). Linha com `id` gravado → `ALTERACAO` ou `SEM_MUDANCA`; sem `id` → `INCLUSAO`; `id` gravado que não aparece → `EXCLUSAO`; `id` de outra curva ou data → erro da linha. `SIMULACAO` não grava; `APLICACAO` com qualquer erro → 422 sem gravar; sem erro, tudo numa transação com a trava. `formato=xlsx` devolve a planilha enviada com a coluna `Resultado`.

### 11.5 Controller

`DadosMercadoAPI` + `DadosMercadoController` (`@RequestMapping("/api/v1/dados-mercado")`), rotas da spec; o corpo do vértice entra como `Map<String, Object>` e sai como o `VerticeBruto` achatado (`id` + campos + só leitura) mais `avisos`.

### 11.6 Testes

| Classe | Cobre |
|---|---|
| `VerticeBrutoServiceTest` | gravação, 422 por coluna, avisos comuns e por provedor, tickers, código com duas curvas (portas simuladas) |
| `PlanilhaVerticeBrutoTest` | planilhas montadas no teste: alteração, inclusão, exclusão, sem mudança, erro de linha, aba ausente |
| `DadosMercadoRotasTest` | MockMvc `standaloneSetup`: 400 provedor, 404, 422 com `detalhes`, 201 com avisos |

## 12. Remover e conferir

Nada a remover no serviço (é novo ou é do outro dev). Conferir que nenhum código do curves grava `dBaseReft`, `cUsuarCalc`, `tDadoCurva` (fora do `DELETE`) nem usa Blob.

### 12.1 Conferência da primeira parte (tarefas 1 a 5), antes do commit

- **Só o que é das tarefas 1 a 5b:** curva de mercado, provedores da curva, configuração de cálculo com valores aceitos, vértices brutos dos três provedores (seção 11), repasse ao engine (seção 16) e a base comum (erros, correlação, log `CADASTRO_ALTERADO` e `CURVA_PRIMARIA_EDITADA`, contrato de tipos, catálogo de enums). Os enums de painel e de vértices entram porque o catálogo de `/valores` lista todos (tarefa 1.2b); o evento `VERTICES_EDITADOS`, as rotas de vértices, painel e planilha ficam para a change `curves-operacao-curvas`.
- **Camadas:** cada classe no lugar da tabela "Onde fica cada tipo de classe" (seção 0).
- **`pom.xml`:** sem Resource Server nem `azure-identity` enquanto a autenticação estiver adiada; `poi-ooxml` só se a auditoria em `xlsx` (tarefa 2.2) entrar agora.
- **Jackson:** a configuração de `BigDecimal` como texto está no mapper que o Spring MVC usa (Jackson 3); conferido por um teste de rota.
- **Um serviço de valores só:** nada de dois serviços para `/valores`.
- **Sem engine:** nenhuma classe `EnginePort`, `EngineHttpClient` nem `RespostaEngine`, nenhum bloco `curves.engine` no `application.yml` e nenhuma chamada HTTP ao engine em `src/main` (busca por `EnginePort`, `EngineHttpClient`, `valores-cadastro` e `VALORES_SEM_ENGINE` sem resultado).
- **Arquivos gerados fora do código** (cópia do script do banco, documentos `.md` do assistente na raiz): não vão no commit sem decisão do time.


## 13. Ordem de implementação (uma tarefa de `tasks.md` por vez; `mvn -q compile` ao fim de cada uma)

1. Seção 1 (tarefas 1.x), sem a seção 1.6 (cliente do engine, fase 2).
2. Seção 2 (2.x), seção 3 (3.x), seção 4 (4.x), seção 5 (4.4, só a parte local). Ao fim: CRUD completo de curva de mercado, provedores e configuração de cálculo.
3. Seção 11, curva primária B3 (tarefas 5.x).
4. Seção 14 (testes) e seção 15 (homologação) da primeira parte.

Os passos seguintes são da segunda parte, a change `curves-operacao-curvas` (as tarefas 6.x a 10.x só existem no `tasks.md` dela):

5. Seção 1.6, cliente do engine (1.1 da change da segunda parte), com a consulta ao engine em `/valores` (seção 5, fase 2); seção 7, planilha do cadastro (6.x).
6. Seção 6, painel (7.1).
7. Seções 8 e 10, vértices (8.x), seção 9, planilha de vértices (9.x), origens e dias úteis (10.x).
8. Seção 14 (testes) e seção 15 (homologação) da segunda parte.

## 14. Testes (ao final)

Ordem: **verificar, adaptar, criar, rodar**. Só `spring-boot-starter-test` (JUnit 5 com testes parametrizados, Mockito, AssertJ, MockMvc); sem ArchUnit. **Sem banco nem engine reais**: portas de saída (repositórios e, na fase 2, `EnginePort`) com Mockito. Fuso dos testes como no `main`: `TimeZoneExtension` registrada por autodetecção do JUnit (`junit-platform.properties` + `META-INF/services`), com `TimeZone.setDefault` no `beforeAll`. Quem depende de "hoje" (vigência, atraso do painel) recebe a data ou a hora por parâmetro do método testado.

### 14.1 Verificar

`mvn -q compile` limpo. Buscas sem resultado em `src/main`: `class Relogio`, `NOLOCK`, `synchronized`, `BlobServiceClient`, `double`/`Double` em valor de curva, gravação de `dBaseReft` ou `cUsuarCalc`; e no pacote `domain` do serviço (no poc, `src/main/java/br/com/poc/domain`; no real, o do pacote raiz): `import org.springframework`, `import jakarta`, `import com.fasterxml.jackson.databind`, `import tools.jackson`, `import org.apache.poi` e imports de `adapter` ou `application` do próprio serviço.

### 14.2 Criar

| Teste | Casos |
|---|---|
| `CurvaMercadoServiceTest` | cenários da spec `cadastro-curva-mercado` (criação da DIxPRE, nome que colide, preço com cotação, renomear, inativação, duas alterações seguidas e a última vence, enum em caixa errada, nome em uso por curva sem código → 409, `inicioVigencia` ausente → 422 sem comparar datas) |
| `CurvaProvedorServiceTest` | cenários da spec `provedor-curva` (TaxaSwap, provedor inexistente, `TCEN`, ciclo de 2 e de 3 curvas, último provedor excluído, troca do provedor no `PUT` → 422, avisos); o SQL do `MAX + 1` com `UPDLOCK, HOLDLOCK` enviado ao repositório |
| `ValidadorParametrosTest` | um caso por regra da tabela 4.1 (os mesmos da spec do engine); ordem das chaves em `cModDado`; 1.024 caracteres |
| `ConfiguracaoCurvaServiceTest` | cenários da spec `configuracao-calculo-curva` (troca a partir de amanhã, correção retroativa, desistência, vigente em data antiga, unidade que invalida, `MODELOS_POR_ORIGEM` com e sem provedor) |
| `ValoresServiceTest` | fase 1: tabela embutida, provedores de `tPrvdrDadoMercd` e catálogos do serviço, sem nenhuma chamada ao engine; todo valor com `rotulo` e `descricao`. Fase 2 (change `curves-operacao-curvas`): engine respondendo; engine fora com `VALORES_SEM_ENGINE`; tabela embutida igual à parte fixa da resposta do engine (resposta gravada em `src/test/resources/valores-engine.json`) |
| `CadastroPlanilhaServiceTest` | exportar e importar sem editar → zero mudanças; 30 prioridades trocadas; provedor removido; versão existente editada → erro; erro impede o lote; vírgula e ponto juntos → erro; `MODELOS_POR_ORIGEM` malformado |
| `PainelServiceTest` (fase 2) | todos os cenários da spec `painel-curvas`, uma linha por situação e por `motivo`, engine fora, feriado americano, carga atrasada (ontem) e carga de hoje não atrasada, origem secundária |
| `DadoVerticeCurvaServiceTest` | vetores da seção 0.2 (`hashPontos`, arredondamento, `diasUteis` 76 com 30/360 = 110); cenários da spec `vertices-curva-manual` (um valor, vértice retirado, casas a mais, lista igual sem escrita, conferência divergente desfaz, data sem construção, engine fora com `INTERPOLADA_DESATUALIZADA`, feriado, sábado, repetida); regravação chamada também com `SEM_MUDANCA` |
| `VerticeBrutoServiceTest` (seção 11.6) | cenários da spec `vertices-brutos-provedor` (listagem da carga, consulta da PRE, linha de outra data → 404, repetidos, valor com 13 casas → 422, correção depois da construção, data sem carga digitada, `de` maior que `ate` → 400, trava não obtida em 60 s → 500 sem gravar); nenhuma chamada ao engine; `MAX + 1` com `UPDLOCK, HOLDLOCK` enviado ao repositório; log sem o evento quando o commit falha |
| `DadoVerticeCurvaPlanilhaServiceTest` | cenários da spec `vertices-curva-planilha`; planilha sem `DiasUteis`; `DiasUteis` apagado → `ALTERACAO`; valor da `PTX` 56,3772259 numérico |
| `ApiContratoTest` (MockMvc) | um teste por rota com 401, 403 e papel certo (**adiado** com a autenticação: por ora só a rota com sucesso); um por `codigoErro`; `X-Correlation-Id` em sucesso, erro e `xlsx`; decimais como string |
| `FusoTest` | `OffsetDateTime.ofInstant(Instant.parse("2026-09-15T01:30:00Z"), ZoneId.systemDefault())` → `2026-09-14T22:30-03:00`; subida recusada com outro fuso |

### 14.3 Rodar

`mvn verify` passa sem banco nem engine. Teste que falha e reflete a spec → corrigir o código.

## 15. Conferido na homologação (não é teste automatizado)

Com o banco e o Entra ID do projeto (o curves não depende do engine; o engine só é usado nas conferências que o citam):
- `GET /api/v1/curvas-mercado/valores` responde 200 com a tabela embutida, os provedores e os catálogos, com o engine desligado;
- as 7 curvas do `exemplo-cadastro-7-curvas.txt` cadastradas pela API e pela planilha, e a simulação do engine sem `CADASTRO_INVALIDO` em nenhuma;
- duas inclusões de provedor da curva simultâneas com `idCurvaProvedor` diferentes;
- edição de vértices enquanto o engine constrói a mesma curva: espera e grava por cima;
- tarefa 11.2 do `tasks.md` da change `curves-operacao-curvas` (construir, editar, interpolar, carga sem sobrescrever, painel `DIVERGENTE_DA_FONTE`, recálculo forçado);
- `openspec validate curves-cadastro-curvas --strict`.

---

## 16. Ações e consultas via engine (spec `acoes-curva-mercado`)

Única chamada da primeira parte ao engine: **repasse** para a tela Curvas. Sem cache, sem `/valores-cadastro`, sem `EnginePort` da fase 2 (seção 1.6). A rota `GET /api/v1/curvas/{codigo}/{dataBase}/pontos` do engine vem da change `fed-curvas-mercado` (tarefa 1.1); antes dela, o repasse de pontos responde o 404 do engine.

### 16.1 Contrato ponta a ponta

Front → curves (`{urlAPI}/api/v1/curvas-mercado/{codigo}/{dataBase}/...`, proxy `/api`) → engine (`{curves.engine.url}/api/v1/curvas/{codigo}/{dataBase}/...`). No 2xx, a curves repassa **status e corpo sem alterar**; no 4xx, mantém status, código, mensagem e `detalhes` do engine no **formato de erro da curves**; tempo esgotado, rede ou 5xx viram `503 ENGINE_INDISPONIVEL`.

| Ação | Front → curves | curves → engine | Tempo front / curves |
|---|---|---|---|
| Construir | `POST .../construcao` | `POST .../construcao` | 130 s / 120 s |
| Recalcular | `POST .../construcao?forcarRecalculo=true` | idem | 130 s / 120 s |
| Origem secundária | `POST .../construcao?forcarRecalculo=true&fonte=B3&produto=TS` | idem | 130 s / 120 s |
| Regravar interpolada | `POST .../interpolada` | `POST .../interpolada` | 70 s / 60 s |
| Vértices | `GET .../vertices` | `GET /api/v1/curvas/{codigo}/{dataBase}` | 40 s / 30 s |
| Pontos | `GET .../pontos?de=&ate=` | `GET .../pontos?de=&ate=` | 40 s / 30 s |
| Interpolar | `GET .../interpolacao?du=21&du=252&data=2027-01-04` | idem (repetir os parâmetros como vieram) | 40 s / 30 s |

Cabeçalhos curves → engine: `X-Correlation-Id` (o recebido), `X-Usuario` (o recebido, quando vier; sem autenticação na v0 e na v1), **nunca** `Authorization`.

Respostas que o front lê (só estes campos; o resto é ignorado):

```jsonc
// construcao (200)
{ "codigo": "PRE", "nome": "DIxPRE", "dataBase": "2026-09-14", "situacao": "RECONSTRUIDA",
  "quantidadePontos": 278, "hashPontos": "7c49...", "duracaoMs": 1840,
  "avisos": [ { "codigo": "ORIGEM_SECUNDARIA", "mensagem": "..." } ] }
// vertices (200) — "pontos" é o nome do campo no engine; na tela, "Vértices"
{ "codigo": "PRE", "dataBase": "2026-09-14", "hashPontos": "7c49...", "avisos": [],
  "pontos": [ { "data": "2026-09-15", "valor": "13.9000000", "diasUteis": 1, "diasCorridos": 1, "dias30360": 1,
                "fatorAcumulado": "1.0005166043641946", "fatorDiario": "1.0005166043641946",
                "recalculado": { "diasUteis": 1, "diasCorridos": 1, "dias30360": 1,
                                 "fatorAcumulado": "1.0005166043641946", "fatorDiario": "1.0005166043641946" } } ] }
// pontos (200) — rota nova do engine (change fed-curvas-mercado, tarefa 1.1)
{ "codigo": "PRE", "dataBase": "2026-09-14", "total": 12390, "hashPontos": "7c49...", "avisos": [],
  "pontos": [ { "data": "2026-09-15", "valor": "13.9000000" } ] }
// interpolacao (200)
{ "prazos": [ { "pedido": "21", "data": "2026-10-14", "du": 21, "dc": 30, "valor": "13.8123456",
                "classificacao": "INTERPOLADO", "fatorAcumulado": "...", "fatorDiario": "..." } ] }
// erro: sempre no formato de erro da curves (o mesmo do CRUD de provedores); o front lê pelo helper lerErro
```


### 16.2 Porta e adaptador do engine

```java
// application/port/out/EnginePort.java  (a fase 2 da curves acrescenta métodos nesta mesma porta)
public interface EnginePort {
    RespostaEngine construir(String codigo, LocalDate dataBase, boolean forcarRecalculo, String fonte, String produto, String usuario, String correlationId);
    RespostaEngine regravarInterpolada(String codigo, LocalDate dataBase, String usuario, String correlationId);
    RespostaEngine consultarVertices(String codigo, LocalDate dataBase, String correlationId);
    RespostaEngine consultarPontos(String codigo, LocalDate dataBase, LocalDate de, LocalDate ate, String correlationId);
    RespostaEngine interpolar(String codigo, LocalDate dataBase, String queryString, String correlationId);
    record RespostaEngine(int status, String corpoJson) {}
}
```

```java
// adapter/out/client/engine/EngineHttpClient.java  (@Component, implements EnginePort)
private final HttpClient http = HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1)
        .connectTimeout(Duration.ofSeconds(5)).build();
@Value("${curves.engine.url}") private String baseUrl;          // sem valor padrão: falta → não sobe

private RespostaEngine enviar(String metodo, String caminho, Duration limite, String usuario, String correlationId) {
    var b = HttpRequest.newBuilder(URI.create(baseUrl + "/api/v1/curvas/" + caminho)).timeout(limite)
            .header("Accept", "application/json").header("X-Correlation-Id", correlationId)
            .method(metodo, HttpRequest.BodyPublishers.noBody());
    if (usuario != null) b.header("X-Usuario", usuario);
    try {
        var r = http.send(b.build(), HttpResponse.BodyHandlers.ofString());
        if (r.statusCode() >= 500) throw new EngineIndisponivelException();
        return new RespostaEngine(r.statusCode(), r.body());
    } catch (IOException e) { throw new EngineIndisponivelException(); }   // inclui HttpTimeoutException
      catch (InterruptedException e) { Thread.currentThread().interrupt(); throw new EngineIndisponivelException(); }
}
// construir: POST "{codigo}/{dataBase}/construcao?forcarRecalculo=..&fonte=..&produto=.." (só os presentes), 120 s
// regravarInterpolada: POST ".../interpolada", 60 s · consultarVertices: GET "{codigo}/{dataBase}", 30 s
// consultarPontos: GET ".../pontos?de=..&ate=..", 30 s · interpolar: GET ".../interpolacao?" + queryString, 30 s
// codigo sempre com URLEncoder.encode(codigo, UTF_8)
```

`EngineIndisponivelException` (nova, no molde da `ServiceUnavailableException` da curves) com o código `ENGINE_INDISPONIVEL` (503) no enum de códigos de erro, um método no handler que responde as rotas e o texto em `messages.properties` ("O engine não respondeu. Tente de novo em instantes.").

### 16.3 Serviço e controller

- `CurvaMercadoAcoesService`: confere a curva pelo `CurvaMercdRepositoryPort` (404 `NAO_ENCONTRADO`); valida (400 `PARAMETRO_INVALIDO`): `fonte` e `produto` juntos ou nenhum; `de <= ate`; interpolação com ao menos um `du` ou `data`. Depois chama o `EnginePort`.
- `CurvaMercadoAcoesController` + `CurvaMercadoAcoesAPI` (`@RequestMapping("/api/v1/curvas-mercado/{codigo}/{dataBase}")`, `@DateTimeFormat(iso = DATE) LocalDate dataBase`): no 2xx, cada método devolve **o corpo do engine como veio**; no 4xx, lança a exceção de negócio da curves com o status, o código, a mensagem e os `detalhes` lidos do Problem Details do engine (campos `code` ou `codigoErro`, `detail`, `detalhes`), para o handler montar o formato da curves:

```java
private ResponseEntity<String> repassar(RespostaEngine r) {
    if (r.status() >= 400) throw ErroDoEngine.de(r, jsonMapper);   // vira o formato de erro da curves
    return ResponseEntity.status(r.status()).contentType(MediaType.APPLICATION_JSON).body(r.corpoJson());
}
```

- Interpolação: passar a `request.getQueryString()` adiante sem remontar (mantém `du` e `data` repetidos e na ordem).
- `application.yml`: `curves.engine.url: ${CURVES_ENGINE_URL}`.

### 16.4 Testes

| Classe | Cobre |
|---|---|
| `CurvaMercadoAcoesServiceTest` | 404, os 400, repasse do status, `EngineIndisponivelException` → 503 |
| `CurvaMercadoAcoesRotasTest` | MockMvc `standaloneSetup`: corpo e status do engine repassados no 2xx, 422 do engine no formato da curves, query string da interpolação intacta |

## Extras não finalizados (fora das tarefas e das specs)

> **Não implementar agora; retomar se o número de usuários crescer.** O que está abaixo não faz parte de nenhuma tarefa nem de nenhuma spec das duas changes do curves.

### ETag e If-Match (concorrência otimista)

**Aviso:** a primeira parte do curves já foi enviada ao Copilot **com** o `ETag` e o `If-Match` na especificação, então o código gerado pode já tê-los. A remoção é uma **correção a aplicar no reenvio dos arquivos** (tarefa 1.3 retirada; classes `EtagCurvaMercado` e `CurvaCanonicoJsonSerializer`; códigos `ALTERADO_POR_OUTRO` e `IF_MATCH_AUSENTE` do enum de erros; cabeçalho `ETag` das respostas; exigência de `If-Match` nas escritas; teste `EtagCurvaMercadoTest`). A decisão (cerca de 3 usuários): quem salva por último vence, e o estado anterior fica no log `CADASTRO_ALTERADO`, de onde se recupera.

**O que é.** Duas pessoas leem a mesma curva e as duas enviam alterações: sem controle, a segunda apaga a primeira sem perceber. Com o `ETag`, a leitura traz um hash do estado, e toda escrita devolve esse hash em `If-Match`. Se o estado mudou nesse meio tempo, a escrita é recusada.

**Onde ficava cada peça:**
- `domain/cadastro/EtagCurvaMercado.java`, Java puro; o JSON canônico era montado pelo adaptador (`CurvaCanonicoJsonSerializer`, em `adapter/out`) com o Jackson e passado como texto.
- `GET /curvas-mercado/{codigo}` e toda resposta de escrita traziam o cabeçalho `ETag`.
- Códigos de erro `ALTERADO_POR_OUTRO` (412) e `IF_MATCH_AUSENTE` (428), na spec `cadastro-curva-mercado` e no `CadastroErrorCode`.
- Na planilha do cadastro (change `curves-operacao-curvas`), a coluna `Controle` da aba `Curvas` guardava o `ETag` da curva na exportação (vazio = curva nova). Na importação, `Controle` diferente do `ETag` atual dava `ALTERADO_POR_OUTRO` na linha da curva e nada era aplicado (cenário "Curva alterada depois da exportação").
- No `exemplo-cadastro-7-curvas.txt`, a ordem das chamadas pedia `If-Match` com o `ETag` mais recente depois da criação (cada alteração muda o `ETag`, e a resposta traz o novo), e a aba `Curvas` tinha `Controle` vazio.

**Documento canônico e hash.** SHA-256 hexa minúsculo do JSON canônico: chaves em ordem alfabética em todos os níveis, sem espaços, nulos presentes como `null`, strings UTF-8 sem escape de não ASCII. Estrutura (a `PRE` do exemplo das 7 curvas: curva criada, provedor da curva `idCurvaProvedor` 1, versão 1):

```json
{"configuracoes":[{"fimVigencia":null,"inicioVigencia":"2026-01-01","interpolador":"FlatForward","modeloConstrucao":"PRONTA_TS_B3","parametros":{"BASE_INTERPOLACAO":"Discount","BUSINESS_DAY_CONVENTION":"Following","CALENDARIO":"Brazil","CASAS_DECIMAIS":7,"DAY_COUNTER_TEMPO":"Business252","EXTRAPOLACAO_FIM":"FlatForward","EXTRAPOLACAO_INICIO":"Disabled","FREQUENCY":"Annual","HORIZONTE":"10Y","MERCADO_CALENDARIO":"Settlement","MODO_ARREDONDAMENTO":"HALF_UP"},"versao":1}],"curva":{"classeAtivo":null,"classificacao":null,"codigo":"PRE","compounding":"Compounded","dayCounterCotacao":"Business252","fimVigencia":null,"inicioVigencia":"2026-01-01","moeda":"BRL","nome":"DIxPRE","pais":"BR","situacao":"ATIVO","unidade":"TAXA"},"provedores":[{"tickerProvedor":"PRE","idCurvaProvedor":1,"prioridade":1,"produto":"TS","provedor":"B3"}]}
```

Vetor de teste: esse texto dá `0a45962d58276782018cb313962646ae889877c9c01279cab32aa3909c654936`. Provedores da curva ordenados por `idCurvaProvedor`, configurações por `versao`. Ficavam **fora** do cálculo `dBaseReft`, `cUsuarCalc`, `cUsuarAtulz`, `dCriacReg` e `dUltAtulz`: assim uma construção do engine (que grava `dBaseReft`) não invalidava a edição de ninguém. Não se usava o `dUltAtulz` porque o `datetime` do SQL Server tem precisão de cerca de 3 ms. As colunas `CHAR` eram aparadas antes do cálculo, e o hash era o mesmo antes e depois de reler uma curva. Com Jackson: `JsonMapper.builder().enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS).enable(MapperFeature.SORT_PROPERTIES_ALPHABETICALLY)`, montando o documento com `Map`/`record` só com esses campos.

**Regras de escrita.** Toda alteração (curva, inativação, reativação, provedor da curva, configuração): sem `If-Match` → 428 `IF_MATCH_AUSENTE`; `If-Match` diferente do `ETag` calculado **dentro da transação, depois de travar a curva** (`SELECT ... WITH (UPDLOCK, ROWLOCK)`, com o `@QueryHints` de 60 s da seção 2.1) → 412 `ALTERADO_POR_OUTRO`, nada gravado. A resposta de sucesso trazia o `ETag` novo. A edição de vértices e a do bruto da B3 nunca usaram `If-Match`.

**Cenários da spec `cadastro-curva-mercado` (requisito "Concorrência otimista"):**
- *Duas pessoas editando a mesma curva:* duas pessoas leem a `PRE` e as duas enviam alterações com o mesmo `ETag`; a primeira é gravada, e a segunda recebe 412 `ALTERADO_POR_OUTRO`.
- *Alteração sem If-Match:* `PUT /curvas-mercado/PRE` sem o cabeçalho; 428 `IF_MATCH_AUSENTE`, nada gravado.
- *Situação lida de coluna CHAR:* o `ETag` era o mesmo de antes da leitura.

**Testes que existiam:**
- `EtagCurvaMercadoTest`: vetor acima; ordem de chaves; `dBaseReft` alterado não muda o `ETag`; `CHAR` com espaços dá o mesmo `ETag`.
- `CurvaMercadoServiceTest`: duas pessoas editando → 412; sem `If-Match` → 428.
- `CadastroPlanilhaServiceTest`: `Controle` antigo → `ALTERADO_POR_OUTRO`.
- Conferência 12.1 (o vetor do `ETag`) e homologação (`ETag` real da `PRE` igual ao vetor, se o `idCurvaProvedor` for 1).

**Para retomar:** devolver a tarefa 1.3 e as peças acima, o `ETag` nas respostas do `GET` e das escritas, a coluna `Controle` na planilha do `curves-operacao-curvas` e o cenário de importação de planilha antiga, e os passos com `If-Match` do exemplo das 7 curvas.

# curve-engine

Constrói curva por bootstrap a partir dos contratos DI1 (ou por modelo Groovy importado), valida e publica; interpola sob demanda com cache.

## Layout (arquitetura hexagonal)

```
com.poccurves.engine
├── application/
│   ├── model/                entidades e regras puras (VersaoCurva, ModeloCurva, ContextoValidacao, CurvaJuros/Vertice,
│   │                         interpoladores e políticas de extrapolação, CurveBootstrapper, RateHelper,
│   │                         ReconciliadorCurva...) — inclui algoritmo, não só dado
│   ├── validator/            os 8 TesteValidacao (TesteEstrutural, TesteMonotonicidadeFatorDesconto...)
│   ├── exception/            ModeloConstrucaoException, VersaoJaExisteException
│   ├── port/                 7 *RepositoryPort, CurvaPublicadaEventPort, JsonPort, ModeloConstrucaoPort, CacheInterpolacaoPort
│   ├── service/               orquestração interna, não chamada direto por controller (ConstrucaoCurvaService,
│   │                         BateriaValidacaoService, PromocaoVersaoCurvaService, ModeloConstrucaoResolver)
│   └── usecase/               ponto de entrada por operação de negócio, um por controller (PublicacaoCurvaService,
│                             InterpolacaoService, CompararModelosService, ListarModelosService,
│                             ImportarModeloGroovyService, ConsoleDesenvolvimentoModeloService)
├── adapter/in/web            InterpolacaoController, ModelosController, ConsoleDesenvolvimentoModeloController (@Profile("local")), ConstrucaoCurvaController
├── adapter/in/bootstrap      ModeloCurvaBootstrap
├── adapter/out/persistence   7 repositórios JDBC
├── adapter/out/http          OrchestradorCallbackClient
├── adapter/out/json          JacksonJsonAdapter
├── adapter/out/construcao    BuiltinModeloConstrucao, GroovyModeloConstrucao (sandbox)
├── adapter/out/cache         RedisCacheInterpolacaoAdapter
└── config/                   wiring Spring (UseCaseConfig, InterpoladorConfig, RedisConfig, CurveEngineSecurityConfig)
```

Layout espelha o padrão hex real usado em outro projeto (`adapter`/`application`/`util`, com `application` subdividido em `model`/`service`/`usecase`/`validator`/`port`/`exception`) — revisado nesta sessão a pedido do usuário, substituindo o `domain`/`application` separado que existia antes. `ArchitectureTest` continua banindo import de framework em qualquer subpacote de `application` (o padrão `..application..` já cobre todos os novos subpacotes).

`@Transactional` em `application` é exceção deliberada (mesma razão de `curve-api`).

## Ciclo de modelo de construção

Cada `ModeloCurva` tem `tipo() == BUILTIN` ou `GROOVY`. `ModeloConstrucaoResolver` despacha para a primeira implementação de `ModeloConstrucaoPort` (injetadas via lista pelo Spring — hoje `BuiltinModeloConstrucao` e `GroovyModeloConstrucao`) cujo `suporta(modelo)` seja verdadeiro. A troca de modelo de uma curva (`versao_definicao_curva.modelo_curva_id` apontando pra outro `ModeloCurva`) é operação de tela, sem build/deploy/restart — o resolver decide por chamada, não tem estado.

- **BUILTIN**: hoje só `PRE_DI1_B3`, delega para `CurveBootstrapper.montarCurvaPreDeDi1` (montagem direta a partir dos contratos DI1, sem stripping — ver Javadoc de `CurveBootstrapper`).
- **GROOVY**: script importado via `POST /api/v1/modelos/groovy` (`ROLE_CURVE_ADMIN`), validado por compilação E execução real contra os insumos de amostra informados na importação. Comparação entre dois modelos (sem publicar) via `POST /api/v1/modelos/comparar`. Console local de teste ad-hoc, sem persistir, em `POST /api/v1/dev/console-modelo/testar` — só existe sob `@Profile("local")`, nunca registrado em ambiente implantado.

## Contenção do sandbox Groovy

`GroovyModeloConstrucao` roda script de terceiro com duas camadas independentes, não uma:

1. **`SecureASTCustomizer`** (compile-time) — lista de permissão de imports e de receptores (tipos/pacotes que o script pode nomear ou chamar método).
2. **`SandboxSecurityManager`** (JVM, escopado à thread `groovy-modelo-construcao`) — backstop de arquivo/rede/processo/`System.exit`.

**A camada 1 sozinha é insuficiente**, achado real e verificado nesta sessão: `SecureASTCustomizer` não intercepta chamada de construtor nua (`new Socket(...)`, `new File(...)`) — só a camada 2 pega isso de verdade (provado com teste que efetivamente tentou conectar). As duas camadas juntas são o requisito mínimo, não uma opcional sobre a outra.

Limites adicionais: timeout configurável (`curve-engine.groovy.timeout-segundos`, default 5s) via `Future.get` com `ExecutorService` dedicado — violação aborta nomeando o limite excedido. **Limite de memória não é isolável por thread na JVM** (só por processo) — o timeout é o limite prático real; um script que aloca sem controle estoura o timeout ou o `OutOfMemoryError` da JVM inteira, que não é contido pelo desenho atual (limitação conhecida, documentada no Javadoc da classe, não escondida).

O script recebe só uma lista de mapas imutáveis com os insumos já resolvidos (nunca conexão, cliente HTTP, `ApplicationContext` ou relógio mutável) e devolve uma lista de mapas `[prazoDiasUteis, taxa]` — tradução por valor nos dois sentidos, sem nenhuma referência a objeto vivo do processo atravessando a fronteira.

## Bateria de validação

8 `TesteValidacao` (`application/validator/`): estrutural, monotonicidade de fator de desconto, limite de taxa forward, faixa plausível, suavidade da estrutura a termo, reprecificação de instrumentos de calibração, variação contra curva anterior, comparação contra curva importada da mesma data. Cada um recebe `ContextoValidacao` (curva construída + insumos + comparações opcionais) e um `limite` — a classificação (BLOQUEANTE/AVISO) é configuração por curva (`versao_definicao_curva.limites_validacao`), não constante de código.

`BateriaValidacaoService` roda todos os testes habilitados dentro de um `try/catch` amplo por teste — falha na execução de um teste vira `REPROVADO`, nunca propaga nem é tratada como sucesso (`aprovadaSemBloqueioReprovado` só é `true` se nenhum BLOQUEANTE reprovou). `PublicacaoCurvaService.processarPedidoConstrucao` promove a versão quando o veredito aprova (mesmo com AVISO reprovado — a curva sobe com o aviso registrado no callback de conclusão, campo `warnings`) e reprova (preservando a versão anterior publicada) quando um BLOQUEANTE reprova, notificando o `curve-orchestrator` do resultado nos três casos (sucesso, reprovação, erro) via `OrchestradorCallbackClient` — ver D1d em `curves-solution-architecture/design.md`: o motor não usa Kafka, recebe pedido de construção em `POST /api/v1/construcoes` e responde em background com callback HTTP.

## API de interpolação

`POST /curvas/{codigo}/interpolacao` — lê `versao_curva`/`vertice_curva` já publicados, nunca reconstrói. Seleção de versão: corrente (default) ou por número explícito (`asOf` por instante histórico não implementado — contrato `curva-api.yaml` também não tem esse campo). Interpolador/política de extrapolação resolvidos pela definição vigente na data; resposta sempre sinaliza qual foi usado. Consulta em lote preserva a ordem e isola prazo inválido dos demais.

**Cache Redis** (`RedisCacheInterpolacaoAdapter`): chave `interpolacao:{codigoCurva}|{dataReferencia}|{versaoId}|{interpolador}|{prazo}` — inclui o UUID da versão, então uma versão nova publicada produz uma chave inteiramente diferente por construção, sem precisar de nenhuma invalidação explícita. Falha de conexão/timeout com o Redis nunca propaga — degrada para recalcular do banco (perda de desempenho, nunca de correção).

## ArchitectureTest

```bash
mvn -pl services/curve-engine -am test -Dtest=ArchitectureTest
```

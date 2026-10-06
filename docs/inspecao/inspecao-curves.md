# Inspeção da change curves-cadastro-curvas (v1)

> Transcrito das fotos do relatório gerado pelo Copilot no repositório real (2026-10-06), conforme `openspec/changes/curves-cadastro-curvas/verificacao.md`. Inspeção somente de leitura; o Copilot não alterou código nem `tasks.md`.

## A. Funcionalidades

### 1.1 Erros, correlação, fuso e contrato de tipos

Status: diverge

Provas:
- `Application.java:27-36`
- `AbstractRestExceptionHandler.java:231-239, 280`
- `ApplicationExceptionHandler.java:127-139`
- `JsonConfiguration.java:25-42`
- `application.yml:77-78`

O fuso da JVM é fixado e validado.

Os erros usam `errorId` (não `correlationId`) e `detalhes` só aparece quando não vazio. Não há evidência de `X-Correlation-Id` em todas as respostas.

Decimais são string no DTO B3, mas ANBIMA e Bloomberg declaram valores como `BigDecimal`.

O formato de instante usa UTC e não garante `-03:00`.

### 1.2 Auditoria do cadastro (log e JSON)

Status: pronto

Provas:
- `CurvaMercadoService.java:67-80, 144, 222, 263, 322`
- `CurvaProvedorService.java:106, 151, 170`
- `ConfiguracaoCurvaService.java:153, 197`
- `LogCloudEventosAdapter.java:24-46`
- `CurvaMercadoController.java:95-123`

Eventos de criação, alteração, exclusão e inativação são publicados após as mutações e executados em `afterCommit`.

O endpoint remonta a auditoria para resposta JSON.

### 2.1 CRUD da curva (inclusive `dono` em `cPprioDado`)

Status: diverge

Provas:
- `CurvaMercdEntity.java:62-63, 77-78, 89-90`
- `CurvaMercdPersistenceAdapter.java:96-105, 119-137`

O campo `cPprioDado` existe na entidade, mas não é copiado pelo adaptador de/para o domínio.

`dBaseReft` e `cUsuarCalc` são somente leitura pela entidade.

### 2.2 Listagem

Esperado: `itens`, `total`, `provedores`, `dono`, `ultimaExecucao` e filtros `provedor` e `dono`.

Status: diverge

Provas:
- `CurvaMercadoController.java:35-58`
- `CurvasMercadoPaginadaResponse.java:5-11`
- `CurvaMercadoResponse.java:9-22`

Há filtros para nome, código, unidade e situação, mas não para `provedor` ou `dono`.

O item não expõe os três campos pedidos. A resposta usa `totalElementos`/`totalPaginas`, não `total`.

### 3.1 Provedores da curva

Status: diverge

Provas:
- `CurvaProvedorService.java:66-109, 115-153, 289-305`
- `CurvaPrvdrRepository.java:25-26`
- `CurvaPrvdrEntity.java:36`
- `CodigoAvisoCurva.java:8`

CRUD, prioridade e ID com `MAX + 1`/trava existem.

O `PUT` mantém o provedor existente. `CURVA_COM_FILHAS` aparece no enum, mas não há uso correspondente no serviço.

### 4.1 Configuração: validação e versões

Status: diverge

Provas:
- `ValidadorParametros.java:92-97, 283-303, 309-322`
- `ConfiguracaoCurvaService.java:98-110, 139-151`

JSON ordenado/compacto limitado a 1.024 caracteres e aviso `MODELO_NAO_NATIVO` existem.

A criação rejeita início de vigência no passado, divergindo do cenário de correção retroativa descrito em `tasks.md:41`.

### 4.2 Valores aceitos

Status: diverge

Provas:
- `ValoresController.java:13-23`
- `ValoresService.java:30-58, 61-69, 144-157`

O próprio serviço expõe os valores. Os catálogos enumerados recebem rótulo/descrição, mas os valores dos parâmetros de construção são listas de strings simples.

Não há evidência de enumeração desses parâmetros no Swagger.

### 5.1 CRUD dos brutos B3, ANBIMA e Bloomberg

Status: pronto

Provas:
- `BtrsCurvaPrimrController.java:24, 34-108`
- `AnbmaCurvaPrimrController.java:24, 34-108`
- `BbergCurvaPrimrController.java:24, 34-108`
- `BtrsCurvaPrimrService.java:76-195`
- `AnbmaCurvaPrimrService.java:70-183`
- `BbergCurvaPrimrService.java:69-192`

Os CRUDs dos três provedores já estão expostos e têm serviços próprios. As rotas estão listadas na seção B.

### 5.1 Rotas `/dados-mercado` sobre esses CRUDs

Status: falta

Provas:
- `BtrsCurvaPrimrController.java:24, 34-108`
- `AnbmaCurvaPrimrController.java:24, 34-108`
- `BbergCurvaPrimrController.java:24, 34-108`

Os controllers expõem rotas sob `/curvas-mercado/.../primaria-*`. Não foi localizada rota `/dados-mercado`.

### 6.1 Repasse ao engine (cinco rotas)

Status: falta

Provas:
- `CurvaMercadoController.java:26-95`
- `BtrsCurvaPrimrController.java:24-108`
- `CodigoAvisoCurva.java:10`

Não há rotas de construção/interpolação nos controllers inspecionados.

`ENGINE_INDISPONIVEL` existe como valor de enum, mas isso não comprova cliente HTTP nem tratamento 503.

## B. Rotas expostas (`/api/v1`)

### Cadastro de curvas

- Esperado `GET /curvas-mercado` (listagem). Existe: `GET /api/v1/curvas-mercado` — `CurvaMercadoController.java:26, 35`.
- Esperado `GET/POST /curvas-mercado` e `GET/PUT /curvas-mercado/{codigo}`. Existem: `GET /api/v1/curvas-mercado/{codigo}`, `POST /api/v1/curvas-mercado`, `PUT /api/v1/curvas-mercado/{codigo}` — `CurvaMercadoController.java:61, 67, 74`.
- Esperado `POST /curvas-mercado/{codigo}/inativacao` e `/reativacao`. Existem — `CurvaMercadoController.java:83, 89`.
- Esperado `GET /curvas-mercado/{codigo}/auditoria`. Existe — `CurvaMercadoController.java:95-99`.

### Provedores e configurações

- Esperado `GET/POST /curvas-mercado/{codigo}/provedores` e `PUT/DELETE .../{idCurvaProvedor}`. Existem — `CurvaProvedorController.java:33, 41, 57, 74`.
- Esperado `GET /curvas-mercado/provedores?provedor=&produto=&codigoNaFonte=`. Existe `GET /api/v1/curvas-mercado/provedores`, os três parâmetros opcionais — `CurvaProvedorController.java:95-102`.
- Configurações (listar, vigente, validar, criar e excluir): `GET /api/v1/curvas-mercado/{codigo}/configuracoes`, `GET .../vigente`, `POST .../validacao`, `POST .../configuracoes`, `DELETE .../{versao}` — `ConfiguracaoCurvaController.java:21, 28, 36, 45, 54, 68`.
- Valores aceitos: `GET /api/v1/curvas-mercado/valores` — `ValoresController.java:13, 20`.

### CRUDs dos dados brutos atuais

B3 — `BtrsCurvaPrimrController.java:24, 34, 47, 66, 82, 98, 108`:
- `GET /api/v1/curvas-mercado/primaria-b3`
- `GET /api/v1/curvas-mercado/{codigo}/primaria-b3/{dataBase}`
- `POST .../vertices`, `PUT .../vertices/{id}`, `DELETE .../vertices/{id}`, `DELETE .../{dataBase}`

ANBIMA — `AnbmaCurvaPrimrController.java:24, 34, 47, 66, 82, 98, 108`:
- `GET /api/v1/curvas-mercado/primaria-anbima`
- `GET /api/v1/curvas-mercado/{codigo}/primaria-anbima/{dataBase}`
- `POST .../vertices`, `PUT .../vertices/{id}`, `DELETE .../vertices/{id}`, `DELETE .../{dataBase}`

Bloomberg — `BbergCurvaPrimrController.java:24, 34, 47, 66, 82, 98, 108`:
- `GET /api/v1/curvas-mercado/primaria-bloomberg`
- `GET /api/v1/curvas-mercado/{codigo}/primaria-bloomberg/{dataBase}`
- `POST .../vertices`, `PUT .../vertices/{id}`, `DELETE .../vertices/{id}`, `DELETE .../{dataBase}`

### Rotas esperadas não encontradas

- `/dados-mercado/{provedor}...`: não foi localizada rota com esse prefixo; os CRUDs atuais estão sob `primaria-*` (provas: os três controllers acima, `:24, 34-108`).
- Rotas de construção/interpolação (`POST /curvas-mercado/{codigo}/{dataBase}/construcao`, `POST .../interpolada`, `GET .../vertices`, `/pontos` e `/interpolacao`): não foram localizadas rotas de ação/repasse (provas: `CurvaMercadoController.java:26-95`; `BtrsCurvaPrimrController.java:24-108`).

### Outras rotas existentes

- `POST/GET /api/v1/provedores`
- `GET/PUT/DELETE /api/v1/provedores/{nomeProvedor}`
- `GET /api/v1/provedores/filtro`
- Prova: `ProvedorController.java:20, 30, 51, 61, 75, 96, 107`

## C. Contrato com o engine

### Cliente HTTP

Esperado: informar se existe, a classe e a propriedade da URL.

Resultado: não sei. Não localizei classe/porta/cliente do engine em `src/main/java`; há somente a constante `ENGINE_INDISPONIVEL` no enum de avisos (`CodigoAvisoCurva.java:10`).

### Rotas chamadas

Esperadas: `POST /api/v1/curvas/{codigo}/{dataBase}/construcao`, `POST .../interpolada`, `GET /api/v1/curvas/{codigo}/{dataBase}`, `GET .../pontos`, `GET .../interpolacao`.

Não há rota de ação nos controllers listados na seção B; a listagem de curvas termina em cadastro/auditoria (`CurvaMercadoController.java:26-95`).

Resultado: não bate.

### Cabeçalhos enviados

Esperados: `X-Correlation-Id` e `X-Usuario`; nunca `Authorization`.

Não localizei implementação de chamada HTTP do engine ou configuração desses cabeçalhos.

Resultado: não sei.

### Engine fora ou tempo esgotado

Esperado: HTTP 503 `ENGINE_INDISPONIVEL`.

A constante está no enum `CodigoAvisoCurva.java:10`. Não foi localizada implementação de exceção/cliente que converta falha em HTTP 503.

Resultado: não bate.

### Regra de parâmetros

Esperado: mesmas chaves e combinações da spec `curve-build-pipeline` do engine.

Chaves reconhecidas em `ValidadorParametros.java:92-97`:
- `BASE_INTERPOLACAO`, `DAY_COUNTER_TEMPO`, `FREQUENCY`, `CALENDARIO`, `MERCADO_CALENDARIO`
- `BUSINESS_DAY_CONVENTION`, `EXTRAPOLACAO_INICIO`, `EXTRAPOLACAO_FIM`, `HORIZONTE`, `CASAS_DECIMAIS`
- `MODO_ARREDONDAMENTO`, `VERSAO_SCRIPT_CONSTRUCAO`, `VERSAO_SCRIPT_INTERPOLACAO`, `VERSAO_SCRIPT_CALENDARIO`, `MODELOS_POR_ORIGEM`

Resultado: não sei se bate; a spec do engine não foi localizada no diretório `openspec` deste repositório.

### `cModDado`

Esperado: JSON compacto, chaves ordenadas conforme tabela, até 1.024 caracteres.

A validação serializa `paramsOrdenados` e rejeita acima de 1.024 (`ValidadorParametros.java:283-303`). O serviço persiste `res.jsonCompacto()` e a coluna tem tamanho 1.024 (`ConfiguracaoCurvaService.java:139-151`; `ConfgCurvaEntity.java:33`).

Resultado: bate.

### `tCurvaMercd`

Esperado: nunca escrever `dBaseReft` nem `cUsuarCalc`.

Ambos os campos estão com `insertable = false, updatable = false` (`CurvaMercdEntity.java:77-78, 89-90`).

Resultado: bate.

### `tCurvaPrvdr`

Esperado: prioridade em `cPriorCsumo`; `idCurvaProvedor` por `MAX + 1` com trava.

Prioridade mapeada em `cPriorCsumo` (`CurvaPrvdrEntity.java:36`). A consulta usa `MAX + 1` com `UPDLOCK, HOLDLOCK` (`CurvaPrvdrRepository.java:25-26`); o serviço chama `proximoIdCurvaPrvdr()` (`CurvaProvedorService.java:92`).

Resultado: bate.

### Situação

Esperado: gravar `ATIVO`/`INATIVO` em `cSitReg` e ler com `trim`.

O adaptador grava a situação em `cSitReg` (`CurvaMercdPersistenceAdapter.java:105`). O getter remove espaços finais (`CurvaMercdEntity.java:107`).

Resultado: bate.

## D. Contrato com o front

### Formato de erro

Esperado: código, mensagem, `detalhes` e `correlationId`.

As exceções usam `code`, `error` e `message`; `detalhes` só é incluído quando não vazio (`ApplicationExceptionHandler.java:127-139`). O erro genérico inclui `errorId`, não `correlationId` (`AbstractRestExceptionHandler.java:231-239`).

Resultado: diverge.

### Decimais

Esperado: string, sem expoente.

JSON habilita `WRITE_BIGDECIMAL_AS_PLAIN` (`JsonConfiguration.java:25`). B3 formata para string (`BtrsCurvaPrimrVerticeResponse.java:22-40`), mas ANBIMA e Bloomberg declaram campos como `BigDecimal` (`AnbmaCurvaPrimrVerticeResponse.java:11-12`; `BbergCurvaPrimrVerticeResponse.java:14-16`).

Resultado: diverge.

### Datas e instantes

Esperado: `AAAA-MM-DD`; instante com `-03:00`.

Datas de configuração são `LocalDate` com padrão `yyyy-MM-dd` (`ConfiguracaoCurvaResponse.java:15-16`). A configuração global usa UTC e padrão sem offset (`application.yml:77-78`); o serializador de `LocalDateTime` usa esse padrão (`JsonConfiguration.java:32-35`).

Resultado: diverge.

### `avisos`

Esperado: `{ codigo, mensagem, detalhes }`, sempre presente.

`AvisoCurva` tem esses três componentes (`domain/aviso/AvisoCurva.java:5-8`). `CurvaMercadoResponse` normaliza lista nula (`CurvaMercadoResponse.java:22-38`), mas `CurvaProvedorResponse` não tem campo `avisos` (`CurvaProvedorResponse.java:7-14`). DTOs de dados brutos declaram listas sem normalização (`AnbmaCurvaPrimrDataResponse.java:9-13`).

Resultado: diverge.

### Autenticação

Esperado: nenhuma; `X-Usuario` opcional.

Não há parâmetro/cabeçalho de usuário nos métodos da listagem de curvas (`CurvaMercadoController.java:35-43`). Isso não prova a ausência de filtros globais de autenticação.

Resultado: não sei.

## E. Nomes divergentes

- Código: `BtrsCurvaPrimrController` e rota `primaria-b3`. Esperado: `B3` e rotas `/dados-mercado`. Prova: `BtrsCurvaPrimrController.java:24, 34-108`.
- Código: `AnbmaCurvaPrimrController` e rota `primaria-anbima`. Esperado: rotas `/dados-mercado`. Prova: `AnbmaCurvaPrimrController.java:24, 34-108`.
- Código: `BbergCurvaPrimrController` e rota `primaria-bloomberg`. Esperado: rotas `/dados-mercado`. Prova: `BbergCurvaPrimrController.java:24, 34-108`.
- Código: `totalElementos` e `totalPaginas`. Esperado: `total`. Prova: `CurvasMercadoPaginadaResponse.java:5-11`.
- Código: `cPprioDado`. Esperado: campo de domínio/API `dono`. Provas: `CurvaMercdEntity.java:62-63`; `CurvaMercdPersistenceAdapter.java:96-105, 119-137`.

## F. Sobras (existem e a spec atual não pede)

- `CurvaMercadoController.java:95-120`: a rota de auditoria aceita `formato=xlsx` e gera planilha, além do JSON.

## G. Dúvidas

### Tarefas listadas na seção A

A seção A do modelo de `verificacao.md` lista apenas as tarefas 1.1–6.1, mas `tasks.md` também contém 0.1–0.5 e 7.1–7.2. Elas devem entrar nesta inspeção ou ficam fora da matriz? Provas: `verificacao.md:14-27`; `tasks.md:19-23, 54-55`.

### Spec do engine

Qual spec deve ser usada para comparar as chaves e combinações de `ValidadorParametros`? Não localizei `curve-build-pipeline` no diretório `openspec` deste repositório. Provas: `ValidadorParametros.java:92-97`; `tasks.md:41`; `verificacao.md:50-60`.

### Escopo de `avisos`

A regra "`avisos` sempre presente" aplica-se a todas as respostas, inclusive à listagem de provedores? `CurvaProvedorResponse` não contém `avisos`. Provas: `CurvaProvedorResponse.java:7-14`; `verificacao.md:68-71`.

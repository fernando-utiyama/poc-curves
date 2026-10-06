## Context

- **`web/fed`** (Angular 22, sem zona, rotas com `#`, componentes do Liquid via `LiquidCorp`): o proxy `/api` aponta só para a `services/curves`; `request.interceptor` põe `Content-Type: application/json`, `withCredentials` e **3 s de tempo limite em toda requisição**; o `AppErrorHandler` leva todo erro não tratado para `erro/{status}`. O molde de tela ligada à API é o de provedores (`ProvedoresService` + `provedores-lista`, `provedor-add`, `provedor-detalhe`): lista do Liquid (`BradTableService`), aviso de sucesso por `history.state.feedback` no `AlertaSnackbarComponent`, modal de exclusão com Esc e clique fora.
- As telas de curva atuais (`curvas-lista`, `curva-add`, `curva-detalhe`) gravam no `localStorage` e não chamam API.
- **`services/curves`** já tem o CRUD de `tCurvaMercd` e de `tCurvaPrvdr` (change `curves-cadastro-curvas`). O cliente do engine foi retirado da parte 1 da curves; esta change o recoloca, só para as três ações.
- **Engine**: `POST /curvas/{codigo}/{dataBase}/construcao`, `POST .../interpolada`, `GET .../interpolacao` e `GET /curvas/{codigo}/{dataBase}` (change `engine-construcao-curvas`), sem autenticação, com `X-Usuario` opcional. Não há rota que leia os pontos gravados em `tDadoCurva`.

## Goals / Non-Goals

**Goals:**
- Trocar o protótipo de curvas por duas telas sobre a API, no mesmo padrão das telas de provedor: o Cadastro de curvas (manutenção) e a tela Curvas (trabalho do dia a dia numa data-base).
- Front falando só com a curves; a curves é a única que fala com o engine nessas ações.

**Non-Goals:**
- Configuração de cálculo (versões de `tConfgCurva`): a tela só mostra a vigente; criar e excluir versões fica para outra change.
- Painel do dia, edição manual de vértices, planilhas de cadastro e de vértices (change `curves-operacao-curvas`).
- Autenticação: nenhuma na v0 e na v1 (sem JWT no `fed`, na curves e no engine); o usuário, quando houver, vai no `X-Usuario`.

## Decisions

### D1. Ações do engine repassadas pela curves
O front chama `/api/v1/curvas-mercado/{codigo}/{dataBase}/...`, e a curves repassa ao engine. **Por quê:** o `fed` tem um proxy só (`/api` → curves), a curves já valida a curva antes de chamar o engine. **Alternativas rejeitadas:** proxy direto do `fed` ao engine (segundo destino no proxy); pelo bff (o `fed` não fala com o bff hoje).

### D2. Repasse transparente no sucesso; erro no formato da curves
No 2xx, status e corpo do engine voltam sem alteração. No 4xx, a curves mantém status, código, mensagem e `detalhes` do engine, mas no formato de erro dela, para o front ter um formato só (o engine usa Problem Details, e a curves usa o formato do CRUD de provedores). A curves só cria `ENGINE_INDISPONIVEL` (503) para tempo esgotado, rede ou 5xx. **Por quê:** os códigos e as mensagens do engine (`INSUMO_AUSENTE`, `CURVA_NAO_CONSTRUIDA`...) já estão em pt-BR e são o que o gestor precisa ver; traduzir duplicaria o catálogo.

### D3. Cliente do engine na curves
Porta de saída `EngineRepassePort` em `application/port/out` (nome próprio, para não colidir com a `EnginePort` da fase 2 da curves, que devolve `Disponivel`/`Indisponivel`; o mesmo adaptador `EngineHttpClient` pode implementar as duas) com cinco métodos (`construir`, `regravarInterpolada`, `consultarVertices`, `consultarPontos`, `interpolar`), cada um devolvendo `RespostaEngine(int status, String corpoJson)`; adaptador com `java.net.http.HttpClient` (uma instância), base-URL de `curves.engine.url` **sem valor padrão** (sobe com erro se faltar), tempo limite por método (120 s na construção, 60 s na regravação, 30 s nas consultas), sem `Authorization`, com `X-Usuario` e `X-Correlation-Id`. Rotas num `CurvaMercadoAcoesController` + `CurvaMercadoAcoesAPI` e serviço `CurvaMercadoAcoesService`.

### D4. Tempo limite por requisição no `fed`
Um `HttpContextToken<number>` (`TEMPO_LIMITE_MS`, padrão 3000) lido pelo `request.interceptor` no lugar do `timeout(3000)` fixo; o `CurvasMercadoService` passa 130000 (construção), 70000 (regravação) ou 40000 (vértices, pontos e interpolação). **Alternativa rejeitada:** subir o padrão para todas as chamadas, o que esconderia lentidão do cadastro.

### D5. Erros das ações tratados na tela
As chamadas de ação e de gravação usam `subscribe({ error })` e mostram o erro no próprio componente, para o `AppErrorHandler` não levar a `erro/{status}`. Só falhas fora disso (por exemplo, 500 ao abrir a tela) seguem o tratamento global.

### D6. Duas telas: cadastro e dia a dia
- **Cadastro de curvas** (`/cadastro-curvas`, `/cadastro-curvas/nova`, `/cadastro-curvas/:codigo`): os componentes `curvas-lista`, `curva-add` e `curva-detalhe` reescritos. O detalhe tem dois blocos: dados da curva (leitura e edição) e provedores da curva (tabela do Liquid com Editar e Excluir por linha). Nenhuma ação sobre datas.
- **Curvas** (`/curvas`): componente novo `curvas-dia`, com a seleção da curva e da data no topo, a barra de ações (Construir, Recalcular, Origem secundária, Regravar interpolada) e as abas Vértices, Pontos e Interpolar. **Por quê:** o cadastro muda pouco e é de quem administra; a curva do dia é consultada e recalculada a toda hora, por quem opera. Misturar os dois numa tela só alongava o detalhe e escondia as ações.

### D7. Pontos lidos do que está gravado
A aba Pontos usa uma rota nova do engine que lê `tDadoCurva` (`GET /curvas/{codigo}/{dataBase}/pontos?de=&ate=`), repassada pela curves. **Por quê:** mostra exatamente o que os consumidores leem do banco, e o aviso `INTERPOLADA_DESATUALIZADA` diz quando isso diverge dos vértices. **Alternativa rejeitada:** montar a lista pela `/interpolacao`, que recalcula na hora e esconderia uma interpolada desatualizada. A rota só lê; o padrão da tela é um intervalo de 30 dias, para não trazer os 12.390 pontos da `PRE` sem pedir.

## Risks / Trade-offs

- [Construção longa segura a requisição por até 130 s] → botões desabilitados durante a ação e mensagem orientando a consultar os vértices se o front esgotar o tempo; a curves não repete a chamada.
- [Recalcular por engano sobrescreve a curva] → modal de confirmação no recálculo e na origem secundária.
- [Engine fora deixa só as ações indisponíveis] → o cadastro continua funcionando; as ações mostram `ENGINE_INDISPONIVEL`.
- [Quem tinha dados de teste no `localStorage` perde a visão deles] → são dados de protótipo, sem valor; nenhuma migração.

## Migration Plan

1. Implantar a curves com as rotas de ação e a variável `curves.engine.url` configurada.
2. Implantar o `fed` com as telas novas. Rollback: voltar o deploy do `fed`; as rotas novas da curves não quebram nada se ninguém as chamar.

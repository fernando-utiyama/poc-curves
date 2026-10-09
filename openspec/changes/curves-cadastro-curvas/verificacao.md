# Inspeção da curves (v1) — só leitura

**Objetivo:** dizer, com prova, o que do `tasks.md` já existe, o que diverge e o que falta, e se o contrato com o engine e o front bate. O resultado volta para a revisão, que ajusta as tarefas.

**Regras**
1. **Não altere código nem o `tasks.md`.** Não rode `mvn test` nem a suíte; não compile.
2. Procure pela funcionalidade (o que faz, que tabela lê ou grava, que rota expõe), não pelo nome da spec.
3. **Toda linha precisa de prova:** `Arquivo.java:linha` ou um trecho de até 3 linhas. Sem prova, o status é `não sei`; nunca deduza.
4. Responda preenchendo as tabelas abaixo, nesta ordem, num arquivo `inspecao-curves.md` na raiz do repositório. Seja curto: uma linha por item.
5. Planilha está fora da v1: não procure nem aponte falta de planilha.

Status: `pronto` (existe e cumpre) · `diverge` (existe, mas difere: diga em quê) · `falta` · `não sei`.

## A. Funcionalidades (uma linha por tarefa do `tasks.md`)

| Tarefa | Status | Prova | O que diverge ou falta |
|---|---|---|---|
| 1.1 Erros, correlação, fuso e contrato de tipos | | | |
| 1.2 Auditoria do cadastro (log e JSON) | | | |
| 2.1 CRUD da curva (inclusive `dono` em `cPprioDado`) | | | |
| 2.2 Listagem (`itens`, `total`, `provedores`, `dono`, `ultimaExecucao`, filtros `provedor` e `dono`) | | | |
| 3.1 Provedores da curva | | | |
| 4.1 Configuração: validação e versões | | | |
| 4.2 Valores aceitos | | | |
| 5.1 CRUD dos brutos B3, ANBIMA e Bloomberg | | | |
| 5.1 Rotas `/dados-mercado` sobre esses CRUDs | | | |
| 6.1 Repasse ao engine (cinco rotas) | | | |

## B. Rotas expostas (`/api/v1`)

Liste **todas** as rotas que o código expõe hoje (método, caminho, classe:linha) e marque as esperadas:

| Rota esperada | Existe? | Método e caminho reais | Classe:linha |
|---|---|---|---|
| `GET /curvas-mercado` (listagem) | | | |
| `GET/POST /curvas-mercado`, `GET/PUT /curvas-mercado/{codigo}` | | | |
| `POST /curvas-mercado/{codigo}/inativacao` e `/reativacao` | | | |
| `GET /curvas-mercado/{codigo}/auditoria` | | | |
| `GET/POST /curvas-mercado/{codigo}/provedores`, `PUT/DELETE .../{idCurvaProvedor}` | | | |
| `GET /curvas-mercado/provedores?provedor=&produto=&tickerProvedor=` | | | |
| Configurações (listar, vigente, validação, criar, excluir) | | | |
| `GET /curvas-mercado/valores` | | | |
| CRUD dos brutos da B3 (rotas atuais) | | | |
| CRUD dos brutos da ANBIMA (rotas atuais) | | | |
| CRUD dos brutos da Bloomberg (rotas atuais) | | | |
| `/dados-mercado/{provedor}...` | | | |
| `POST /curvas-mercado/{codigo}/{dataBase}/construcao`, `/interpolada`; `GET .../vertices`, `/pontos`, `/interpolacao` | | | |
| Outras rotas que existem e não estão acima | | | |

## C. Contrato com o engine

| Item | Esperado | No código (prova) | Bate? |
|---|---|---|---|
| Cliente HTTP do engine | existe? qual classe, qual propriedade de URL | | |
| Rotas do engine que a curves chama | `POST /api/v1/curvas/{codigo}/{dataBase}/construcao`, `POST .../interpolada`, `GET /api/v1/curvas/{codigo}/{dataBase}`, `GET .../pontos`, `GET .../interpolacao` | | |
| Cabeçalhos enviados | `X-Correlation-Id` e `X-Usuario`; nunca `Authorization` | | |
| Engine fora ou tempo esgotado | 503 `ENGINE_INDISPONIVEL` | | |
| Regra de parâmetros (`ValidadorParametros` ou equivalente) | mesmas chaves e combinações da spec `curve-build-pipeline` do engine | liste as chaves que o código conhece: | |
| `cModDado` gravado | JSON compacto, chaves na ordem da tabela, até 1.024 caracteres | | |
| `tCurvaMercd` | nunca escreve `dBaseReft` nem `cUsuarCalc` | | |
| `tCurvaPrvdr` | prioridade em `cPriorCsumo`; `idCurvaProvedor` por `MAX + 1` com trava | | |
| Situação | grava `ATIVO`/`INATIVO` em `cSitReg`; lê com `trim` | | |

## D. Contrato com o front

| Item | Esperado | No código (prova) | Bate? |
|---|---|---|---|
| Formato de erro | nomes reais dos campos (código, mensagem, `detalhes`, `correlationId`) | | |
| Decimais | string, sem expoente | | |
| Datas e instantes | `AAAA-MM-DD`; instante com `-03:00` | | |
| `avisos` | `{ codigo, mensagem, detalhes }`, sempre presente | | |
| Autenticação | nenhuma; `X-Usuario` opcional | | |

## E. Nomes divergentes

| Nome no código | Nome na change | Onde |
|---|---|---|

## F. Sobras (existem e a spec atual não pede)

| Arquivo | O que faz |
|---|---|

## G. Dúvidas

Uma linha por dúvida, com a prova que gerou a dúvida.

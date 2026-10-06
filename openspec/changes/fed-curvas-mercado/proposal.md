## Why

O front (`web/fed`) tem telas de curva que são protótipo: listam, criam e editam curvas só no `localStorage` do navegador, com um modelo de quatro campos (`id`, `nome`, `descricao`, `produto`) e provedores e fatores de exemplo gerados por fórmula. O cadastro real das curvas de mercado já existe na `services/curves` (`/api/v1/curvas-mercado`), e o engine já sabe construir, recalcular, regravar a curva interpolada e interpolar prazos. Faltam as telas que o gestor usa: uma para manter o cadastro e outra para o trabalho do dia a dia sobre uma curva numa data-base. Falta também a `services/curves` repassar ao engine as ações e consultas que o front pede, e falta no engine uma leitura dos pontos interpolados que ele grava.

## What Changes

- **Tela "Cadastro de curvas"** no `fed` (`/cadastro-curvas`): pesquisa com os filtros da API (trecho de nome, código, unidade, situação) e paginação; cadastro de curva nova; detalhe com alteração, inativação, reativação, auditoria em `.xlsx` e os provedores da curva (incluir, alterar, excluir). É o CRUD completo do cadastro, sem ações sobre datas.
- **Tela "Curvas"** no `fed` (`/curvas`), para o dia a dia: escolher a curva e a data-base e, nessa curva e data, ver os **vértices** construídos, os **pontos** interpolados (um por dia corrido), interpolar prazos pedidos, e disparar construir, recalcular, recalcular por origem secundária e regravar a curva interpolada.
- **`services/curves` repassa ao engine** essas ações e consultas, para o front falar só com a curves (o proxy `/api` do `fed` continua apontando só para ela).
- **Engine ganha a leitura dos pontos interpolados gravados** (`tDadoCurva`), por intervalo de datas.
- **Tempo limite por requisição no `fed`**: o `request.interceptor` aplica 3 s a toda chamada; as ações que chamam o engine passam a ter um tempo limite próprio.
- **BREAKING (front):** as telas protótipo `curvas-lista`, `curva-add` e `curva-detalhe` deixam de usar o `localStorage` (`tcenCurvas`, `tcenCurvasProvedores`, `tcenCurvasParametrizacao`) e passam a ser o Cadastro de curvas, sob `/cadastro-curvas`; a parametrização de 52 colunas e o import de Excel da tela de detalhe saem.

## Capabilities

### New Capabilities
- `fed-cadastro-curvas`: tela do `fed` com o CRUD completo do cadastro de curvas de mercado (pesquisa, criação, alteração, inativação, reativação, auditoria e provedores da curva).
- `fed-curvas-dia`: tela do `fed` para trabalhar uma curva numa data-base (vértices, pontos interpolados, interpolação de prazos, construção, recálculo e regravação da interpolada).
- `pontos-interpolados-engine`: leitura, no engine, dos pontos da curva interpolada gravados em `tDadoCurva` para uma curva e data-base.

### Modified Capabilities
<!-- Nenhuma: não há specs arquivadas em openspec/specs; as specs relacionadas (cadastro-curva-mercado, provedor-curva, curve-engine-api) estão em changes ainda não arquivadas e não mudam de requisito. -->

## Impact

- **web/fed:** `curvas-lista`, `curva-add` e `curva-detalhe` reescritos como Cadastro de curvas (sai o `localStorage`), componente novo da tela Curvas, `CurvasMercadoService`, `request.interceptor` com tempo limite por requisição, cabeçalho com os itens "Curvas" e "Cadastro de curvas".
- **services/curves:** rotas novas sob `/api/v1/curvas-mercado/{codigo}/{dataBase}/...` e um cliente do engine (porta de saída para o engine).
- **engine:** rota nova `GET /api/v1/curvas/{codigo}/{dataBase}/pontos`; usa sem mudar as rotas `construcao`, `interpolada`, `interpolacao` e de consulta de vértices (change `engine-construcao-curvas`).
- **Contrato:** usa sem mudar `cadastro-curva-mercado`, `provedor-curva` e `configuracao-calculo-curva` (change `curves-cadastro-curvas`) e `curve-engine-api` (change `engine-construcao-curvas`).
- **Banco:** nenhuma mudança de schema.

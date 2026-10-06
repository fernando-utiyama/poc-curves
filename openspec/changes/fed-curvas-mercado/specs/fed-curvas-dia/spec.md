## Purpose

No front `web/fed`, a tela "Curvas" do dia a dia: escolher uma curva e uma data-base e, nelas, ver os vértices construídos, interpolar prazos e disparar construção, recálculo e regravação da curva interpolada, mostrando o resultado devolvido pelo engine.

## ADDED Requirements

### Requirement: Seleção da curva e da data-base
A tela `/curvas`, no menu como "Curvas", SHALL ter a seleção da curva (pesquisa por trecho do nome ou código, pela `GET /api/v1/curvas-mercado`, só curvas `ATIVO` por padrão, com a opção de incluir as inativas) e o campo "Data-base" (`dd/mm/aaaa`, padrão: hoje em Brasília). Com `?codigo=` na URL, a curva SHALL vir selecionada. Escolhidas a curva e a data, a tela SHALL carregar os vértices (requisito "Vértices") e mostrar o nome, o código, a unidade e a situação da curva.

#### Scenario: Aberta pelo cadastro
- **WHEN** o gestor abre `/curvas?codigo=PRE`
- **THEN** a curva `PRE` vem selecionada, com a data de hoje, e a tela carrega os vértices dela

### Requirement: Vértices
A aba "Vértices" SHALL mostrar o resultado de `GET /api/v1/curvas-mercado/{codigo}/{dataBase}/vertices` (spec `acoes-curva-mercado`), com os campos como o engine os devolve: a quantidade de vértices, o `hashPontos` e a tabela com data, valor, dias úteis, dias corridos e, só para `TAXA`, fator acumulado e fator diário; quando a resposta trouxer os valores recalculados, eles aparecem ao lado, com a diferença destacada. Os avisos (por exemplo, `CALENDARIO_DIVERGENTE`, `INTERPOLADA_DESATUALIZADA`) SHALL aparecer acima da tabela. Com 404 `CURVA_NAO_CONSTRUIDA`, a aba SHALL mostrar "Curva ainda não construída nesta data" e o botão "Construir".

#### Scenario: Curva construída
- **WHEN** o gestor escolhe a `PRE` e `14/09/2026`
- **THEN** a aba mostra 278 vértices, o primeiro com valor `13,9000000` e 1 dia útil

#### Scenario: Ainda não construída
- **WHEN** a consulta responde 404 `CURVA_NAO_CONSTRUIDA`
- **THEN** a aba mostra "Curva ainda não construída nesta data" e o botão "Construir"

### Requirement: Interpolar prazos
A aba "Interpolar" SHALL aceitar uma lista de prazos em dias úteis e de datas, chamar `GET /api/v1/curvas-mercado/{codigo}/{dataBase}/interpolacao` com todos, e mostrar, para cada prazo na ordem pedida (a resposta vem na mesma ordem), a data, os dias úteis, os dias corridos, o valor, a classificação em pt-BR (`PONTO` "Vértice", `INTERPOLADO` "Interpolado", `EXTRAPOLADO_INICIO` "Extrapolado no início", `EXTRAPOLADO_FIM` "Extrapolado no fim") e, só para `TAXA`, os fatores. Com 422 `PRAZO_FORA_DO_DOMINIO`, SHALL listar os prazos recusados.

#### Scenario: Interpolação por dias úteis
- **WHEN** o gestor pede 21 e 252 dias úteis na `PRE` de `14/09/2026`
- **THEN** a tabela mostra os dois prazos com data, valor e classificação

### Requirement: Ações sobre a curva na data-base
A tela SHALL oferecer, para a curva e a data escolhidas, pelas rotas da spec `acoes-curva-mercado`:

| Ação | Chamada |
|---|---|
| Construir | `POST /api/v1/curvas-mercado/{codigo}/{dataBase}/construcao` |
| Recalcular | a mesma, com `forcarRecalculo=true`, depois de confirmação num modal |
| Recalcular por origem secundária | a mesma, com `forcarRecalculo=true`, `fonte` e `produto` de um provedor da curva que não é o de menor prioridade, escolhido numa lista; só aparece se a curva tiver mais de um provedor |
| Regravar interpolada | `POST .../interpolada` |

O resultado SHALL ser mostrado na tela: situação em pt-BR (campo `situacao`: `CONSTRUIDA` "Construída", `RECONSTRUIDA` "Recalculada", `EXISTENTE` "Já construída"), quantidade de vértices, avisos e duração; depois de uma ação bem-sucedida, as abas abertas SHALL ser recarregadas. Em erro, a tela SHALL mostrar a mensagem e o código devolvidos (por exemplo, `INSUMO_AUSENTE`, `CONSTRUCAO_EM_ANDAMENTO`, `ENGINE_INDISPONIVEL`), sem levar à página de erro global. Enquanto uma ação está em andamento, os botões de ação SHALL ficar desabilitados.

#### Scenario: Recalcular com confirmação
- **WHEN** o gestor clica em "Recalcular" na `PRE` de `14/09/2026` e confirma
- **THEN** o front chama `POST /api/v1/curvas-mercado/PRE/2026-09-14/construcao?forcarRecalculo=true`, mostra "Recalculada" e recarrega os vértices

#### Scenario: Sem insumo na data
- **WHEN** a construção da `DPL` responde 422 `INSUMO_AUSENTE`
- **THEN** a tela mostra a mensagem do engine e o código `INSUMO_AUSENTE`, e continua na tela Curvas

#### Scenario: Origem secundária
- **WHEN** a curva `DI_BACKUP` tem os provedores B3/`TS` (prioridade 2) e outro de prioridade 1, e o gestor recalcula por B3/`TS`
- **THEN** a chamada leva `forcarRecalculo=true&fonte=B3&produto=TS`, e a tela mostra o aviso `ORIGEM_SECUNDARIA` devolvido

### Requirement: Tempo de espera
As chamadas ao engine pela curves SHALL esperar a resposta por até 130 segundos (construção e recálculo), 70 segundos (regravação da interpolada) e 40 segundos (vértices e interpolação), acima do tempo limite da curves para cada uma; as demais chamadas do `fed` mantêm o tempo limite padrão de 3 segundos. Esgotado o tempo do front numa ação, a tela SHALL mostrar "A ação não respondeu a tempo; consulte os vértices para conferir se terminou."

#### Scenario: Construção demorada
- **WHEN** a construção leva 40 segundos para responder
- **THEN** o front espera e mostra o resultado, sem erro de tempo esgotado

### Requirement: Textos e formatos
Todos os textos SHALL estar em pt-BR, com acentuação. Datas SHALL ser mostradas em `dd/mm/aaaa` e enviadas em `AAAA-MM-DD`; valores decimais, recebidos como texto, SHALL ser mostrados com vírgula decimal, sem conversão que perca casas.

#### Scenario: Valor de vértice
- **WHEN** a API devolve o valor `"13.9000000"`
- **THEN** a tabela mostra `13,9000000`

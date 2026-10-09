## Purpose

No `services/curves`, manter os provedores de cada curva de mercado (`tCurvaPrvdr`): de qual provedor, produto e código na fonte a curva recebe dados, e em que prioridade. É por esses provedores que o processor sabe sob qual curva gravar os dados brutos e que o engine escolhe a origem da curva.

## ADDED Requirements

### Requirement: Campos do provedor da curva
Cada provedor da curva SHALL gravar uma linha em `tCurvaPrvdr`:

| Campo da API | Coluna | Regra |
|---|---|---|
| `idCurvaProvedor` | `cIdtfdUnic` | gerado pelo serviço (ver abaixo); só leitura |
| curva | `cTickerIndcd` | o nome da curva da rota |
| `provedor` | `iPrvdrDados` | obrigatório; MUST existir em `tPrvdrDadoMercd` (mantida pelo CRUD de provedores, fora desta change). O engine e o processor reconhecem as fontes pelos identificadores `B3`, `ANBIMA` e `BLOOMBERG` |
| `produto` | `cPrvdrMercd` | obrigatório; 1 a 50 caracteres (ex.: `TS`, `MS`, `BLC2`): o identificador da publicação na fonte |
| `tickerProvedor` | `cTickerPrvdr` | obrigatório; 1 a 1.024 caracteres, sem espaços nas pontas (ex.: `PRE`, `S0490Z`) |
| `prioridade` | `cPriorCsumo` | obrigatório; inteiro maior ou igual a 1 |

Regras:
- (curva, `provedor`, `produto`) MUST ser único: 409 `PROVEDOR_DUPLICADO`;
- `prioridade` MUST ser única dentro da curva: 409 `PRIORIDADE_EM_USO`;
- o mesmo (`provedor`, `produto`, `tickerProvedor`) MAY estar ligado a mais de uma curva: todas recebem os dados brutos daquele código.

Como `cIdtfdUnic` não tem identity nem sequência e o schema não pode mudar, o serviço SHALL gerá-lo como `MAX(cIdtfdUnic) + 1` (ou 1 se a tabela estiver vazia), lido com `UPDLOCK, HOLDLOCK` dentro da mesma transação da inserção, de modo que inserções simultâneas não gerem o mesmo valor. A consulta do `MAX + 1` SHALL esperar até 60 segundos pela trava; esgotado o tempo, a resposta é 500 `ERRO_INTERNO`, sem gravar.

#### Scenario: Ligar a DIxPRE ao TaxaSwap
- **WHEN** o cliente liga a curva `PRE` ao provedor `B3`, produto `TS`, código na fonte `PRE`, prioridade 1
- **THEN** `tCurvaPrvdr` ganha a linha (`DIxPRE`, `B3`, `TS`, `PRE`, 1), e a resposta é 201 com o `idCurvaProvedor`

#### Scenario: Provedor inexistente
- **WHEN** o cliente liga uma curva ao provedor `XPTO`, que não existe em `tPrvdrDadoMercd`
- **THEN** a resposta é 404 com `NAO_ENCONTRADO` informando o provedor, e nada é gravado

#### Scenario: Duas inclusões simultâneas
- **WHEN** dois provedores de curvas diferentes são incluídos ao mesmo tempo
- **THEN** cada uma recebe um `idCurvaProvedor` diferente

### Requirement: Curvas componentes como provedor
O provedor interno `TCEN` (que precisa existir em `tPrvdrDadoMercd`) SHALL ligar uma curva derivada às suas curvas componentes (spec `curve-build-pipeline` do change `engine-modelos-curva`): `tickerProvedor` = nome de uma curva de mercado existente, e `produto` = papel da curva componente no cálculo (ex.: `NUMERADOR`, `DENOMINADOR`). Além das regras gerais, o provedor da curva com `TCEN` MUST ser rejeitado com 422 `DADOS_INVALIDOS` quando a curva componente não existir, for a própria curva, ou criar um ciclo (a curva componente, direta ou indiretamente, já tem esta curva como componente), citando o caminho do ciclo. Inativar uma curva que é componente de curva ativa SHALL trazer o aviso `CURVA_COM_FILHAS`, com as filhas, sem bloquear.

#### Scenario: Inflação implícita ligada às curvas componentes
- **WHEN** o cliente liga a curva `IPCA_IMPLICITA` a (`TCEN`, `NUMERADOR`, `DIxPRE`, prioridade 1) e (`TCEN`, `DENOMINADOR`, `NTN-B`, prioridade 2)
- **THEN** os dois provedores são gravados, e `GET /api/v1/curvas-mercado/provedores?provedor=TCEN&tickerProvedor=NTN-B` lista a `IPCA_IMPLICITA`

#### Scenario: Ciclo recusado
- **WHEN** a curva `A` tem `B` como componente, e o cliente liga `B` a (`TCEN`, `NUMERADOR`, `A`)
- **THEN** a resposta é 422 com `DADOS_INVALIDOS`, citando o ciclo `B` → `A` → `B`

### Requirement: Rotas dos provedores da curva
O serviço SHALL expor (prefixo `/api/v1`):

| Rota | Uso |
|---|---|
| `GET /curvas-mercado/{codigo}/provedores` | listar os provedores da curva, por prioridade |
| `POST /curvas-mercado/{codigo}/provedores` | incluir |
| `PUT /curvas-mercado/{codigo}/provedores/{idCurvaProvedor}` | alterar `produto`, `tickerProvedor` ou `prioridade` |
| `DELETE /curvas-mercado/{codigo}/provedores/{idCurvaProvedor}` | excluir |
| `GET /curvas-mercado/provedores?provedor=&produto=&tickerProvedor=` | quais curvas recebem um código da fonte |

Excluir um provedor da curva não apaga dados brutos já gravados. Trocar o `iPrvdrDados` de um provedor da curva não é permitido: exclui-se e inclui-se outro. O `PUT` que enviar um `provedor` diferente do gravado MUST ser recusado com 422 `DADOS_INVALIDOS` no campo `provedor`, antes de qualquer gravação.

#### Scenario: Troca do provedor num PUT
- **WHEN** o cliente envia `PUT .../provedores/{idCurvaProvedor}` de um provedor `B3` com `provedor` = `ANBIMA`
- **THEN** a resposta é 422 com `DADOS_INVALIDOS` no campo `provedor`, informando que o provedor não muda, e nada é gravado

#### Scenario: Quais curvas recebem o PRE da B3
- **WHEN** o cliente chama `GET /api/v1/curvas-mercado/provedores?provedor=B3&produto=TS&tickerProvedor=PRE`
- **THEN** a resposta lista as curvas ligadas a esse código, como `PRE` e `DI_MERCADO`, com a prioridade de cada provedor da curva

### Requirement: Avisos de coerência com o engine
Sem bloquear a operação, a resposta SHALL trazer `avisos` quando, depois da alteração:
- a curva ficar sem nenhum provedor (`CURVA_SEM_ORIGEM`): o engine não consegue construí-la;
- o provedor e o produto do provedor da curva de menor prioridade não forem os esperados pelo modelo de construção da configuração vigente, para os modelos nativos (`PRONTA_TS_B3`: `B3`/`TS`; `NTNB_BOOTSTRAP_ANBIMA`: `ANBIMA`/`MS`; `SOFR_ZERO_BLOOMBERG`: `BLOOMBERG`/`BLC2`) (`ORIGEM_INCOMPATIVEL_COM_MODELO`): o engine responde `CADASTRO_INVALIDO` na construção;
- a configuração vigente ou uma futura tiver em `MODELOS_POR_ORIGEM` uma chave de provedor da curva que deixou de existir (`MODELO_POR_ORIGEM_SEM_PROVEDOR`): a entrada fica sem uso.

Os provedores da curva de prioridade maior que a menor são as **origens secundárias**: o processor grava para elas o dado bruto do mesmo jeito que para a principal, e o usuário pode construir a curva por uma delas no engine (rota de construção com `fonte` e `produto`, spec `curve-engine-api` do change `engine-modelos-curva`). A construção automática usa só a principal.

#### Scenario: Último provedor excluído
- **WHEN** o único provedor da curva `SLP` é excluído
- **THEN** a exclusão é feita, e a resposta traz o aviso `CURVA_SEM_ORIGEM`

## Purpose

No `services/curves`, manter as ligações entre curva de mercado e provedor (`tCurvaPrvdr`): de qual provedor, produto e código na fonte a curva recebe dados, e em que prioridade. É por essas ligações que o processor sabe sob qual curva gravar os dados brutos e que o engine escolhe a origem da curva.

## ADDED Requirements

### Requirement: Campos da ligação
Cada ligação SHALL gravar uma linha em `tCurvaPrvdr`:

| Campo da API | Coluna | Regra |
|---|---|---|
| `idLigacao` | `cldtfdUnic` | gerado pelo serviço (ver abaixo); só leitura |
| curva | `cTickerIndcd` | o nome da curva da rota |
| `provedor` | `iPrvdrDados` | obrigatório; MUST existir em `tPrvdrDadoMercd` (mantida pelo CRUD de provedores, fora desta change). O engine e o processor reconhecem as fontes pelos identificadores `B3`, `ANBIMA` e `BLOOMBERG` |
| `produto` | `cPrvdrMercd` | obrigatório; 1 a 50 caracteres (ex.: `TS`, `MS`, `BLC2`): o identificador da publicação na fonte |
| `codigoNaFonte` | `cTickerPrvdr` | obrigatório; 1 a 1.024 caracteres, sem espaços nas pontas (ex.: `PRE`, `S0490Z`) |
| `prioridade` | `cPriorCsumo` | obrigatório; inteiro maior ou igual a 1 |

Regras:
- (curva, `provedor`, `produto`) MUST ser único: 409 `LIGACAO_DUPLICADA`;
- `prioridade` MUST ser única dentro da curva: 409 `PRIORIDADE_EM_USO`;
- o mesmo (`provedor`, `produto`, `codigoNaFonte`) MAY estar ligado a mais de uma curva: todas recebem os dados brutos daquele código.

Como `cldtfdUnic` não tem identity nem sequência e o schema não pode mudar, o serviço SHALL gerá-lo como `MAX(cldtfdUnic) + 1` (ou 1 se a tabela estiver vazia), lido com `UPDLOCK, HOLDLOCK` dentro da mesma transação da inserção, de modo que inserções simultâneas não gerem o mesmo valor.

#### Scenario: Ligar a DIxPRE ao TaxaSwap
- **WHEN** o cliente liga a curva `PRE` ao provedor `B3`, produto `TS`, código na fonte `PRE`, prioridade 1
- **THEN** `tCurvaPrvdr` ganha a linha (`DIxPRE`, `B3`, `TS`, `PRE`, 1), e a resposta é 201 com o `idLigacao`

#### Scenario: Provedor inexistente
- **WHEN** o cliente liga uma curva ao provedor `XPTO`, que não existe em `tPrvdrDadoMercd`
- **THEN** a resposta é 404 com `NAO_ENCONTRADO` informando o provedor, e nada é gravado

#### Scenario: Duas inclusões simultâneas
- **WHEN** duas ligações de curvas diferentes são incluídas ao mesmo tempo
- **THEN** cada uma recebe um `idLigacao` diferente

### Requirement: Ligação com curvas mães
O provedor interno `TCEN` (que precisa existir em `tPrvdrDadoMercd`) SHALL ligar uma curva derivada às suas curvas mães (spec `curve-build-pipeline` do change `engine-modelos-curva`): `codigoNaFonte` = nome de uma curva de mercado existente, e `produto` = papel da mãe no cálculo (ex.: `NUMERADOR`, `DENOMINADOR`). Além das regras gerais, a ligação com `TCEN` MUST ser rejeitada com 422 `DADOS_INVALIDOS` quando a curva mãe não existir, for a própria curva, ou criar um ciclo (a mãe, direta ou indiretamente, já tem esta curva como mãe), citando o caminho do ciclo. Inativar uma curva que é mãe de curva ativa SHALL trazer o aviso `CURVA_COM_FILHAS`, com as filhas, sem bloquear.

#### Scenario: Inflação implícita ligada às mães
- **WHEN** o cliente liga a curva `IPCA_IMPLICITA` a (`TCEN`, `NUMERADOR`, `DIxPRE`, prioridade 1) e (`TCEN`, `DENOMINADOR`, `NTN-B`, prioridade 2)
- **THEN** as duas ligações são gravadas, e `GET /api/v1/ligacoes?provedor=TCEN&codigoNaFonte=NTN-B` lista a `IPCA_IMPLICITA`

#### Scenario: Ciclo recusado
- **WHEN** a curva `A` tem `B` como mãe, e o cliente liga `B` a (`TCEN`, `NUMERADOR`, `A`)
- **THEN** a resposta é 422 com `DADOS_INVALIDOS`, citando o ciclo `B` → `A` → `B`

### Requirement: Rotas das ligações
O serviço SHALL expor (prefixo `/api/v1`):

| Rota | Uso | Papel |
|---|---|---|
| `GET /curvas-mercado/{codigo}/ligacoes` | listar as ligações da curva, por prioridade | `Curvas.Leitura` |
| `POST /curvas-mercado/{codigo}/ligacoes` | incluir | `Curvas.Cadastro` |
| `PUT /curvas-mercado/{codigo}/ligacoes/{idLigacao}` | alterar `produto`, `codigoNaFonte` ou `prioridade` | `Curvas.Cadastro` |
| `DELETE /curvas-mercado/{codigo}/ligacoes/{idLigacao}` | excluir | `Curvas.Cadastro` |
| `GET /ligacoes?provedor=&produto=&codigoNaFonte=` | quais curvas recebem um código da fonte | `Curvas.Leitura` |

As alterações seguem a concorrência otimista da curva (`If-Match` com o `ETag` da curva). Excluir uma ligação não apaga dados brutos já gravados. Trocar o provedor de uma ligação não é permitido: exclui-se e inclui-se outra.

#### Scenario: Quais curvas recebem o PRE da B3
- **WHEN** o cliente chama `GET /api/v1/ligacoes?provedor=B3&produto=TS&codigoNaFonte=PRE`
- **THEN** a resposta lista as curvas ligadas a esse código, como `PRE` e `DI_MERCADO`, com a prioridade de cada ligação

### Requirement: Avisos de coerência com o engine
Sem bloquear a operação, a resposta SHALL trazer `avisos` quando, depois da alteração:
- a curva ficar sem nenhuma ligação (`CURVA_SEM_ORIGEM`): o engine não consegue construí-la;
- o provedor e o produto da ligação de menor prioridade não forem os esperados pelo modelo de construção da configuração vigente, para os modelos nativos (`PRONTA_TS_B3`: `B3`/`TS`; `NTNB_BOOTSTRAP_ANBIMA`: `ANBIMA`/`MS`; `SOFR_ZERO_BLOOMBERG`: `BLOOMBERG`/`BLC2`) (`ORIGEM_INCOMPATIVEL_COM_MODELO`): o engine responde `CADASTRO_INVALIDO` na construção;
- a configuração vigente ou uma futura tiver em `MODELOS_POR_ORIGEM` uma chave de ligação que deixou de existir (`MODELO_POR_ORIGEM_SEM_LIGACAO`): a entrada fica sem uso.

As ligações de prioridade maior que a menor são as **origens secundárias**: o processor grava para elas o dado bruto do mesmo jeito que para a principal, e o usuário pode construir a curva por uma delas no engine (rota de construção com `fonte` e `produto`, spec `curve-engine-api` do change `engine-modelos-curva`). A construção automática usa só a principal.

#### Scenario: Última ligação excluída
- **WHEN** a única ligação da curva `SLP` é excluída
- **THEN** a exclusão é feita, e a resposta traz o aviso `CURVA_SEM_ORIGEM`

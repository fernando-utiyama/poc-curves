## Purpose

No `services/curves`, listar, consultar, incluir, alterar e apagar à mão as linhas do dado bruto da B3 (`tBtrsCurvaPrimr`), como contingência para corrigir ou digitar o que o processor grava do `TaxaSwap.txt`. O front primeiro mostra uma listagem geral, uma linha por curva e data-base com dado bruto, para o gestor selecionar; depois abre as linhas da curva naquela data e faz a manutenção linha a linha. O serviço só grava o bruto: nada é disparado no engine, e a curva construída muda só numa construção posterior (spec `curve-load-trigger` e `curve-engine-api` do change `engine-construcao-curvas`).

## ADDED Requirements

### Requirement: Rotas da curva primária B3
O serviço SHALL expor (prefixo `/api/v1`), identificando a curva pelo código (`tCurvaMercd.cTickerIdtfdUnic`) e a linha pelo id (`tBtrsCurvaPrimr.cIdtfdUnic`):

| Rota | Uso | Papel |
|---|---|---|
| `GET /curvas-mercado/primaria-b3?de=AAAA-MM-DD&ate=AAAA-MM-DD&codigo=&nome=&pagina=&tamanho=` | listagem geral para seleção: uma linha por curva e data-base com dado bruto da B3 | `Curvas.Leitura` |
| `GET /curvas-mercado/{codigo}/primaria-b3/{dataBase}` | as linhas da curva na data-base, com os avisos da data | `Curvas.Leitura` |
| `POST /curvas-mercado/{codigo}/primaria-b3/{dataBase}/linhas` | incluir uma linha (também numa data sem nenhuma) | `Curvas.Operador` |
| `PUT /curvas-mercado/{codigo}/primaria-b3/{dataBase}/linhas/{id}` | alterar uma linha, com todos os campos | `Curvas.Operador` |
| `DELETE /curvas-mercado/{codigo}/primaria-b3/{dataBase}/linhas/{id}` | apagar uma linha | `Curvas.Operador` |
| `DELETE /curvas-mercado/{codigo}/primaria-b3/{dataBase}` | apagar todas as linhas da curva na data-base | `Curvas.Operador` |

Linhas de `tCurvaMercd` sem código MUST NOT aparecer. A curva inexistente, e a linha inexistente ou de outra curva ou data-base, respondem 404 `NAO_ENCONTRADO`. Os erros, a autenticação, o `X-Correlation-Id`, o horário de Brasília e o contrato de tipos (decimais como string, datas ISO) seguem a spec `cadastro-curva-mercado`.

#### Scenario: Linha de outra data
- **WHEN** o cliente chama `PUT .../PRE/primaria-b3/2026-09-15/linhas/{id}` com o id de uma linha da `PRE` de `2026-09-14`
- **THEN** a resposta é 404 com `NAO_ENCONTRADO`, e nada é gravado

### Requirement: Listagem geral para seleção
`GET /curvas-mercado/primaria-b3` SHALL devolver, paginado (tamanho padrão 50, máximo 500), uma linha por par (curva, data-base) que tenha linhas em `tBtrsCurvaPrimr`, ordenada por data-base decrescente e código, com:

| Campo | Origem |
|---|---|
| `codigo`, `nome`, `situacao` | `tCurvaMercd` |
| `dataBase` | `tBtrsCurvaPrimr.dBaseReft` |
| `quantidadeLinhas` | quantidade de linhas da curva na data-base |
| `codigosNaFonte` | `cTickerPrvdr` dos provedores da curva com provedor `B3` e produto `TS` em `tCurvaPrvdr` (lista vazia se não houver) |
| `curvaConstruida` | `true` se a curva tem pontos em `tDadoVertcCurva` na data-base |

Filtros: intervalo `de`..`ate` de datas-base (padrão: `ate` = hoje, `de` = `ate` − 30 dias; intervalo máximo de 366 dias, acima → 400 `PARAMETRO_INVALIDO`), `codigo` exato e trecho de `nome` (normalizado, como na listagem de curvas). A listagem MUST ser feita por uma consulta agregada no banco, sem ler as linhas uma a uma.

#### Scenario: Intervalo invertido
- **WHEN** o cliente chama `GET /api/v1/curvas-mercado/primaria-b3?de=2026-09-15&ate=2026-09-01`
- **THEN** a resposta é 400 com `PARAMETRO_INVALIDO`, informando que `de` é posterior a `ate`

#### Scenario: Seleção depois da carga
- **WHEN** o gestor abre a listagem em `2026-09-15`, com a carga B3 de `2026-09-14` gravada para as 5 curvas ligadas
- **THEN** a listagem traz 5 linhas com `dataBase` = `2026-09-14`, cada uma com `quantidadeLinhas` = 278, o código na fonte (`PRE`, `DCL`, `DPL`, `INP`, `PTX`) e `curvaConstruida` conforme o engine já tenha construído

### Requirement: Consulta das linhas de uma data
`GET /curvas-mercado/{codigo}/primaria-b3/{dataBase}` SHALL devolver as linhas ordenadas por dias corridos e id, cada uma com `id` (`cIdtfdUnic`), `diasCorridos` (`cDiaCorri`), `diasUteis` (`cDiaUtil`), `valor` (`vPrecoTx`), `fatorAcumulado` (`vFatorAcum`), `fatorDia` (`vFatorDia`), decimais como string na escala da coluna, e `dataPonto` (data-base + dias corridos, só leitura), mais `curvaConstruida` e os avisos da data (requisito "Validação das linhas") calculados sobre as linhas gravadas. Data-base sem linhas responde 200 com a lista vazia, para o gestor poder digitar do zero.

#### Scenario: Consulta das linhas da PRE
- **WHEN** o cliente chama `GET /api/v1/curvas-mercado/PRE/primaria-b3/2026-09-14`
- **THEN** a resposta traz as 278 linhas, a primeira com `diasCorridos` 1, `diasUteis` 1 e `valor` `13.900000000000`, e a linha de `dataPonto` `2027-01-04` com `diasCorridos` 112, `diasUteis` 75 e `valor` `13.589000000000`

### Requirement: Validação das linhas
Pela mesma regra da edição manual dos pontos, a gravação só MUST ser recusada quando a linha não pode ser gravada de forma consistente; regra de negócio vira aviso, e a linha é gravada. O `POST` e o `PUT` MUST responder 422 `DADOS_INVALIDOS`, sem gravar nada e com um item em `detalhes` por campo, só quando:
- faltar `diasCorridos`, `diasUteis` ou `valor` (sem eles a linha não é um vértice);
- `diasCorridos` ou `diasUteis` não for um inteiro que caiba em `INT`;
- `valor` não for decimal ou não couber em `vPrecoTx` (`DECIMAL(28,12)`: até 16 dígitos inteiros e 12 casas);
- `fatorAcumulado` ou `fatorDia`, opcionais, não forem decimais ou não couberem em `DECIMAL(28,16)` (até 12 dígitos inteiros e 16 casas).

Os valores são gravados exatamente como enviados, sem arredondamento (é o dado bruto). Os avisos SHALL vir na resposta de toda gravação e da consulta, calculados sobre todas as linhas da data depois da gravação, com o efeito que terão no modelo `PRONTA_TS_B3` (spec `b3-ready-curve-model`, requisito "Regras do arquivo"):

| Aviso | Quando | Efeito no engine |
|---|---|---|
| `DIAS_CORRIDOS_NAO_POSITIVO` | `diasCorridos` menor que 1 | a construção falha com `INSUMO_INVALIDO` até a correção |
| `DIAS_UTEIS_INCOERENTES` | `diasUteis` menor que 1 ou maior que `diasCorridos` | a construção falha com `INSUMO_INVALIDO` até a correção |
| `DIAS_CORRIDOS_REPETIDOS` | duas linhas da data com os mesmos `diasCorridos` | a construção falha com `INSUMO_INVALIDO` até a correção |
| `CURVA_SEM_PROVEDOR_B3` | a curva não tem provedor com provedor `B3` e produto `TS` | o processor não grava nem substitui essa curva; o engine só lê o bruto quando o modelo da curva é o da B3 |
| `CURVA_JA_CONSTRUIDA` | a curva já tem pontos em `tDadoVertcCurva` na data-base | a curva construída não muda; a correção só vale num recálculo forçado pelo engine (`forcarRecalculo=true`) |

#### Scenario: Dias corridos repetidos
- **WHEN** o gestor inclui na `PRE` de `2026-09-14` uma linha com `diasCorridos` 112, que já existe
- **THEN** a linha é gravada, e a resposta é 201 com o aviso `DIAS_CORRIDOS_REPETIDOS` citando as duas linhas

#### Scenario: Valor que não cabe na coluna
- **WHEN** o gestor envia `valor` `13.1234567890123`
- **THEN** a resposta é 422 com `DADOS_INVALIDOS` no campo `valor`, e nada é gravado

#### Scenario: Correção depois da construção
- **WHEN** o gestor altera o valor da linha de 112 dias corridos da `PRE` de `2026-09-14`, já construída pelo engine
- **THEN** a linha é gravada, a resposta traz `CURVA_JA_CONSTRUIDA`, e os pontos da curva em `tDadoVertcCurva` continuam os da construção anterior

### Requirement: Gravação sob a trava da curva
Toda escrita (`POST`, `PUT`, `DELETE`) SHALL acontecer numa única transação que:
1. trava a linha da curva em `tCurvaMercd` com `UPDLOCK, ROWLOCK` (a mesma trava da edição de pontos e da construção no engine, que lê o bruto sob ela), esperando até 60 segundos para obtê-la; sem a trava, 500 `ERRO_INTERNO` sem gravar;
2. no `POST`, gera o `cIdtfdUnic` como o processor: `MAX(cIdtfdUnic) + 1` lido com `UPDLOCK, HOLDLOCK` na mesma transação, sem criar objeto no banco, também com espera de até 60 segundos e 500 `ERRO_INTERNO` sem gravar se esgotada;
3. grava a linha com `cTickerIndcd` = nome da curva e `dBaseReft` = data-base (a FK para `tCurvaMercd` é satisfeita pela curva existente);
4. relê as linhas da data para calcular os avisos.

Nenhuma rota do curves faz controle de versão: quem salva por último vence, com o estado anterior no log (nesta rota, no `CURVA_PRIMARIA_EDITADA`). O serviço MUST NOT escrever em `tDadoVertcCurva`, `tDadoCurva` nem `tCurvaMercd`, e MUST NOT chamar o engine por causa dessa gravação. Uma nova carga ou reprocessamento da mesma data pelo processor SHALL substituir todas as linhas da curva na data (spec `b3-carga-processor`, que apaga e insere), inclusive as editadas à mão.

#### Scenario: Digitar uma data sem carga
- **WHEN** a carga B3 de `2026-09-15` não chegou, e o gestor inclui as linhas da `PRE` dessa data uma a uma
- **THEN** cada `POST` responde 201 com o id gerado, nada é disparado no engine, e a próxima construção da data (manual ou pelo orquestrador) usa essas linhas

#### Scenario: Trava da curva não obtida
- **WHEN** a trava da `PRE` está com outra transação (por exemplo, a construção do engine) por mais de 60 segundos, e o gestor envia um `POST` de linha
- **THEN** a resposta é 500 com `ERRO_INTERNO`, nada é gravado e nenhum evento `CURVA_PRIMARIA_EDITADA` é emitido

#### Scenario: Reprocessamento depois da edição
- **WHEN** o gestor alterou uma linha da `PRE` de `2026-09-14`, e depois a data é reprocessada pelo processor
- **THEN** as linhas da `PRE` na data voltam a ser as do arquivo, e a edição continua registrada no log `CURVA_PRIMARIA_EDITADA`

### Requirement: Log da edição do bruto
A edição do bruto é contingência e MUST NOT gerar registro de auditoria. Cada escrita bem-sucedida SHALL registrar, depois do commit, o evento de log `CURVA_PRIMARIA_EDITADA` (nível `AVISO`), com `correlationId`, usuário, fonte (`B3`), código, nome, data-base, operação (`INCLUSAO`, `ALTERACAO`, `EXCLUSAO` ou `EXCLUSAO_DATA`), a linha antes e depois (na `EXCLUSAO_DATA`, todas as linhas apagadas) e a quantidade de linhas da data antes e depois, no horário de Brasília.

#### Scenario: Rastro de uma alteração
- **WHEN** o valor de uma linha da `PRE` de `2026-09-14` é alterado de `13.589000000000` para `13.590000000000`
- **THEN** o log tem `CURVA_PRIMARIA_EDITADA` com `ALTERACAO`, o usuário e a linha com os dois valores, e nenhum registro de auditoria é gravado

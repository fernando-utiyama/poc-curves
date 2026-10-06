## Purpose

Mantém a trilha de toda construção e recálculo de curva sem alterar o schema do banco e sem gravar nada de curva no Blob Storage: quem gravou, quando, a partir de qual carga, com quais modelos e cadastro, e quais pontos foram substituídos. O resumo da última construção fica nas colunas de cálculo de `tCurvaMercd`, o registro de cada gravação, no log estruturado, e o arquivo de auditoria é montado na hora, a pedido do front.

## ADDED Requirements

### Requirement: Registro de auditoria no log
Toda construção e toda reconstrução por recálculo que gravar pontos SHALL emitir, depois do commit, o evento de log `CURVA_GRAVADA` com nível `AVISO` e os campos:
- `idAuditoria` (UUID), `codigo`, `nome`, `dataBase`;
- `operacao`: `CONSTRUCAO` ou `RECONSTRUCAO`;
- `acionadoPor`: `CARGA` (webhook do processor), `DATA_INTEIRA` (construção da data, sob demanda) ou `API`, `usuario` (cabeçalho `X-Usuario` do chamador, nulo quando ausente), `instante` (horário de Brasília, com fuso), `correlationId`;
- `idCarga`, quando a construção veio do webhook (nulo nas demais);
- `hashPontos` gravado e `hashPontosAnterior` (nulo na primeira construção), quantidade de pontos;
- `pontosAnteriores`: lista completa (data e valor) dos pontos substituídos (vazia na primeira construção);
- `origem`: fonte, produto, código na fonte e prioridade do provedor usado, e se é o principal ou um secundário;
- `proveniencia`: versão do engine, `estadoScript`, avisos, modelos (nome, origem, versão, hash), todos os itens do cadastro vigente e, para curva derivada, as curvas componentes (nome, papel e `hashPontos` usado).

Os modelos da `proveniencia` (construção, interpolação e calendário) SHALL ser os que de fato executaram: modelo nativo com origem `JAVA` e a versão do engine; script com origem `GROOVY`, a versão e o hash do script usado. Valores fixos (ex.: sempre `JAVA` versão 1) MUST NOT ser gravados. Se o commit falhar, o evento MUST NOT ser emitido; o erro sai em `CONSTRUCAO_FALHOU`. A construção sem recálculo que devolve `EXISTENTE` e a simulação MUST NOT gerar `CURVA_GRAVADA`. A edição manual de pontos é feita pelo `services/curves` (change `curves-cadastro-curvas`), como operação de contingência, e registrada no log dele (`VERTICES_EDITADOS`). O engine MUST NOT gravar registro de auditoria no Blob Storage nem em tabela.

O destino dos logs (Log Analytics ou equivalente) SHALL ter retenção definida pela área de risco e ser consultável por `nome`, `dataBase` e evento. Este é o histórico de construções da fase atual.

#### Scenario: Proveniência de script Groovy
- **WHEN** a `PRE` é construída com o interpolador pelo script Groovy `FlatForwardAjustado` versão 3 ativo
- **THEN** a proveniência do `CURVA_GRAVADA` traz o interpolador com origem `GROOVY`, versão 3 e o hash do script, e o modelo de construção com origem `JAVA` e a versão do engine

#### Scenario: Construção auditada
- **WHEN** a `PRE` de `2026-09-14` é construída pela carga `B3-TS-20260914-46a249c60bec`
- **THEN** o log tem um `CURVA_GRAVADA` com operação `CONSTRUCAO`, `acionadoPor` = `CARGA`, o `idCarga`, o `hashPontos`, 278 pontos, `pontosAnteriores` vazia e a proveniência

#### Scenario: Recálculo depois de uma edição manual
- **WHEN** um ponto da `PRE` de `2026-09-14` é editado à mão no `services/curves` de 14,1670000 para 14,2000000, e depois a data é recalculada
- **THEN** o `CURVA_GRAVADA` do recálculo tem operação `RECONSTRUCAO` e `pontosAnteriores` com os pontos editados, incluindo 14,2000000

### Requirement: Arquivo de auditoria montado na hora
`GET /api/v1/curvas/{codigo}/{dataBase}/auditoria?formato=xlsx|json`, pedido pelo front, SHALL montar o arquivo de auditoria da curva na data no momento do pedido, sem guardar nada, a partir do estado atual do banco e da fonte:
- **`Resumo`**: código, nome, data-base, instante da geração (horário de Brasília), `correlationId`, versão do engine, última data-base construída e quem calculou (`dBaseReft` e `cUsuarCalc` de `tCurvaMercd`), cadastro vigente na data (curva, origem ou componentes, configuração com todos os parâmetros) e os modelos que seriam usados hoje (nome, origem, versão, hash);
- **`Pontos`**: os pontos gravados em `tDadoVertcCurva` (data, valor, dias úteis, dias corridos, dias 30/360, fator diário e acumulado), com o `hashPontos`, e, ao lado, os mesmos valores recalculados agora, marcando as diferenças; e a quantidade de linhas da curva interpolada em `tDadoCurva`, com a indicação se ela confere com a interpolação dos pontos atuais;
- **`Conferencia`**: os pontos que o modelo produz agora a partir da fonte, lado a lado com os gravados, com a diferença e a situação de cada ponto (`IGUAL`, `DIFERENTE`, `SO_SIMULADO`, `SO_GRAVADO`, os mesmos da simulação), como a comparação da simulação (spec `curve-calculation-memory`), ou o erro da fonte, se o modelo falhar;
- **`Insumos`**: as linhas brutas da origem lidas na data (ou, para curva derivada, os pontos das curvas componentes), com as colunas da spec do modelo.

O nome do arquivo SHALL ser `{codigo}_{dataBase}_AUDITORIA_{AAAAMMDDHHmmss}.xlsx`. Sem pontos gravados, o arquivo SHALL sair do mesmo jeito, com `Pontos` vazia. O arquivo mostra o estado de agora; quem gravou os pontos em cada momento anterior, e os pontos substituídos, estão nos eventos `CURVA_GRAVADA` do engine e `VERTICES_EDITADOS` do `services/curves`, no log.

#### Scenario: Auditoria de uma curva editada
- **WHEN** o gestor pede pelo front a auditoria da `PRE` de `2026-09-14`, que teve um ponto editado à mão depois da construção
- **THEN** o arquivo é montado na hora, com os 278 pontos gravados, a aba `Conferencia` mostrando o ponto `DIFERENTE` entre o gravado e o que a fonte produz, e nada é gravado no Blob nem no banco


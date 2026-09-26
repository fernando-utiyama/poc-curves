## Purpose

Mantém uma trilha de auditoria persistente de toda construção e recálculo de curva, sem alterar o schema do banco: quem gravou, quando, a partir de qual carga, com quais modelos e cadastro, e quais pontos foram substituídos. O resumo da última construção fica nas colunas de cálculo de `tCurvaMercd`, e o histórico completo, em registros imutáveis no Blob Storage.

## ADDED Requirements

### Requirement: Registro de auditoria antes do commit
Toda construção e toda reconstrução por recálculo SHALL gravar, dentro da transação e antes do commit, um registro imutável no Blob Storage em `auditoria/{nome codificado em URL}/{dataBase}/{AAAAMMDDTHHmmssSSS}_{idAuditoria}.json` (instante no horário de Brasília; pasta pelo nome, que é imutável, para o histórico sobreviver a uma troca de código no cadastro), com escrita condicional `If-None-Match: *` e os campos:
- `idAuditoria` (UUID), `codigo`, `nome`, `dataBase`;
- `operacao`: `CONSTRUCAO` ou `RECONSTRUCAO`;
- `usuario` (usuário ou identidade de serviço autenticada), `instante` (horário de Brasília, com fuso), `correlationId`;
- `idCarga`;
- `hashPontos` gravado e `hashPontosAnterior` (nulo na primeira construção), quantidade de pontos;
- `pontosAnteriores`: lista completa (data e valor) dos pontos substituídos (vazia na primeira construção);
- `proveniencia`: versão do engine, `estadoScript`, avisos, modelos (nome, origem, versão, hash) e todos os itens do cadastro vigente.

Se a gravação no Blob falhar, a operação MUST seguir e confirmar os pontos: o engine SHALL registrar no log o registro completo (inclusive `pontosAnteriores`) no evento `AUDITORIA_PENDENTE`, com nível `ERRO`, e SHALL tentar gravá-lo no Blob em segundo plano a cada 60 segundos até conseguir, registrando `AUDITORIA_GRAVADA`. Se o commit falhar depois da gravação no Blob, o engine SHALL gravar `{mesmo nome}.desfeita.json` com o motivo da falha e registrar o evento de log `AUDITORIA_DESFEITA`. A construção sem recálculo que devolve `EXISTENTE` e a simulação MUST NOT gerar registro. A edição manual de pontos é feita pelo `services/curves` (change `curves-dado-curva`), como operação de contingência, sem registro de auditoria no engine; ela aparece como diferença entre o `hashPontos` gravado e o do último registro de auditoria, e no log do `services/curves`. O engine MUST NOT alterar nem apagar registros de auditoria.

#### Scenario: Construção auditada
- **WHEN** a `PRE` de `2026-09-14` é construída pela carga `B3-TS-20260914-1`
- **THEN** existe um registro `CONSTRUCAO` com a identidade de serviço do processor, o `idCarga`, o `hashPontos`, 278 pontos, `pontosAnteriores` vazia e a proveniência

#### Scenario: Blob indisponível numa reconstrução
- **WHEN** o Blob está inacessível durante uma reconstrução
- **THEN** a reconstrução é concluída, o log tem `AUDITORIA_PENDENTE` com o registro completo, e o registro é gravado no Blob quando ele voltar

#### Scenario: Edição manual sem auditoria
- **WHEN** um ponto da `PRE` de `2026-09-14` é editado à mão no `services/curves` de 14,1670000 para 14,2000000
- **THEN** o engine não grava registro de auditoria, e um recálculo posterior grava em `pontosAnteriores` os pontos editados, incluindo 14,2000000

### Requirement: Resumo da última construção em tCurvaMercd
Na mesma transação de uma construção ou reconstrução bem-sucedida, o engine SHALL atualizar, na linha da curva em `tCurvaMercd` (já travada pela construção):
- `dBaseReft` = a maior entre a data-base construída e o valor atual (nunca retrocede);
- `cUsuarCalc` = usuário ou identidade de serviço que construiu.

O engine MUST NOT alterar nenhuma outra coluna de `tCurvaMercd`. A edição manual de pontos no `services/curves` também não altera essas colunas. O catálogo de curvas SHALL devolver `dBaseReft` como `ultimaDataBase`.

#### Scenario: Reconstrução de data antiga
- **WHEN** a `PRE` tem `dBaseReft` = `2026-09-14` e a data `2026-09-10` é reconstruída
- **THEN** `dBaseReft` continua `2026-09-14`, e `cUsuarCalc` passa a ser o usuário da reconstrução


### Requirement: Consulta do histórico
`GET /api/v1/curvas/{codigo}/{dataBase}/historico` SHALL resolver o nome da curva pelo código e listar os registros de auditoria gravados no Blob da curva e data-base, inclusive os de antes de uma troca de código (registros ainda pendentes só existem no log), do mais recente para o mais antigo, sem `pontosAnteriores`, cada um com a situação `CONFIRMADA` ou `DESFEITA` (quando existir o arquivo `.desfeita.json`). `GET /api/v1/curvas/{codigo}/{dataBase}/historico/{idAuditoria}` SHALL devolver o registro completo, também em `formato=xlsx` (aba `Pontos` com os pontos anteriores e `Resumo` com os demais campos).

#### Scenario: Quem alterou a curva
- **WHEN** o analista consulta o histórico da `PRE` de `2026-09-14` depois de uma construção e um recálculo
- **THEN** a lista tem o registro `RECONSTRUCAO` com o usuário e os dois `hashPontos`, seguido do registro `CONSTRUCAO`

### Requirement: Retenção imutável
O container ou a pasta `auditoria/` SHALL ter política de imutabilidade do Azure Blob Storage (retenção por tempo), configurada na infraestrutura, com o prazo definido pela área de risco. O engine SHALL verificar na subida que tem permissão de escrita em `auditoria/` e, se não tiver, registrar `AUDITORIA_SEM_PERMISSAO` com nível `ERRO`, sem deixar de subir.

#### Scenario: Tentativa de apagar um registro
- **WHEN** alguém tenta apagar um registro de `auditoria/` dentro do prazo de retenção
- **THEN** o Blob Storage recusa a exclusão

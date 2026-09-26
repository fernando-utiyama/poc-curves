## Purpose

Garante que uma curva só é construída depois que o dado bruto da sua origem foi gravado por completo. O processor avisa o engine por webhook que terminou a carga de uma fonte, produto e data-base. O engine registra a carga, constrói as curvas que dependem dela e confere se leu exatamente a quantidade de linhas avisada.

## ADDED Requirements

### Requirement: Webhook de carga concluída
O engine SHALL expor `POST /api/v1/cargas`, com o corpo:

```json
{
  "idCarga": "texto único por carga, até 100 caracteres",
  "fonte": "B3",
  "produto": "TS",
  "dataBase": "2026-09-14",
  "linhasPorCodigo": { "PRE": 278, "DCL": 278 }
}
```

`linhasPorCodigo` SHALL trazer, para cada código na fonte da carga, a quantidade de linhas gravadas na tabela bruta para aquela data-base, inclusive as que o modelo vier a descartar. A rota MUST exigir credencial de serviço do processor. Campo ausente, quantidade negativa, mapa vazio ou data inválida MUST resultar em 400 `PARAMETRO_INVALIDO`. O processor SHALL chamar o webhook só depois do commit das linhas brutas, e repetir a chamada com o mesmo `idCarga` até receber 2xx.

#### Scenario: Aviso da carga B3
- **WHEN** o processor termina de gravar o `TaxaSwap.txt` de `2026-09-14` e chama o webhook com `fonte` = `B3`, `produto` = `TS` e as quantidades por código
- **THEN** o engine registra a carga e constrói as curvas cadastradas com origem `B3`/`TS` cujo código na fonte está na carga

### Requirement: Registro durável da carga
Antes de construir, o engine SHALL gravar a carga no Blob Storage em `cargas/{fonte}/{produto}/{dataBase}.json`, com escrita condicional por ETag, no formato `{ "idCarga", "recebidaEm", "linhasPorCodigo", "curvas": { "{codigo}": { "idCarga", "situacao", "codigoErro", "hashPontos", "em" } }, "historico": [ { "idCarga", "recebidaEm" } ] }`. Uma carga com `idCarga` diferente para a mesma fonte, produto e data-base SHALL substituir `idCarga` e `linhasPorCodigo`, e SHALL acrescentar a anterior ao `historico`. O registro SHALL ser visível a todas as instâncias. Se o Blob estiver inacessível, o webhook MUST NOT falhar: o engine SHALL construir usando a carga recebida no corpo da requisição, registrar `CARGA_NAO_REGISTRADA` com nível `ERRO` e tentar gravar o registro no Blob em segundo plano a cada 60 segundos até conseguir.

#### Scenario: Blob fora ao receber a carga
- **WHEN** o webhook da carga B3 chega com o Blob inacessível
- **THEN** as curvas da carga são construídas e conferidas contra as quantidades do corpo, a resposta é 200, e o log tem `CARGA_NAO_REGISTRADA`

#### Scenario: Instância diferente constrói depois
- **WHEN** o webhook da carga B3 de `2026-09-14` foi recebido pela instância A, e depois uma construção manual da `PRE` dessa data chega à instância B
- **THEN** a instância B encontra a carga registrada e constrói

### Requirement: Construção disparada pela carga
Ao receber uma carga, o engine SHALL processar, na própria requisição, cada curva com origem igual à fonte e ao produto da carga e com código na fonte presente em `linhasPorCodigo`:
- **curva sem pontos gravados na data**: é construída (`CONSTRUIDA`);
- **curva com pontos gravados na data**: MUST NOT ser reconstruída, qualquer que seja o `idCarga`, e é devolvida como `EXISTENTE`.

A carga nunca recalcula uma curva: recálculo só acontece por `POST .../construcao?forcarRecalculo=true`. Quando a carga tem `idCarga` diferente do que gerou os pontos gravados de uma curva (republicação), e o registro da carga pôde ser lido, o engine SHALL devolver essa curva como `EXISTENTE` com o aviso `PONTOS_DE_CARGA_ANTERIOR`, informando os dois `idCarga`, e SHALL registrar o evento `CARGA_REPUBLICADA` no log com nível `AVISO`. O `idCarga` que gerou os pontos de cada curva SHALL ser guardado em `curvas` no registro da carga, a cada construção bem-sucedida, inclusive as feitas pela API.

Cada curva SHALL ser construída de forma independente: a falha de uma MUST NOT impedir as outras. O resultado de cada curva SHALL ser gravado em `curvas` no registro da carga. A resposta SHALL ser 200 com o `idCarga` e, por curva, o código, a situação ou o `codigoErro` e a mensagem, e o `hashPontos`. Falha de construção de uma curva é resultado de negócio, não erro do webhook. Um código na fonte presente na carga sem nenhuma curva cadastrada SHALL gerar um evento `AVISO` no log, sem erro.

#### Scenario: Retry do processor
- **WHEN** o processor repete o webhook com o mesmo `idCarga` depois de um tempo esgotado, e a `PRE` já tinha sido construída com sucesso para essa carga
- **THEN** a `PRE` é devolvida como `EXISTENTE`, sem reconstruir, e as curvas que faltavam são construídas

#### Scenario: Republicação pela B3
- **WHEN** chega uma carga B3 de `2026-09-14` com `idCarga` diferente do que gerou os pontos gravados da `PRE`
- **THEN** a `PRE` não é reconstruída, é devolvida como `EXISTENTE` com o aviso `PONTOS_DE_CARGA_ANTERIOR`, a carga anterior vai para o `historico` e o log tem `CARGA_REPUBLICADA`

#### Scenario: Recálculo forçado depois da republicação
- **WHEN** depois da republicação o operador chama `POST /api/v1/curvas/PRE/2026-09-14/construcao?forcarRecalculo=true`
- **THEN** a `PRE` é reconstruída com as linhas da carga nova, conferidas contra a quantidade da carga nova, e o registro passa a indicar o `idCarga` novo para a `PRE`

#### Scenario: Uma curva falha
- **WHEN** a construção da `DPL` falha por `INSUMO_INVALIDO` durante o processamento de uma carga
- **THEN** as outras curvas da carga são construídas, a resposta é 200 com o erro da `DPL`, e o log tem `CONSTRUCAO_FALHOU` da `DPL`

### Requirement: Construção exige carga concluída
Toda construção, disparada pelo webhook ou por `POST .../construcao`, MUST falhar com `CARGA_NAO_CONCLUIDA` quando o registro de cargas pôde ser lido e não há carga registrada para a fonte, o produto e a data-base da origem da curva, ou o código na fonte da curva não está em `linhasPorCodigo`. Se o registro não puder ser lido por falha do Blob, a construção por `POST .../construcao` SHALL seguir sem conferir a carga, com o aviso `CARGA_NAO_VERIFICADA` na resposta, no log, na auditoria e na memória de cálculo; a construção pelo webhook usa a carga do corpo. A simulação SHALL rodar mesmo sem carga concluída e SHALL informar no `Resumo` se havia carga registrada e o `idCarga`. Havendo carga registrada, a simulação SHALL aplicar a mesma conferência de quantidade da construção.

#### Scenario: Construção manual com o Blob fora
- **WHEN** a construção da `PRE` é pedida pela API com o Blob inacessível
- **THEN** a curva é construída sem conferência de carga, e a resposta traz o aviso `CARGA_NAO_VERIFICADA`

#### Scenario: Construção antes do aviso
- **WHEN** a construção da `PRE` de `2026-09-15` é pedida antes do webhook da carga B3 dessa data
- **THEN** a construção falha com `CARGA_NAO_CONCLUIDA`, e nada é gravado

### Requirement: Conferência da quantidade lida
Antes de executar o modelo, o pipeline SHALL contar as linhas brutas lidas para a curva e a data-base. Se a contagem for diferente de `linhasPorCodigo[código na fonte]` da carga registrada, a construção MUST falhar com `INSUMO_INCOMPLETO`, informando a quantidade lida e a avisada. A contagem SHALL incluir as linhas que o modelo descarta depois.

#### Scenario: Leitura parcial
- **WHEN** a carga avisou 278 linhas da `PRE`, mas a leitura encontra 150
- **THEN** a construção falha com `INSUMO_INCOMPLETO`, informando 150 lidas e 278 avisadas

### Requirement: Log da carga
O engine SHALL registrar o evento `CARGA_RECEBIDA` (`idCarga`, fonte, produto, data-base, `linhasPorCodigo`, se é nova, repetida ou republicação), um `CARGA_REPUBLICADA` por curva mantida com pontos de carga anterior e, ao final, `CARGA_PROCESSADA` (`idCarga`, quantidade de curvas por situação, por aviso e por código de erro, duração).

#### Scenario: Carga com falha parcial
- **WHEN** uma carga termina com 4 curvas construídas e 1 com erro
- **THEN** o log tem `CARGA_PROCESSADA` com 4 sucessos e 1 erro, e o código de erro

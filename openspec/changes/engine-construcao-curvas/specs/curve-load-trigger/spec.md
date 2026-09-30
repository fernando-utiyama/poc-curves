## Purpose

Garante que uma curva só é construída automaticamente depois que o dado bruto da sua origem foi gravado por completo. O processor grava cada carga numa única transação e, depois do commit, avisa o engine por webhook, com a quantidade de linhas por código; e o orquestrador pede a construção automática de uma data inteira, que cobre as curvas derivadas e serve de rede de segurança. O engine constrói na hora as curvas que dependem da carga, confere se leu exatamente a quantidade avisada e não guarda nenhum registro próprio da carga: o que ele precisa saber depois está no banco (dados brutos e pontos) e no log.

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

`linhasPorCodigo` SHALL trazer, para cada código na fonte da carga, a quantidade de linhas gravadas na tabela bruta para aquela data-base, inclusive as que o modelo vier a descartar. A rota MUST exigir credencial de serviço do processor. Campo ausente, quantidade negativa, mapa vazio ou data inválida MUST resultar em 400 `PARAMETRO_INVALIDO`. O processor SHALL gravar todas as linhas da carga numa única transação, chamar o webhook só depois do commit e repetir a chamada com o mesmo `idCarga` até receber 2xx. O engine MUST NOT gravar a carga em lugar nenhum: o corpo da requisição é usado só durante o seu processamento, e o `idCarga` vai para o log.

#### Scenario: Aviso da carga B3
- **WHEN** o processor termina de gravar o `TaxaSwap.txt` de `2026-09-14` e chama o webhook com `fonte` = `B3`, `produto` = `TS` e as quantidades por código
- **THEN** o engine constrói as curvas cadastradas com origem `B3`/`TS` cujo código na fonte está na carga, e o log tem `CARGA_RECEBIDA` com o `idCarga`

### Requirement: Construção disparada pela carga
Ao receber uma carga, o engine SHALL processar, na própria requisição, cada curva cuja **origem principal** tem a fonte e o produto da carga e um código na fonte presente em `linhasPorCodigo`. A fonte e o produto da carga só **selecionam** as curvas: a construção SHALL usar a origem principal, como qualquer construção automática, e MUST NOT ser tratada como construção por origem secundária (sem o aviso `ORIGEM_SECUNDARIA`, e com `principal` verdadeiro na origem do `CURVA_GRAVADA`). Um mesmo código na fonte pode estar em mais de uma curva cadastrada (ex.: duas curvas de configurações diferentes sobre o mesmo código da B3): todas SHALL ser processadas, cada uma na sua transação. Uma curva que tem a carga só como origem secundária MUST NOT ser construída pela carga (o dado bruto fica gravado para construção pela API):
- **curva com `cSitReg` = `INATIVO`, ou com a data-base fora da vigência da curva** (`dInicVgcia` a `dValidAte` em `tCurvaMercd`): MUST NOT ser construída, e é devolvida como `IGNORADA`, com o motivo `CURVA_INATIVA` ou `FORA_DA_VIGENCIA_CURVA`. Não é erro: o usuário ainda pode construí-la por `POST .../construcao`;
- **curva sem pontos gravados na data**: é construída (`CONSTRUIDA`);
- **curva com pontos gravados na data**: MUST NOT ser reconstruída, e é devolvida como `EXISTENTE`.

A carga nunca recalcula uma curva: recálculo só acontece por `POST .../construcao?forcarRecalculo=true`. Para cada curva `EXISTENTE`, o engine SHALL executar o modelo sobre os dados brutos atuais, sem gravar (como a simulação), e comparar o `hashPontos` resultante com o gravado. Se forem diferentes (a fonte republicou, os pontos foram editados à mão ou o cadastro mudou), a curva SHALL ser devolvida com o aviso `PONTOS_DIFERENTES_DA_FONTE`, com a quantidade de pontos diferentes, e o log SHALL ter o evento `PONTOS_DIFERENTES_DA_FONTE` com nível `AVISO`. Se a comparação não puder ser feita (o modelo falha sobre os dados brutos atuais, a trava não é obtida, o dado bruto sumiu), a curva continua `EXISTENTE` (os pontos gravados não mudam), mas SHALL vir com o aviso `COMPARACAO_INDISPONIVEL`, com o `codigoErro` e a mensagem da falha, e o log SHALL ter o evento `COMPARACAO_INDISPONIVEL` com nível `AVISO`: a falha MUST NOT ser escondida, nem virar erro da curva.

Cada curva SHALL ser construída de forma independente: a falha de uma MUST NOT impedir as outras. A resposta SHALL ser 200 com o `idCarga` e, por curva, o código, a situação ou o `codigoErro` e a mensagem, os avisos e o `hashPontos`. Falha de construção de uma curva é resultado de negócio, não erro do webhook. Um código na fonte presente na carga sem nenhuma curva cadastrada SHALL gerar um evento `AVISO` no log, sem erro.

#### Scenario: Carga pela origem principal
- **WHEN** a carga B3/`TS` de `2026-09-14` constrói a `PRE`, cuja origem principal é B3/`TS`/`PRE`
- **THEN** a resposta e o `CURVA_GRAVADA` trazem a origem B3/`TS`/`PRE` com `principal` verdadeiro, sem o aviso `ORIGEM_SECUNDARIA`

#### Scenario: Duas curvas no mesmo código
- **WHEN** as curvas `PRE` e `PRE_252` têm como origem principal o mesmo código `PRE` da B3, e chega a carga com `PRE` em `linhasPorCodigo`
- **THEN** as duas são construídas, cada uma com o seu resultado na resposta

#### Scenario: Comparação que falha
- **WHEN** a `DPL` de `2026-09-14` já tem pontos, e a construção da data compara com a fonte atual, mas o modelo falha com `INSUMO_INVALIDO`
- **THEN** a `DPL` volta como `EXISTENTE` com o aviso `COMPARACAO_INDISPONIVEL` trazendo `INSUMO_INVALIDO`, e o log registra `COMPARACAO_INDISPONIVEL`

#### Scenario: Retry do processor
- **WHEN** o processor repete o webhook com o mesmo `idCarga` depois de um tempo esgotado, e a `PRE` já tinha sido construída com sucesso
- **THEN** a `PRE` é devolvida como `EXISTENTE`, sem reconstruir e sem aviso, e as curvas que faltavam são construídas

#### Scenario: Republicação pela B3
- **WHEN** a B3 republica o arquivo de `2026-09-14` com a taxa de um vértice da `PRE` corrigida, o processor substitui as linhas brutas e chama o webhook com um `idCarga` novo
- **THEN** a `PRE` não é reconstruída, é devolvida como `EXISTENTE` com o aviso `PONTOS_DIFERENTES_DA_FONTE` e 1 ponto diferente, e o log tem o evento com o `idCarga` novo

#### Scenario: Recálculo forçado depois da republicação
- **WHEN** depois da republicação o operador chama `POST /api/v1/curvas/PRE/2026-09-14/construcao?forcarRecalculo=true`
- **THEN** a `PRE` é reconstruída com as linhas brutas atuais, e um novo webhook da mesma carga devolveria a `PRE` como `EXISTENTE` sem aviso

#### Scenario: Curva inativa na carga
- **WHEN** a carga B3 de `2026-10-11` traz o código `SLP`, e a curva `SLP` foi inativada no cadastro
- **THEN** a `SLP` não é construída e é devolvida como `IGNORADA` com o motivo `CURVA_INATIVA`, e as outras curvas da carga são construídas

#### Scenario: Usuário constrói a curva inativa
- **WHEN** depois disso o operador chama `POST /api/v1/curvas/SLP/2026-10-11/construcao`
- **THEN** a `SLP` é construída (`CONSTRUIDA`) com o aviso `CURVA_INATIVA`

#### Scenario: Uma curva falha
- **WHEN** a construção da `DPL` falha por `INSUMO_INVALIDO` durante o processamento de uma carga
- **THEN** as outras curvas da carga são construídas, a resposta é 200 com o erro da `DPL`, e o log tem `CONSTRUCAO_FALHOU` da `DPL`

### Requirement: Construção automática da data pelo orquestrador
O engine SHALL expor `POST /api/v1/construcoes/{dataBase}`, sem corpo e sem parâmetros de query, exigindo o papel `Curvas.Orquestrador`. A rota é o segundo gatilho automático, ao lado do webhook do processor: o processor dispara as curvas de dado de mercado da carga que gravou, e o orquestrador dispara a data inteira, o que cobre as curvas derivadas cuja curva componente foi construída ou recalculada fora de uma carga e serve de rede de segurança se o aviso de uma carga se perder.

Ao receber a chamada, o engine SHALL processar, na própria requisição, toda curva com código não nulo, com as mesmas regras da carga (requisito "Construção disparada pela carga"):
- curva inativa ou com a data-base fora da vigência: `IGNORADA`, com o motivo;
- curva com pontos gravados na data: `EXISTENTE`, sem reconstruir, com a comparação com a fonte atual e o aviso `PONTOS_DIFERENTES_DA_FONTE` quando diferirem;
- curva sem pontos e com insumo (para origem de provedor, ao menos uma linha bruta da origem na data; para curva derivada, todas as curvas componentes com pontos gravados na data): construída (`CONSTRUIDA`);
- curva sem pontos e sem insumo: MUST NOT ser construída, e é devolvida como `SEM_INSUMO`, sem erro e sem log de falha.

Primeiro SHALL ser processadas as curvas com origem de provedor, em paralelo, com até `engine.construcao-data.paralelismo` (padrão 8) ao mesmo tempo; depois, as derivadas, em cadeia, na ordem das dependências, como no requisito "Construção em cadeia das curvas derivadas". Não há conferência de quantidade (`INSUMO_INCOMPLETO`), porque não há quantidade avisada: vale o que está gravado, como na construção pela API. A rota MUST NOT recalcular nenhuma curva e MUST NOT guardar registro da chamada. A falha de uma curva MUST NOT impedir as demais.

A resposta SHALL ser 200 com a data-base e, por curva, o código, a situação ou o `codigoErro` e a mensagem, o motivo de `IGNORADA`, os avisos e o `hashPontos`. Os `CURVA_GRAVADA` das curvas construídas SHALL ter `acionadoPor` = `ORQUESTRADOR`. O engine SHALL registrar `CONSTRUCAO_DATA_RECEBIDA` (data-base, usuário) e, ao final, `CONSTRUCAO_DATA_PROCESSADA` (data-base, quantidade de curvas por situação, por aviso e por código de erro, duração).

Se o webhook do processor e esta rota construírem a mesma curva e data ao mesmo tempo, a trava da curva (spec `curve-build-pipeline`) serializa as duas: a existência de pontos SHALL ser conferida depois de obter a trava, e a segunda a obter a trava devolve `EXISTENTE`.

#### Scenario: Derivada depois de uma curva componente construída à mão
- **WHEN** a carga ANBIMA de `2026-09-14` falhou, o operador construiu a `NTN-B` pela API, e depois o orquestrador chama `POST /api/v1/construcoes/2026-09-14`
- **THEN** a derivada com componentes `DIxPRE` e `NTN-B` é construída com `acionadoPor` = `ORQUESTRADOR`, e as curvas já construídas vêm como `EXISTENTE`

#### Scenario: Aviso de carga perdido
- **WHEN** o processor gravou a carga B3 de `2026-09-14`, mas o webhook nunca chegou ao engine, e o orquestrador chama a rota da data
- **THEN** as curvas B3 são construídas, e as curvas sem dado bruto na data vêm como `SEM_INSUMO`

#### Scenario: Chamada repetida
- **WHEN** o orquestrador chama a rota duas vezes para a mesma data, sem mudança de insumo
- **THEN** a segunda resposta traz todas as curvas construídas como `EXISTENTE`, sem aviso, e nada é gravado

#### Scenario: Webhook e orquestrador ao mesmo tempo
- **WHEN** o webhook da carga B3 e a rota do orquestrador tentam construir a `PRE` de `2026-09-14` ao mesmo tempo
- **THEN** uma das duas constrói a `PRE`, a outra espera a trava e devolve `EXISTENTE`, e `tDadoVertcCurva` tem uma única vez os 278 pontos

### Requirement: Dados brutos exigidos na construção
A construção de uma curva com origem de provedor, pela carga ou por `POST .../construcao`, SHALL ler os dados brutos da origem na data-base diretamente do banco: como o processor grava cada carga numa única transação, dado bruto presente é carga completa. Sem nenhuma linha da origem na data, a construção MUST falhar com `INSUMO_AUSENTE`. A construção de uma curva derivada MUST falhar com `CURVA_COMPONENTE_NAO_CONSTRUIDA`, listando as curvas componentes sem pontos na data, quando alguma curva componente não tiver pontos gravados na data. A simulação SHALL rodar nas mesmas condições e mostrar o erro no `Resumo`.

#### Scenario: Construção antes da carga
- **WHEN** a construção da `PRE` de `2026-09-15` é pedida antes de o processor gravar a carga B3 dessa data
- **THEN** a construção falha com `INSUMO_AUSENTE`, e nada é gravado

### Requirement: Conferência da quantidade lida na carga
Na construção disparada pelo webhook, antes de executar o modelo, o pipeline SHALL contar as linhas brutas lidas para a curva e a data-base. Se a contagem for diferente de `linhasPorCodigo[código na fonte]` do corpo, a construção MUST falhar com `INSUMO_INCOMPLETO`, informando a quantidade lida e a avisada. A contagem SHALL incluir as linhas que o modelo descarta depois. A conferência existe para a carga duplicada ou atropelada (duas cargas da mesma data, com o aviso da primeira chegando depois da gravação da segunda): nesse caso a construção daquele aviso é bloqueada, e o aviso da carga seguinte constrói. A construção por `POST .../construcao` e a construção da data pelo orquestrador não têm quantidade avisada e usam exatamente o que está gravado: o que o usuário deixou gravado é o certo, e uma linha a menos gera a curva com um ponto a menos.

#### Scenario: Leitura parcial
- **WHEN** a carga avisou 278 linhas da `PRE`, mas a leitura encontra 150
- **THEN** a construção falha com `INSUMO_INCOMPLETO`, informando 150 lidas e 278 avisadas

#### Scenario: Aviso atrasado de uma carga substituída
- **WHEN** o aviso da carga 1 de `2026-09-14` (278 linhas da `PRE`) chega depois de a carga 2 da mesma data ter gravado 279 linhas
- **THEN** a `PRE` falha nesse aviso com `INSUMO_INCOMPLETO`, e o aviso da carga 2 constrói a `PRE` com os 279 vértices

#### Scenario: Linha a menos deixada pelo usuário
- **WHEN** a `PRE` de `2026-09-14` tem 277 linhas brutas gravadas porque o usuário retirou uma, e o operador pede `POST /api/v1/curvas/PRE/2026-09-14/construcao?forcarRecalculo=true`
- **THEN** a `PRE` é construída com 277 pontos, sem conferência de quantidade

### Requirement: Log da carga
O engine SHALL registrar o evento `CARGA_RECEBIDA` (`idCarga`, fonte, produto, data-base, `linhasPorCodigo`), um `PONTOS_DIFERENTES_DA_FONTE` por curva mantida com pontos diferentes dos que a fonte atual produz e, ao final, `CARGA_PROCESSADA` (`idCarga`, quantidade de curvas por situação, inclusive `IGNORADA`, por aviso e por código de erro, duração).

#### Scenario: Carga com falha parcial
- **WHEN** uma carga termina com 4 curvas construídas e 1 com erro
- **THEN** o log tem `CARGA_PROCESSADA` com 4 sucessos e 1 erro, e o código de erro

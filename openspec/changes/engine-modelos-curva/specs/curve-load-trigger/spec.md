## Purpose

Garante que uma curva só é construída automaticamente depois que o dado bruto da sua origem foi gravado por completo. O processor grava cada carga numa única transação e, depois do commit, avisa o engine por webhook, com a quantidade de linhas por código. O engine constrói na hora as curvas que dependem da carga, confere se leu exatamente a quantidade avisada e não guarda nenhum registro próprio da carga: o que ele precisa saber depois está no banco (dados brutos e pontos) e no log.

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
Ao receber uma carga, o engine SHALL processar, na própria requisição, cada curva com origem igual à fonte e ao produto da carga e com código na fonte presente em `linhasPorCodigo`:
- **curva com `cSitReg` = `INATIVO`, ou com a data-base fora da vigência da curva** (`dInicVgcia` a `dValidAte` em `tCurvaMercd`): MUST NOT ser construída, e é devolvida como `IGNORADA`, com o motivo `CURVA_INATIVA` ou `FORA_DA_VIGENCIA_CURVA`. Não é erro: o usuário ainda pode construí-la por `POST .../construcao`;
- **curva sem pontos gravados na data**: é construída (`CONSTRUIDA`);
- **curva com pontos gravados na data**: MUST NOT ser reconstruída, e é devolvida como `EXISTENTE`.

A carga nunca recalcula uma curva: recálculo só acontece por `POST .../construcao?forcarRecalculo=true`. Para cada curva `EXISTENTE`, o engine SHALL executar o modelo sobre os dados brutos atuais, sem gravar (como a simulação), e comparar o `hashPontos` resultante com o gravado. Se forem diferentes (a fonte republicou, os pontos foram editados à mão ou o cadastro mudou), a curva SHALL ser devolvida com o aviso `PONTOS_DIFERENTES_DA_FONTE`, com a quantidade de pontos diferentes, e o log SHALL ter o evento `PONTOS_DIFERENTES_DA_FONTE` com nível `AVISO`.

Cada curva SHALL ser construída de forma independente: a falha de uma MUST NOT impedir as outras. A resposta SHALL ser 200 com o `idCarga` e, por curva, o código, a situação ou o `codigoErro` e a mensagem, os avisos e o `hashPontos`. Falha de construção de uma curva é resultado de negócio, não erro do webhook. Um código na fonte presente na carga sem nenhuma curva cadastrada SHALL gerar um evento `AVISO` no log, sem erro.

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

### Requirement: Construção em cadeia das curvas derivadas
Depois de processar as curvas de uma carga, na mesma requisição, o engine SHALL construir cada curva derivada (spec `curve-build-pipeline`), ativa e dentro da vigência, que ainda não tem pontos na data e cujas mães têm todas pontos gravados na data. Uma derivada construída pode liberar outra, que a tem como mãe: o processo SHALL repetir até não haver mais derivada a construir, na ordem das dependências. A cadeia segue as regras da carga: nunca recalcula, uma falha não impede as demais, e cada resultado entra na resposta do webhook e no log. Uma derivada que já tem pontos SHALL ser comparada como as demais: se os pontos que as mães atuais produziriam forem diferentes dos gravados, ela recebe o aviso `PONTOS_DIFERENTES_DA_FONTE`.

Recalcular ou editar à mão uma mãe MUST NOT reconstruir a derivada: ela fica com os pontos da construção anterior, e a diferença aparece na comparação com as mães atuais (painel do `services/curves`). Recalcular a derivada é ação do usuário, por `POST .../construcao?forcarRecalculo=true`.

#### Scenario: Derivada construída depois das mães
- **WHEN** existe uma curva derivada com mães `DIxPRE` (carga B3) e `NTN-B` (carga ANBIMA), e a carga B3 chega antes da ANBIMA
- **THEN** a derivada não é construída na carga B3, e é construída na carga ANBIMA, logo depois da `NTN-B`, com as mães e os seus `hashPontos` na proveniência

#### Scenario: Mãe recalculada depois
- **WHEN** a `DIxPRE` de uma data é recalculada depois da construção da derivada, com valores diferentes
- **THEN** a derivada não é reconstruída, e a sua simulação passa a dar pontos diferentes dos gravados

### Requirement: Dados brutos exigidos na construção
A construção de uma curva com origem de provedor, pela carga ou por `POST .../construcao`, SHALL ler os dados brutos da origem na data-base diretamente do banco: como o processor grava cada carga numa única transação, dado bruto presente é carga completa. Sem nenhuma linha da origem na data, a construção MUST falhar com `INSUMO_AUSENTE`. A construção de uma curva derivada MUST falhar com `CURVA_MAE_NAO_CONSTRUIDA`, listando as mães sem pontos na data, quando alguma mãe não tiver pontos gravados na data. A simulação SHALL rodar nas mesmas condições e mostrar o erro no `Resumo`.

#### Scenario: Construção antes da carga
- **WHEN** a construção da `PRE` de `2026-09-15` é pedida antes de o processor gravar a carga B3 dessa data
- **THEN** a construção falha com `INSUMO_AUSENTE`, e nada é gravado

### Requirement: Conferência da quantidade lida na carga
Na construção disparada pelo webhook, antes de executar o modelo, o pipeline SHALL contar as linhas brutas lidas para a curva e a data-base. Se a contagem for diferente de `linhasPorCodigo[código na fonte]` do corpo, a construção MUST falhar com `INSUMO_INCOMPLETO`, informando a quantidade lida e a avisada. A contagem SHALL incluir as linhas que o modelo descarta depois. A construção por `POST .../construcao` não tem quantidade avisada e lê o que está gravado.

#### Scenario: Leitura parcial
- **WHEN** a carga avisou 278 linhas da `PRE`, mas a leitura encontra 150
- **THEN** a construção falha com `INSUMO_INCOMPLETO`, informando 150 lidas e 278 avisadas

### Requirement: Log da carga
O engine SHALL registrar o evento `CARGA_RECEBIDA` (`idCarga`, fonte, produto, data-base, `linhasPorCodigo`), um `PONTOS_DIFERENTES_DA_FONTE` por curva mantida com pontos diferentes dos que a fonte atual produz e, ao final, `CARGA_PROCESSADA` (`idCarga`, quantidade de curvas por situação, inclusive `IGNORADA`, por aviso e por código de erro, duração).

#### Scenario: Carga com falha parcial
- **WHEN** uma carga termina com 4 curvas construídas e 1 com erro
- **THEN** o log tem `CARGA_PROCESSADA` com 4 sucessos e 1 erro, e o código de erro

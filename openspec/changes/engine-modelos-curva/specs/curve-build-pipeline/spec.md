## Purpose

Constrói e grava os pontos de uma curva numa data a partir do cadastro da curva, e interpola esses pontos sob demanda. Define de forma fechada as unidades, as contagens de tempo, as fórmulas de cotação, interpolação e extrapolação, o domínio, o arredondamento e os erros, para que a mesma entrada produza sempre a mesma saída em qualquer implementação.

## ADDED Requirements

### Requirement: Curva derivada de outras curvas
Uma curva SHALL poder ser derivada de outras curvas de mercado já construídas (ex.: inflação implícita = PRE sobre a NTN-B bootstrapada), sem mudança de schema. Uma curva é **derivada** quando a sua ligação de menor `cPriorCsumo` em `tCurvaPrvdr` tem `iPrvdrDados` = `TCEN`, um provedor interno. Nesse caso, **todas** as ligações da curva com `iPrvdrDados` = `TCEN` são as **curvas componentes**: `cTickerPrvdr` = nome da curva componente (`tCurvaMercd.cTickerIndcd`) e `cPrvdrMercd` = papel da curva componente no cálculo (ex.: `NUMERADOR`, `DENOMINADOR`), definido pelo modelo de construção. Ligações de outros provedores na mesma curva são ignoradas nesta fase.

O cadastro de uma curva derivada MUST ser rejeitado com `CADASTRO_INVALIDO` quando:
- uma curva componente não existir;
- a curva for componente dela mesma, direta ou indiretamente (ciclo);
- o modelo de construção não aceitar a fonte `TCEN`, ou os papéis cadastrados não forem exatamente os que o modelo declara.

Nenhum modelo de construção nativo desta fase aceita a fonte `TCEN`: a estrutura existe para que um modelo derivado (Java numa mudança futura ou script Groovy) seja incluído só com cadastro e o modelo. O modelo derivado lê as curvas componentes pelo contexto de construção (spec `curve-extension-models`), nunca pelas tabelas brutas. A disparada em cadeia e a exigência de componentes construídas estão na spec `curve-load-trigger`. A proveniência da construção de uma curva derivada SHALL trazer, para cada curva componente, nome, papel e `hashPontos` dos pontos usados.

#### Scenario: Inflação implícita cadastrada sem modelo
- **WHEN** a curva `IPCA_IMPLICITA` é cadastrada com as ligações (`TCEN`, `NUMERADOR`, `DIxPRE`, 1) e (`TCEN`, `DENOMINADOR`, `NTN-B`, 2) e um modelo de construção que ainda não existe
- **THEN** a construção falha com `CADASTRO_INVALIDO` informando o modelo, e nenhuma outra curva é afetada

#### Scenario: Ciclo entre curvas
- **WHEN** a curva `A` tem `B` como componente, e `B` é cadastrada com `A` como componente
- **THEN** a construção de qualquer das duas falha com `CADASTRO_INVALIDO`, citando o ciclo `A` → `B` → `A`

### Requirement: Construção por uma origem secundária
Uma curva MAY ter mais de uma ligação de provedor em `tCurvaPrvdr`: a de menor `cPriorCsumo` é a origem principal, e as demais são **origens secundárias**, cujos dados brutos também são gravados pelos feeders (o processor grava os vértices para toda curva ligada ao código, principal ou não). A construção automática (carga e construção da data pelo orquestrador) SHALL usar sempre a origem principal. O usuário SHALL poder construir ou recalcular a curva a partir de uma origem secundária, informando `fonte` e `produto` em `POST .../construcao` (spec `curve-engine-api`), e simular por ela da mesma forma.

Com `fonte` e `produto` informados:
- a origem usada SHALL ser a ligação da curva em `tCurvaPrvdr` com `iPrvdrDados` = `fonte` e `cPrvdrMercd` = `produto`, e `cTickerPrvdr` dela é o código na fonte. Sem nenhuma ligação assim, MUST falhar com `CADASTRO_INVALIDO`, listando as origens cadastradas da curva; com mais de uma, também `CADASTRO_INVALIDO`. A fonte `TCEN` MUST NOT ser informada: as curvas componentes de uma curva derivada não são uma origem selecionável;
- o modelo de construção SHALL ser `MODELOS_POR_ORIGEM["{fonte}/{produto}"]` do `cModDado` quando a chave existir, e `cMotorCalc` quando não existir. O modelo escolhido SHALL aceitar a fonte e o produto informados, senão `CADASTRO_INVALIDO`;
- todo o resto do cadastro (unidade, cotação, base de interpolação, interpolador, calendário, extrapolação, horizonte e arredondamento) SHALL ser o da curva, o mesmo da origem principal: a curva é uma só, e muda só de onde vêm os pontos;
- as regras de gravação, trava, recálculo e situação são as da construção pela API. Com pontos gravados e sem `forcarRecalculo=true`, a resposta é `EXISTENTE`, e a comparação SHALL ser feita contra o que a origem informada produz.

A proveniência, a resposta, a memória de cálculo e o `CURVA_GRAVADA` SHALL informar a origem usada (fonte, produto, código na fonte e prioridade) e o aviso `ORIGEM_SECUNDARIA`. O banco não guarda de qual origem vieram os pontos gravados (o schema não muda): a construção automática, a rota de situação e o arquivo de auditoria comparam sempre com a origem principal, e uma data construída pela secundária aparece como diferente da fonte até ser recalculada pela principal. Quem construiu pela secundária, e quando, está no `CURVA_GRAVADA`.

#### Scenario: Curva reconstruída pela fonte secundária
- **WHEN** a curva `DI_BACKUP` tem origem principal `ANBIMA`/`CZ` (prioridade 1) e secundária `B3`/`TS`/`PRE` (prioridade 2), com `MODELOS_POR_ORIGEM` = `{"B3/TS":"PRONTA_TS_B3"}`, a carga ANBIMA de `2026-09-14` não veio, e o operador chama `POST /api/v1/curvas/DI_BACKUP/2026-09-14/construcao?fonte=B3&produto=TS`
- **THEN** a curva é construída com `PRONTA_TS_B3` a partir das 278 linhas de `tBtrsCurvaPrimr` gravadas sob `DI_BACKUP`, com o interpolador e o arredondamento do cadastro da curva, o aviso `ORIGEM_SECUNDARIA`, e o `CURVA_GRAVADA` traz a origem `B3`/`TS`/`PRE`, prioridade 2

#### Scenario: Volta para a origem principal
- **WHEN** a carga ANBIMA de `2026-09-14` chega depois disso
- **THEN** a carga não reconstrói a `DI_BACKUP`, que vem como `EXISTENTE` com o aviso `PONTOS_DIFERENTES_DA_FONTE`, e o operador recalcula pela principal com `forcarRecalculo=true`, sem `fonte` e `produto`

#### Scenario: Origem não cadastrada
- **WHEN** o operador pede a construção da `PRE` com `fonte=BLOOMBERG&produto=BLC2`, e a `PRE` só tem a origem `B3`/`TS`
- **THEN** a resposta é 422 com `CADASTRO_INVALIDO`, listando as origens cadastradas da `PRE`

#### Scenario: Modelo que não aceita a origem
- **WHEN** a `DI_BACKUP` não tem `MODELOS_POR_ORIGEM`, o `cMotorCalc` é um modelo da ANBIMA, e o operador pede a construção por `B3`/`TS`
- **THEN** a resposta é 422 com `CADASTRO_INVALIDO`, informando que o modelo não aceita `B3`/`TS` e que falta `MODELOS_POR_ORIGEM` para essa origem


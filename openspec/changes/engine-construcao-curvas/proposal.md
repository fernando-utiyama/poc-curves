## Why

O primeiro objetivo do projeto é entregar 7 curvas: DIxPRE, DCL, PTAX, DPL e IBOVESPA (Taxa Swap B3), NTN-B (ANBIMA) e SOFR (Bloomberg). A change `engine-modelos-curva` cobre isso e muito mais (scripts Groovy, memória de cálculo em planilha, auditoria, resiliência, calendário por planilha), e ficou grande demais para uma entrega. Esta change é a primeira parte: o mínimo para construir, gravar, consultar e interpolar as 7 curvas com os modelos e calendários nativos, mais as rotas básicas que os outros serviços chamam. O resto fica na `engine-modelos-curva`, que vem depois. O design e o guia de implementação, comuns às duas, estão nesta change.

## What Changes

- **Escopo desta entrega:** onde um requisito desta change cita algo da segunda parte (simulação, `formato=xlsx`/`zip`, memória de cálculo, scripts Groovy e Blob, auditoria, origem secundária, curva derivada), isso passa a valer com a change `engine-modelos-curva`. Até lá: só modelos, interpoladores e calendários nativos, proveniência sempre com origem `JAVA` e `estadoScript` = `ATUAL`, construção só pela origem principal, e curva derivada recusada com `CADASTRO_INVALIDO`. O contrato do modelo recebe a `MemoriaCalculo`; nesta parte ela pode ser só o acumulador, sem planilha.
- **Pipeline guiado pelo cadastro:** ler o cadastro (`tCurvaMercd`, provedor da curva principal em `tCurvaPrvdr`, configuração vigente em `tConfgCurva` e parâmetros em `cModDado`) → executar o modelo de construção → arredondar → gravar os pontos em `tDadoVertcCurva` e a curva interpolada em `tDadoCurva`, sob a trava da curva, e atualizar `dBaseReft` e `cUsuarCalc` em `tCurvaMercd`.
- **Tipos com nomes do QuantLib e matemática decimal** (`Compounding`, `Frequency`, `BusinessDayConvention`, `DayCounter`, `InterestRate`, `DecimalMath` pelo `StrictMath`).
- **Calendários nativos** `Brazil`/`Settlement` e `UnitedStates`/`FederalReserve`.
- **Interpolação e extrapolação:** bases de interpolação, interpoladores, extrapolação por lado, domínio, dias úteis publicados pela fonte, pontos no mesmo prazo.
- **Modelos nativos das 7 curvas:** `PRONTA_TS_B3` (as 5 da B3), `NTNB_BOOTSTRAP_ANBIMA` e `SOFR_ZERO_BLOOMBERG`, com os contratos de modelo que os scripts Groovy da segunda parte vão estender.
- **`hashPontos`**, proveniência e log estruturado da construção.
- **Rotas básicas para os outros serviços:**
  - processor: `POST /api/v1/cargas` (construção disparada pela carga, com comparação sem recálculo para curvas já construídas);
  - construção da data, sob demanda (operador ou outro serviço; o orquestrador não chama): `POST /api/v1/construcoes/{dataBase}`;
  - `services/curves`: `GET /api/v1/valores-cadastro`, `GET /api/v1/curvas/situacao`, `POST .../interpolada` e `GET /api/v1/calendarios/{nome}` em JSON;
  - front e usuários: catálogo, construção, consulta e interpolação por código e por nome.
- **Contrato de tipos, erros padronizados e correlação**, leitura em `READ COMMITTED` e fuso da JVM em `America/Sao_Paulo`.
- **Correções da revisão de 2026-09-30** que tocam a construção (vetores reais como teste, 30/360, carga pela origem principal, trava, comparação, erros, nomes, código morto).

## Capabilities

### New Capabilities
- `curve-build-pipeline`: cadastro e itens obrigatórios, unidades, contagem de tempo, cotação, bases de interpolação, interpoladores, extrapolação, domínio, arredondamento, gravação com trava, reconstrução, interpolação sob demanda, proveniência, `hashPontos`, log e determinismo.
- `curve-extension-models`: tipos de modelo por nome, contratos, nomes QuantLib e modelos nativos.
- `curve-engine-api`: rotas básicas, parâmetros, erros, contrato de tipos, correlação, resolução por código e nome, catálogo, construção, consulta, regravação da interpolada, interpolação, situação e valores aceitos.
- `curve-load-trigger`: webhook de carga, construção disparada pela carga, construção da data sob demanda, dados brutos exigidos, conferência da quantidade e log da carga.
- `curve-audit-history`: resumo da última construção em `tCurvaMercd`.
- `calendar-management`: exportação dos feriados em JSON.
- `b3-ready-curve-model`, `ntnb-anbima-curve-model`, `sofr-bloomberg-curve-model`: os três modelos das 7 curvas.

### Modified Capabilities
<!-- Nenhuma. -->

## Impact

- **services/engine:** as seções da primeira parte do guia (`implementacao.md` desta change) (tipos, calendários nativos, interpolação, cadastro, modelos nativos, pipeline, carga, API básica). Sem Blob, sem Groovy e sem POI nesta parte.
- **Banco:** sem mudança de schema; escreve `tDadoVertcCurva`, `tDadoCurva` e, em `tCurvaMercd`, só `dBaseReft` e `cUsuarCalc`.
- **Sem autenticação:** o engine não autentica; quem expõe API ao front (`services/curves` e o BFF) autentica. O usuário da auditoria vem do cabeçalho opcional `X-Usuario`, e o `acionadoPor` vem da rota chamada.
- **Outros serviços:** o processor (change `processor-carga-b3`), o `services/curves` (change `curves-cadastro-curvas`) e o orquestrador (change `orquestrador-curvas`) já têm, com esta parte, as rotas do engine de que dependem.
- **Fica para a `engine-modelos-curva`:** origem secundária, curvas derivadas, scripts Groovy no Blob, simulação e memória de cálculo em planilha e zip, auditoria no log e arquivo de auditoria, calendário por planilha, resiliência e métricas, testes em massa.

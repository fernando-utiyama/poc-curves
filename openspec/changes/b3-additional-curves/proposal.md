## Why

A POC hoje só publica uma curva (PRE), mas a arquitetura de ingestão de curva pronta (`imported-curve-ingestion` no curve-processor, `payloadKind: READY_CURVE`) já é genérica por definição de curva — não depende do código PRE especificamente. O Manual de Curvas da B3 (`docs/Manual de Curvas_V21.pdf`) documenta a metodologia oficial de outras quatro curvas centrais da mesa (DCL, PTX, INP, DPL), e o arquivo bruto real baixado da B3 (`docs/TaxaSwap.txt`, o arquivo "Mercado Derivativos – Taxas de Mercado para Swaps" citado no próprio Manual como fonte oficial da PRE) confirma conter os vértices computados dessas quatro curvas, junto com mais de 100 outros códigos, prontos para importação — sem exigir a reimplementação das metodologias de cálculo da B3 (que dependem de fontes externas — Ptax do Bacen, IPCA do IBGE, prévias e NTN-B da Anbima, futuros DDI/DAP/IND da B3 — nenhuma delas ingerida hoje por este projeto).

## What Changes

- Novo feeder em `function-marketdata` para adquirir o arquivo `TaxaSwap.txt` (dataset `B3_TAXA_SWAP`, endpoint `pesquisapregao/download?filelist=TS<AAMMDD>.ex_` — mesmo endpoint de `PR`/`IN`, mas extensão `.ex_`, confirmada ao vivo; **não** `.zip` como PR/IN, suposição inicial por analogia que se provou errada), classificado como `payloadKind: READY_CURVE`.
- Novo parser de layout de largura fixa em `curve-processor` para o `TaxaSwap.txt`, extraindo os vértices (prazo em dias úteis/corridos, taxa ou preço) dos códigos de curva alvo desta mudança — `PRE` (oráculo cruzado, já importada hoje via `referenceRatesProxy`), `DCL`, `PTX`, `INP`, `DPL` — e ignorando os demais ~100 códigos presentes no mesmo arquivo.
- Validação do layout do `TaxaSwap.txt` (não documentado formalmente em lugar nenhum do projeto) usando `PRE` como oráculo: os vértices extraídos do `TaxaSwap.txt` para PRE devem bater, dígito a dígito após a mesma política de arredondamento, com os vértices já validados hoje via `referenceRatesProxy` para a mesma data de pregão.
- Cadastro de quatro novas `definicao_curva` (DCL, PTX, INP, DPL) com modo de origem `IMPORTED`, reaproveitando 100% do pipeline de publicação de curva importada já existente (`ProcessarEnvelopeIngestaoUseCase`, `imported-curve-ingestion`) — sem alterar seu comportamento.
- Reorganização de pacote no `curve-engine`: as classes de construção/interpolação de curva (`CurveBootstrapper`, `Interpolador` e implementações, `InterpoladorRegistry`, `ConfiguracaoInterpolacao`, `RateHelper`, `SplineCubicaNatural`, `PoliticaExtrapolacao*`, `ConvencaoContagemDias`, `RoundingPolicy`, `Vertice`, `CurvaJuros`) saem de `application/model` (hoje misturadas com modelos de domínio não relacionados a construção, como `ModeloCurva`, `VersaoCurva`, `ProcedenciaCurva`) para um pacote dedicado `application/construcao`. Puro refactor de organização, sem mudança de comportamento — abre espaço para os modelos de construção específicos por curva que isso ou mudanças futuras possam exigir.

## Capabilities

### New Capabilities
- `b3-taxa-swap-curve-import`: aquisição do arquivo `TaxaSwap.txt` da B3, parsing do seu layout de largura fixa, e extração dos vértices das curvas DCL, PTX, INP e DPL (com PRE como oráculo de validação do parser) para publicação como curva importada.

### Modified Capabilities
(nenhuma — a reorganização de pacote no curve-engine é refactor puro, sem mudança de requisito/comportamento, e a ingestão de curva importada no curve-processor já é genérica por definição de curva, não precisando de delta.)

## Impact

- **function-marketdata**: novo feeder (`FeederB3TaxaSwap` ou similar), nova fixture real capturada do `TaxaSwap.txt`, novo dataset registrado em `registro-feeders-b3.ts`.
- **curve-processor**: novo `DatasetParser` para o layout de largura fixa do `TaxaSwap.txt`, registrado em `ParserConfig`, mapeando cada código de curva (DCL/PTX/INP/DPL) para sua `definicao_curva`.
- **curve-api / migrações Flyway**: quatro novos registros de `definicao_curva` (modo `IMPORTED`).
- **curve-engine**: reorganização de pacote (`application/model` → `application/construcao`), atualização de imports; sem mudança de comportamento nem de contrato público.
- **docs**: `docs/TaxaSwap.txt` (arquivo real baixado) e `docs/Manual de Curvas_V21.pdf` passam a ser referência de fixture/layout para o novo parser — não devem ser tratados como docs de arquitetura genérica do repositório.

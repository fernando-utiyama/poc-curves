## Why

O primeiro objetivo do projeto é entregar 7 curvas: DIxPRE, DCL, PTAX, DPL e IBOVESPA (Taxa Swap B3), NTN-B (ANBIMA) e SOFR (Bloomberg). As três fontes pedem tratamento diferente:
- as curvas B3 chegam prontas no `TaxaSwap.txt` (278 vértices por curva, com 7 casas decimais);
- a NTN-B exige bootstrap, porque a taxa indicativa da ANBIMA é a taxa de um título com cupom, não taxa zero;
- o SOFR (`S0490Z <tenor> BLC2 Curncy`) já vem como taxa zero por tenor, mas num formato de dado que nenhuma curva do projeto usa.

O `services/engine` da `develop` não trata nem o caso mais simples. A refatoração de modelos ficou pela metade: o interpolador é descartado no registro, a conversão taxa↔fator está repetida em quatro lugares, a convenção é fixa em 252 e `cRotnaCalc` não é usado. Também não há como investigar em produção por que uma curva saiu com um valor.

## What Changes

- **Pipeline guiado pelo cadastro.** Construir = ler o cadastro → executar o modelo de construção (`cMotorCalc`) → arredondar → gravar os pontos em `tDadoCurva`. Consultar e interpolar leem os pontos gravados e aplicam o interpolador (`cRotnaCalc`) na hora, sem cache. Unidades, contagem de tempo, fórmulas de cotação, interpolação e extrapolação, domínio, arredondamento e erros ficam fechados na spec.
- **Cadastro com lista fechada de itens.** Origem (fonte, produto, código na fonte), modelos, unidade, grandeza, eixo de tempo, cotação, calendário, extrapolação por lado, horizonte e arredondamento. Item ausente, valor inválido ou chave desconhecida é erro; os únicos padrões são as extrapolações `Disabled`. Regra de metodologia que vale para todas as curvas de um modelo fica no modelo, como o cupom da NTN-B.
- **Um pacote Java por modelo, estendível por Groovy.**
  - Construção:
    - `prontatsb3` (`PRONTA_TS_B3`): as 5 curvas B3, com checagem dos dias úteis publicados contra o calendário;
    - `ntnbbootstrapanbima` (`NTNB_BOOTSTRAP_ANBIMA`): bootstrap sequencial por bisseção sobre `tAnbmaCurvaPrimr`;
    - `sofrzerobloomberg` (`SOFR_ZERO_BLOOMBERG`): nós por tenor, sem bootstrap;
    - `pontosprontos`: código comum aos modelos sem bootstrap.
  - Interpolação no modelo do QuantLib: grandeza (`Discount`, `CompoundFactor`, `ZeroYield`, `Price`) + interpolador (`Linear`, `LogLinear`, `BackwardFlat`, `ForwardFlat`, `Cubic`) + `DayCounter` do eixo. As funções do Manual de Curvas B3 viram configuração. Extrapolação por lado: `Disabled`, `FlatForward`, `FlatValue`.
  - Calendários `Brazil`/`Settlement` e `UnitedStates`/`FederalReserve`, com os feriados listados na spec. Feriados também podem ser mantidos por planilha: a importação gera um script Groovy de calendário versionado, e a exportação devolve a planilha no mesmo formato.
  - Construção, interpolação e calendário podem ser criados ou sobrescritos por Groovy, com versões imutáveis no Blob Storage existente (`groovy-models/{tipo}/{nome}/`), propagadas a todas as instâncias em até 30 segundos, validação antes de ativar, sandbox por lista permitida e tempo limite.
- **Tipos e enums com os nomes do QuantLib** (`Compounding`, `Frequency`, `BusinessDayConvention`, `TimeUnit`, `DayCounter`, calendários), em implementação própria, 100% Java e `BigDecimal`.
- **Construção disparada pela carga concluída.** O processor avisa por webhook (`POST /api/v1/cargas`) que terminou de gravar uma carga, com a quantidade de linhas por código. O engine registra a carga no Blob e constrói as curvas daquela origem que ainda não têm pontos na data. A carga nunca recalcula: uma republicação da fonte só gera aviso, e recalcular exige `forcarRecalculo=true` na API. Nenhuma construção roda sem carga registrada (salvo com o Blob fora, quando segue com aviso), e o engine confere se leu exatamente a quantidade avisada: carga parcial não vira curva gravada.
- **Simulação e memória de cálculo para investigar em produção.**
  - Uma rota de simulação executa a construção com o mesmo código, sem gravar nada, e compara ponto a ponto com o que está gravado.
  - As rotas de consulta, interpolação e simulação aceitam `formato=xlsx` e baixam uma planilha com a memória de cálculo:
    - insumos lidos e descartados;
    - cada ponto com `DU`, `DC`, `X`, grandeza e fatores;
    - fluxos do bootstrap;
    - cada prazo interpolado com os vizinhos e o peso;
    - eventos.
  - `formato=zip` gera um pacote de depuração: planilha, JSON, código-fonte exato de cada script Groovy usado e manifesto com hashes. A proveniência inclui a versão do engine.
  - Logs estruturados e um `hashPontos` ligam cada consulta à construção ou edição que gravou aqueles pontos.
- **Auditoria e histórico persistentes, sem mudar o banco.** Toda gravação de pontos (construção, reconstrução, edição) grava antes do commit um registro imutável no Blob (com o Blob fora, o registro vai para o log e é regravado depois): quem, quando, por quê (motivo obrigatório para recálculo e edição), de qual carga, com quais modelos e cadastro, e os pontos substituídos. A construção atualiza `tCurvaMercd.dBaseReft` (última data-base) e `cUsuarCalc`. O histórico é consultável pela API.
- **Leitura consistente e segurança de produção.**
  - Leituras só em `READ COMMITTED`, nunca `NOLOCK`: consulta nunca vê a data vazia no meio de uma reconstrução.
  - Todas as rotas exigem JWT do Entra ID, com papéis de leitura, operador, processor, autor e aprovador de scripts.
  - Tempo limite em toda dependência e repetição só do que é idempotente.
  - O Blob fora nunca bloqueia construção nem consulta: o engine constrói com o que tem (auditoria pendente no log, último estado de script conhecido ou modelos nativos) e registra a degradação na proveniência e no log. Uma instância nova espera o Blob por até 5 minutos antes de ficar pronta.
  - Logs e métricas por dependência, e circuit breaker no Blob.
  - O Blob é acessado por Managed Identity.
- **Datas e horários de Brasília.** Datas-base sem hora; "hoje" e todos os instantes (respostas, planilhas, auditoria, logs) em `America/Sao_Paulo`, independentemente do fuso do servidor.
- **Mais testes.** Oráculo contra a B3 sobre pelo menos 12 meses de pregões (feriados móveis e virada de ano incluídos), testes de propriedade e teste de precisão decimal.
- **Curva gravada é curva liberada.** Data quality (checagens e aprovação) fica para uma feature futura.
- **BREAKING — API por código da curva + data-base.** Construir, consultar, editar pontos, interpolar e simular pelo código (ex.: `PRE`) e pela data na URL; leitura também pelo nome de exibição. Erros padronizados com código e `correlationId`. Substitui `POST /api/v1/curvas/construir`, `POST /api/v1/calculo` e `POST /api/v1/modelos/upload`.
- **Removido do engine:**
  - enums: `MetodoInterpolacao`, `PoliticaExtrapolacao`;
  - parâmetros em texto: `CONVENCAO`, `MOD_DADO`;
  - classes: `ComposableCurveBuilder`, `CurveBuilderRegistry`, `CurveInterpolatorRegistry`, `CurveExtrapolatorRegistry`;
  - gravação em `tDadoVertcCurva` e `tMtrizCurva`.

## Capabilities

### New Capabilities
- `curve-build-pipeline`: cadastro e itens obrigatórios, unidades, contagem de tempo, cotação, grandezas, interpoladores, extrapolação, domínio, arredondamento, gravação dos pontos com trava, reconstrução, interpolação sob demanda, proveniência, `hashPontos` e log.
- `curve-extension-models`: contratos dos modelos, nomes QuantLib, modelos e calendários nativos, ordem de resolução, versões e estados de script, validação e contenção.
- `curve-engine-api`: rotas, parâmetros, códigos de erro, correlação, resolução por código e nome, catálogo, construção, consulta, edição de pontos, interpolação, saída `xlsx` e `zip`, rotas de histórico e de calendário, autenticação e papéis, e gestão de scripts.
- `curve-calculation-memory`: simulação sem gravação, comparação com os pontos gravados e planilha de memória de cálculo com abas e colunas fixas.
- `curve-load-trigger`: webhook de carga concluída, registro durável no Blob, construção disparada só para curvas sem pontos, republicação sem recálculo automático, trava de construção sem carga e conferência da quantidade lida.
- `curve-audit-history`: auditoria imutável no Blob antes do commit, resumo em `tCurvaMercd`, motivo obrigatório e consulta do histórico.
- `calendar-management`: calendário por lista, importação de planilha de feriados gerando script Groovy versionado, validação e exportação no mesmo formato.
- `curve-engine-resilience`: tempos limite, repetição, degradação com o Blob fora, saúde e prontidão, logs de requisição e de dependência, e métricas.
- `b3-ready-curve-model`: `PRONTA_TS_B3`, validação das linhas do `TaxaSwap.txt`, cadastro das 5 curvas B3 e oráculo contra o arquivo.
- `ntnb-anbima-curve-model`: `NTNB_BOOTSTRAP_ANBIMA`, leitura de `tAnbmaCurvaPrimr`, fluxo de caixa com cupom fixo, cotação, bootstrap por bisseção e cadastro da NTN-B.
- `sofr-bloomberg-curve-model`: `SOFR_ZERO_BLOOMBERG`, leitura dos nós por tenor, conversão de tenor em data, duplicidade e cadastro da SOFR.

### Modified Capabilities
<!-- Nenhuma: não há specs arquivadas na develop. -->

## Impact

- **services/engine:**
  - reorganização de `domain` (`curva`, `quantlib`, `matematica`, `calendario`, `construcao`, `interpolacao`, `memoria`, `modelo`);
  - reescrita de `ConstruirCurvaService` e `CalcularCurvaService`, e controllers novos;
  - carregador Groovy com scripts no Blob Storage (`azure-storage-blob`, dependência nova no engine);
  - adaptador de planilha com Apache POI (`poi-ooxml`, dependência nova);
  - a entidade de `tDadoCurva` passa a ter só as quatro colunas do schema.
- **Banco: sem alteração de schema.** Parâmetros da curva em JSON em `tConfgCurva.cModDado` (coluna existente); o engine escreve `tDadoCurva` e, em `tCurvaMercd`, só `dBaseReft` e `cUsuarCalc`; `tConfgCurva.cRotnaCalc` passa a ser usado.
- **Entra ID:** registro da aplicação com os papéis `Curvas.Leitura`, `Curvas.Operador`, `Curvas.Processor`, `Curvas.ModelosAutor` e `Curvas.ModelosAprovador`. Todo cliente da API passa a precisar de token.
- **Blob Storage:** pastas `groovy-models/`, `cargas/` e `auditoria/` (com política de imutabilidade) no container existente, acessadas por Managed Identity.
- **Dependências fora do engine (outros changes):**
  - o conector precisa classificar o `TaxaSwap.txt` pelo código exato (hoje DCL/DPL viram DOL e PTX/INP são descartados), e o processor precisa gravar em `tBtrsCurvaPrimr`;
  - a ingestão ANBIMA precisa confirmar a unidade de `vVertcCurva` e a escala de `vPrecoTx`;
  - a ingestão SOFR precisa criar `mkt.SofrCurveRaw` e o feeder;
  - o processor precisa chamar o webhook de carga depois do commit de cada carga, com retry pelo mesmo `idCarga`;
  - o cadastro das 7 curvas vem do processo de cadastro do projeto, com os valores das specs;
  - os clientes da API do engine (curve-bff) precisam migrar para as rotas novas (BREAKING).
- **Fora de escopo:** CRUD de cadastro de curva e provedor (serviço de cadastro, `services/curves` no futuro); persistir a curva diária em `tCurvaData` (depende de alterar `FK_tDadoCurva_tCurvaData`); cache de curva; qualquer mudança de schema (alvos ideais registrados no design).

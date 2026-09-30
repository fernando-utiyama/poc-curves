## Why

> **Dividida em 2026-09-30.** A primeira parte (construir, gravar, consultar e interpolar as 7 curvas com os modelos e calendários nativos, e as rotas básicas usadas pelos outros serviços) está na change `engine-construcao-curvas`, que vem antes. O design e o guia de implementação, comuns às duas, estão na change `engine-construcao-curvas`. Esta change entrega o resto: origem secundária, curvas derivadas, scripts Groovy no Blob, simulação e memória de cálculo, auditoria, calendário por planilha, resiliência, autenticação e testes em massa. O texto abaixo descreve o engine inteiro, como contexto das duas partes.

O primeiro objetivo do projeto é entregar 7 curvas: DIxPRE, DCL, PTAX, DPL e IBOVESPA (Taxa Swap B3), NTN-B (ANBIMA) e SOFR (Bloomberg). As três fontes pedem tratamento diferente:
- as curvas B3 chegam prontas no `TaxaSwap.txt` (278 vértices por curva, com 7 casas decimais);
- a NTN-B exige bootstrap, porque a taxa indicativa da ANBIMA é a taxa de um título com cupom, não taxa zero;
- o SOFR (`S0490Z <tenor> BLC2 Curncy`) já vem como taxa zero por tenor, mas num formato de dado que nenhuma curva do projeto usa.

O `services/engine` da `develop` não trata nem o caso mais simples. A refatoração de modelos ficou pela metade: o interpolador é descartado no registro, a conversão taxa↔fator está repetida em quatro lugares, a convenção é fixa em 252 e `cRotnaCalc` não é usado. Também não há como investigar em produção por que uma curva saiu com um valor.

## What Changes

- **Pipeline guiado pelo cadastro.** Construir = ler o cadastro → executar o modelo de construção (`cMotorCalc`) → arredondar → gravar os pontos (curva construída) em `tDadoVertcCurva` e a curva interpolada, um valor por dia corrido, em `tDadoCurva`. Consultar e interpolar leem os pontos gravados e aplicam o interpolador (`cRotnaCalc`) na hora, sem cache, com o mesmo resultado da interpolada gravada. Unidades, contagem de tempo, fórmulas de cotação, interpolação e extrapolação, domínio, arredondamento e erros ficam fechados na spec.
- **Cadastro com lista fechada de itens.** Origem (fonte, produto, código na fonte), modelos, unidade, base de interpolação, eixo de tempo, cotação, calendário, extrapolação por lado, horizonte e arredondamento. Item ausente, valor inválido ou chave desconhecida é erro; os únicos padrões são as extrapolações `Disabled`. Regra de metodologia que vale para todas as curvas de um modelo fica no modelo, como o cupom da NTN-B.
- **Um pacote Java por modelo, estendível por Groovy.**
  - Construção:
    - `prontatsb3` (`PRONTA_TS_B3`): as 5 curvas B3, obedecendo os dias úteis publicados, com aviso quando o calendário diverge;
    - `ntnbbootstrapanbima` (`NTNB_BOOTSTRAP_ANBIMA`): bootstrap sequencial por bisseção sobre `tAnbmaCurvaPrimr`;
    - `sofrzerobloomberg` (`SOFR_ZERO_BLOOMBERG`): nós por tenor, sem bootstrap;
    - `pontosprontos`: código comum aos modelos sem bootstrap.
  - Interpolação no modelo do QuantLib: base de interpolação (`Discount`, `CompoundFactor`, `ZeroYield`, `Price`) + interpolador (`Linear`, `LogLinear`, `BackwardFlat`, `ForwardFlat`, `Cubic`) + `DayCounter` do eixo. As funções do Manual de Curvas B3 viram configuração. Extrapolação por lado: `Disabled`, `FlatForward`, `FlatValue`.
  - Calendários `Brazil`/`Settlement` e `UnitedStates`/`FederalReserve`, com os feriados listados na spec. Feriados também podem ser mantidos por planilha: a importação gera um script Groovy de calendário versionado, e a exportação devolve a planilha no mesmo formato.
  - Construção, interpolação e calendário podem ser criados ou sobrescritos por Groovy, com versões imutáveis no Blob Storage existente (`groovy-models/{tipo}/{nome}/`), propagadas a todas as instâncias em até 30 segundos, validação antes de ativar, sandbox por lista permitida e tempo limite.
- **Tipos e enums com os nomes do QuantLib** (`Compounding`, `Frequency`, `BusinessDayConvention`, `DayCounter`, calendários), em implementação própria, 100% Java, com valores em `BigDecimal` e `pow`/`ln`/`exp` pelo `StrictMath` do Java. Todo o resto usa o que o Java 21 já tem (`java.time`, `RoundingMode`, records, sealed, virtual threads), em arquitetura hexagonal.
- **Construção disparada pela carga concluída.** O processor grava cada carga numa transação e, depois do commit, avisa por webhook (`POST /api/v1/cargas`) com a quantidade de linhas por código. O engine constrói na hora as curvas daquela origem que ainda não têm pontos na data, conferindo a quantidade lida contra a avisada. A carga nunca recalcula: para as curvas que já têm pontos, o engine roda o modelo sem gravar e avisa (`PONTOS_DIFERENTES_DA_FONTE`) se a fonte atual produz pontos diferentes; recalcular exige `forcarRecalculo=true` na API. O engine não guarda registro da carga. O orquestrador é o segundo gatilho automático: `POST /api/v1/construcoes/{dataBase}` constrói, com as mesmas regras, todas as curvas da data que têm insumo e ainda não têm pontos, inclusive as derivadas cuja curva componente foi construída fora de uma carga, e cobre um aviso de carga perdido.
- **Simulação e memória de cálculo para investigar em produção.**
  - Uma rota de simulação executa a construção com o mesmo código, sem gravar nada, e compara ponto a ponto com o que está gravado.
  - As rotas de consulta, interpolação e simulação aceitam `formato=xlsx` e baixam uma planilha com a memória de cálculo:
    - insumos lidos e descartados;
    - cada ponto com `DU`, `DC`, `X`, base de interpolação e fatores;
    - fluxos do bootstrap;
    - cada prazo interpolado com os vizinhos e o peso;
    - eventos.
  - `formato=zip` gera um pacote de depuração: planilha, JSON, código-fonte exato de cada script Groovy usado e manifesto com hashes. A proveniência inclui a versão do engine.
  - Logs estruturados e um `hashPontos` ligam cada consulta à construção ou edição que gravou aqueles pontos.
- **Auditoria sem mudar o banco e sem Blob.** Toda construção e todo recálculo emitem no log o evento `CURVA_GRAVADA`: quem, quando, de qual carga, com quais modelos e cadastro, e os pontos substituídos. A construção atualiza `tCurvaMercd.dBaseReft` (última data-base) e `cUsuarCalc`. O front pede o arquivo de auditoria de uma curva e data, que o engine monta na hora: pontos gravados, cadastro, modelos e a conferência ponto a ponto com o que a fonte produz agora. A edição manual de pontos sai do engine e vai para o `services/curves` (change `curves-cadastro-curvas`), como contingência.
- **Leitura consistente e segurança de produção.**
  - Leituras só em `READ COMMITTED`, nunca `NOLOCK`: consulta nunca vê a data vazia no meio de uma reconstrução.
  - Todas as rotas exigem JWT do Entra ID, com papéis de leitura, operador, processor, autor e aprovador de scripts.
  - Tempo limite em toda dependência e repetição só do que é idempotente.
  - O Blob guarda, para o engine, só os scripts Groovy, e fora do ar nunca bloqueia construção nem consulta: o engine usa o último estado de script conhecido ou os modelos nativos e registra a degradação na proveniência e no log. Uma instância nova espera o Blob por até 5 minutos antes de ficar pronta.
  - Logs e métricas por dependência, e circuit breaker no Blob.
  - O Blob é acessado por Managed Identity.
- **Datas e horários de Brasília.** Datas-base sem hora; "hoje" e todos os instantes (respostas, planilhas, auditoria, logs) em `America/Sao_Paulo`, pelo fuso padrão da JVM fixado pela aplicação na subida, independentemente do fuso do servidor.
- **Mais testes.** Oráculo contra a B3 sobre pelo menos 12 meses de pregões (feriados móveis e virada de ano incluídos), testes de propriedade e teste da matemática contra valores de referência.
- **Situação e vigência da curva só valem para a construção automática.** A construção automática (carga e orquestrador) não constrói curva inativa ou fora da vigência (devolve `IGNORADA`); a construção pedida pelo usuário constrói, com aviso.
- **Construção por origem secundária.** A construção automática usa a origem principal (menor prioridade em `tCurvaPrvdr`); o usuário pode construir ou recalcular pela origem de reserva, com `fonte` e `produto` na rota de construção, e simular por ela antes. O modelo de cada origem secundária vem de `MODELOS_POR_ORIGEM` no `cModDado`; o resto do cadastro é o da curva. O bruto da reserva já está no banco, porque os feeders gravam para toda curva ligada ao código.
- **Interpolação tolerante a pontos gravados à mão.** Pontos em fim de semana, feriado ou até a data-base, que a edição manual grava com aviso, são tratados na base comum de interpolação: descarte com aviso, sem alterar o gravado.
- **Estrutura para curvas derivadas de outras curvas** (ex.: inflação implícita = PRE sobre a NTN-B), sem nenhum modelo derivado nesta fase: as curvas componentes são ligações com o provedor interno `TCEN`, o modelo as lê já montadas pelo contexto de construção, e a carga constrói as derivadas em cadeia quando as curvas componentes ficam prontas, sem recálculo em cascata.
- **Valores aceitos no cadastro por API.** `GET /api/v1/valores-cadastro` lista, gerado do próprio validador, tudo o que o cadastro aceita, inclusive os modelos Groovy ativos, para o front e a planilha do `services/curves`.
- **Contrato de tipos para o front pt-BR.** Decimais como string (sem perda de precisão no JavaScript), datas ISO, enums com caixa exata, avisos e erros num formato único com o `services/curves` e o conector, textos em pt-BR, e catálogos de enums, avisos e erros com rótulo e descrição em `GET /api/v1/valores-cadastro`. Colunas `CHAR` do cadastro lidas sem os espaços de preenchimento.
- **Curva construída e curva interpolada.** `tDadoVertcCurva` guarda a curva construída: os pontos com dias úteis, dias corridos, dias 30/360 e fatores. `tDadoCurva` guarda a curva interpolada, um valor por dia corrido até o fim do domínio, inclusive fins de semana e feriados, regravada sempre que os pontos mudam (inclusive depois de uma edição manual, pela rota `POST .../interpolada`). A tela mostra o gravado ao lado do recalculado. Os dias úteis publicados pela fonte (B3, ANBIMA) ou informados pelo usuário são obedecidos: ficam em `tDadoVertcCurva.cDiaUtil` e dão o prazo de cada ponto na interpolação; o calendário só conta as datas entre os pontos, ancoradas no ponto anterior.
- **Curva gravada é curva liberada.** Data quality (checagens e aprovação) fica para uma feature futura.
- **BREAKING — API por código da curva + data-base.** Construir, consultar, interpolar e simular pelo código (ex.: `PRE`) e pela data na URL; leitura também pelo nome de exibição. Erros padronizados com código e `correlationId`. Substitui `POST /api/v1/curvas/construir`, `POST /api/v1/calculo` e `POST /api/v1/modelos/upload`.
- **Removido do engine:**
  - enums: `MetodoInterpolacao`, `PoliticaExtrapolacao`;
  - parâmetros em texto: `CONVENCAO`, `MOD_DADO`;
  - classes: `ComposableCurveBuilder`, `CurveBuilderRegistry`, `CurveInterpolatorRegistry`, `CurveExtrapolatorRegistry`;
  - gravação em `tMtrizCurva`, e o cabeçalho de versão que o engine antigo gravava junto com `tDadoVertcCurva`.

## Capabilities

### New Capabilities
- `curve-calculation-memory`: simulação sem gravação, comparação com os pontos gravados e planilha de memória de cálculo com abas e colunas fixas, e pacote zip.
- `curve-engine-resilience`: tempos limite, repetição, degradação com o Blob fora, saúde e prontidão, logs de requisição e de dependência, e métricas.

### Modified Capabilities
Acrescentam requisitos às capabilities criadas pela change `engine-construcao-curvas`:
- `curve-build-pipeline`: curva derivada e construção por origem secundária.
- `curve-extension-models`: ordem de resolução, scripts no Blob, mesma versão em todas as instâncias, versões e estados, validação e contenção.
- `curve-engine-api`: rotas de operação e extensões, autenticação e papéis, saída em planilha e gestão de scripts.
- `curve-load-trigger`: construção em cadeia das derivadas.
- `curve-audit-history`: auditoria no log e arquivo de auditoria montado na hora.
- `calendar-management`: calendário por lista, importação e validação da planilha, exportação em planilha e por versão.
- `b3-ready-curve-model`, `ntnb-anbima-curve-model`, `sofr-bloomberg-curve-model`: o registro de cada modelo na memória de cálculo.

## Impact

- **services/engine:**
  - reorganização de `domain` (`curva`, `quantlib`, `matematica`, `calendario`, `construcao`, `interpolacao`, `memoria`, `modelo`);
  - reescrita de `ConstruirCurvaService` e `CalcularCurvaService`, e controllers novos;
  - carregador Groovy com scripts no Blob Storage (`azure-storage-blob`, dependência nova no engine);
  - adaptador de planilha com Apache POI (`poi-ooxml`, dependência nova);
  - a entidade de `tDadoCurva` passa a ter só as quatro colunas do schema, e a de `tDadoVertcCurva` guarda os pontos.
- **Banco: o engine não depende de alteração de schema.** Parâmetros da curva em JSON em `tConfgCurva.cModDado` (coluna existente); o engine escreve `tDadoVertcCurva` (pontos), `tDadoCurva` (interpolada) e, em `tCurvaMercd`, só `dBaseReft` e `cUsuarCalc`; `tConfgCurva.cRotnaCalc` passa a ser usado.
- **Entra ID:** registro da aplicação com os papéis `Curvas.Leitura`, `Curvas.Operador`, `Curvas.Processor`, `Curvas.Orquestrador`, `Curvas.ModelosAutor` e `Curvas.ModelosAprovador`. Todo cliente da API passa a precisar de token.
- **Blob Storage:** só a pasta `groovy-models/` no container existente, acessada por Managed Identity. Nenhum dado de curva vai para o Blob, que fica com os originais dos feeders e os scripts.
- **Dependências fora do engine (outros changes):**
  - B3: o processor precisa ler o `TaxaSwap.txt` pelo código exato e gravar em `tBtrsCurvaPrimr` (change `conector-b3-webhook-ingest`; hoje o conector transforma DCL/DPL em DOL e descarta PTX/INP);
  - a ingestão ANBIMA (arquivo `ms{AAMMDD}.txt`, produto `MS`) grava só o título inteiro (código SELIC terminado em `99`) e precisa confirmar a unidade de `vVertcCurva`, calculado a partir de `Data Vencimento`; a escala de `vPrecoTx` (percentual) está confirmada pelo arquivo;
  - a ingestão SOFR precisa do feeder gravando os nós em `tBbergCurvaPrimr` (o ticker completo precisa da change `banco-curvas-ajustes`; a forma curta cabe no schema atual);
  - o processor precisa chamar o webhook de carga depois do commit de cada carga, com retry pelo mesmo `idCarga` (change `conector-b3-webhook-ingest`, para a B3);
  - o cadastro das 7 curvas vem do `services/curves` (change `curves-cadastro-curvas`, com o exemplo `exemplo-cadastro-7-curvas.txt`), com os valores das specs;
  - os clientes da API do engine (curve-bff) precisam migrar para as rotas novas (BREAKING).
- **Fora de escopo:** CRUD de cadastro de curva e edição manual dos pontos (change `curves-cadastro-curvas`) e CRUD de provedor (outro dev); `tCurvaData`, que não é usada e sai do schema na change `banco-curvas-ajustes`; cache de curva; qualquer mudança de schema (alvos ideais registrados no design).

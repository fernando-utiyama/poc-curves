## Context

O alvo produtivo é uma plataforma de curvas de Tesouraria/Risco na Azure: Azure Functions em Node.js baixam dados de B3, Bloomberg e LSEG e publicam em Kafka; um `curve-processor` persiste no Azure SQL; um pod de motor de cálculo constrói e interpola curvas; um `curve-orchestrator` dispara os feeders por agendamento cadastrado ou por botão em tela; e APIs expõem cadastro de curva, consulta de curva e curva interpolada. Falta a camada de produto (BFF + front Angular no shell Liquid) e falta contrato escrito entre as peças.

Esta POC reproduz a mesma topologia **100% local em Podman**, com uma única fonte de dados (**B3**), para provar o fluxo e fixar os contratos antes de investir em Azure.

A matemática de curva vive em `libs/curve-kernel/`, um **módulo novo e independente deste repositório** — interpoladores, políticas de extrapolação, bootstrap, `RateHelper`, convenções B3/ANBIMA (calendário com feriados móveis, DU/252, ACT/360, ACT/365, arredondamento com truncamento de primeira classe), montagem das curvas B3 e o reconciliador contra a taxa de referência oficial. É biblioteca pura: sem Spring, sem banco, sem rede.

O repositório `D:\Workspace\curve-platform` é **referência de ideia e de metodologia já validada**, não dependência: nenhum módulo de `poc-curvas` declara dependência dele nem importa código dele. Portar ideias, algoritmos e fixtures de teste é legítimo e esperado; herdar acoplamento não.

Restrições fixas desta POC: Podman rootless (não há Docker Desktop na máquina), Java 21 + Spring Boot 3.4.x sem Lombok, Node 20 + TypeScript nos feeders, Angular como micro-frontend Module Federation dentro do shell Liquid, BigDecimal obrigatório em todo valor com política de arredondamento de mercado.

## Goals / Non-Goals

**Goals:**

- Fixar contratos versionados entre os oito componentes, de forma que cada mudança-irmã possa ser implementada de forma independente contra o contrato, não contra a implementação do vizinho.
- Provar o fluxo ponta a ponta localmente: agendamento → ingestão B3 → Kafka → persistência → bootstrap → vértices → interpolação → API → BFF → tela.
- Tornar toda curva publicada rastreável até os insumos que a geraram (proveniência + versionamento).
- Garantir que o mesmo desenho suba na Azure trocando infraestrutura, não código de domínio.

**Non-Goals:**

- Deploy em Azure (AKS, Azure Functions, Event Hubs, Azure SQL) — a POC é local.
- Feeders de Bloomberg e LSEG implementados; o contrato de feeder existe e é o ponto de extensão, mas só B3 roda.
- Alta disponibilidade, DR, multi-região, tuning de performance em escala de produção.
- Implementar o runtime de modelos em si — ele é especificado e construído na mudança `curve-engine`; aqui entram apenas o modelo de dados que o sustenta e a regra de proveniência.

## Decisions

### D1 — Kafka é a fronteira entre ingestão e domínio, não um barramento genérico

Somente dois fluxos passam por Kafka: dado bruto do feeder (`marketdata.raw.v1`) e dado normalizado pronto para uso (`marketdata.normalized.v1`), mais os dois eventos de curva (`curve.build.requested.v1`, `curve.published.v1`). Consultas nunca passam por Kafka.

*Alternativa considerada*: o feeder gravar direto no SQL e Kafka só notificar. Rejeitada porque perde replay — um erro de parse exigiria rebaixar o arquivo da B3; com o conteúdo em tópico, reprocessa-se do offset.

*O conteúdo trafega em blocos, não como arquivo inteiro.* Os arquivos diários da B3 são grandes demais para uma única mensagem — o limite padrão do Kafka é 1 MB, e o Azure Event Hubs no tier Standard impõe o mesmo teto sem possibilidade de configuração. O feeder quebra o arquivo em blocos de registros; ver D1b.

### D1b — Ingestão em blocos, com corte estrutural no feeder

O feeder quebra o arquivo da fonte em blocos de registros e publica um evento por bloco, carregando `loteId`, `sequencia` e `totalBlocos`. O corte é **estrutural** — "este XML tem N elementos repetidos" — e não semântico: o feeder não sabe o que é taxa, vencimento ou contrato. Toda interpretação financeira continua no `curve-processor`, em Java.

*Por que blocos e não uma mensagem por registro*: um arquivo diário da B3 traz todos os instrumentos negociados. Registro a registro seriam centenas de milhares de mensagens, com overhead por mensagem dominando o processamento. O bloco fica no meio-termo: mensagem pequena, contagem administrável, e um registro defeituoso contamina um bloco em vez do dia inteiro.

*Por que não claim-check* (blob em armazenamento de objeto e só a referência no tópico): resolveria o tamanho, mas acopla a retenção do blob à da dead-letter, cria a possibilidade de referência pendurada e adiciona uma dependência de infraestrutura. A quebra em blocos resolve o mesmo problema sem nada disso.

*O que a quebra custa*: a atomicidade que "um arquivo = uma mensagem" dava de graça. Volta explicitamente pela contagem de blocos — o lote só é considerado completo quando todos chegam, e lote incompleto não gera pedido de construção.

*O que ela ganha além do tamanho*: o processor começa a gravar enquanto o feeder ainda lê o arquivo, o que é ganho direto de orçamento dentro da janela crítica; e ninguém segura o arquivo inteiro em memória.

### D1c — Três faixas de ingestão, isoladas por consumidor

A ingestão trafega em três tópicos distintos, cada um com grupo de consumo e pool de thread próprios:

| Faixa | Origem | Urgência |
|---|---|---|
| Rotina | agendamento diário | normal |
| Prioritária | disparo manual pela tela | alguém está esperando |
| Massa | backfill | pode esperar |

*O problema que isso resolve*: com uma faixa só, o redisparo de uma ingestão travada entra **atrás** da mensagem travada — a chave de partição é a mesma, logo a partição é a mesma. O operador que percebe o problema não consegue contorná-lo.

*Por que faixa e não partição diferente*: mandar o redisparo para outra partição depende de colisão de hash — "provavelmente diferente" não serve para procedimento de emergência — e quebraria a ordenação que a chave existe para garantir. Além disso, partição diferente não basta: se as duas partições forem atribuídas ao mesmo consumidor, a thread bloqueada não busca a outra. **O isolamento necessário é de consumidor e thread**, e é isso que faixas separadas entregam.

*Regra que evita lógica condicional*: todo disparo manual publica na faixa prioritária, sempre — independentemente de haver algo travado. Disparo manual é intervenção por definição.

*Benefício adicional*: a faixa de massa é a que se pausa na janela crítica, sem tocar nas outras duas — é o mecanismo que faz "hoje ganhar do histórico".

### D2 — Envelope de evento comum e obrigatório

Todo evento carrega `eventId` (UUID), `correlationId` (herdado do run que originou), `source` (`B3` | `BLOOMBERG` | `LSEG`), `dataset`, `referenceDate`, `producedAt`, `schemaVersion` e `payload`. A chave de partição é `source|dataset|referenceDate`, o que garante ordenação por dataset e data e permite paralelismo entre datasets.

*Alternativa considerada*: schema livre por feeder. Rejeitada — é exatamente o que faz um feeder novo quebrar o `curve-processor`. Com envelope fixo, o processor roteia por `dataset` e só o parser do payload é específico da fonte.

### D3 — Evolução de contrato por adição, versão no nome do tópico

Campos novos só podem ser adicionados como opcionais. Mudança incompatível cria `*.v2` e os dois tópicos coexistem durante a migração. Schemas ficam versionados em `contracts/events/*.schema.json` e são validados em teste, no produtor e no consumidor.

*Alternativa considerada*: Confluent Schema Registry com Avro. Rejeitada para a POC — mais uma peça de infra em Podman e mais atrito de build, para um ganho que JSON Schema validado em CI já entrega nesta escala. O desenho não impede adotá-lo depois: o envelope já carrega `schemaVersion`.

### D4 — Motor híbrido: construção assíncrona, interpolação síncrona

Construir a curva é caro (bootstrap sobre dezenas de instrumentos) e o resultado é o mesmo para todo mundo → assíncrono, disparado por evento, persistido como `versao_curva` + `vertice_curva`. Interpolar é barato, o prazo pedido é arbitrário e o usuário está esperando na tela → síncrono, via API do motor, sobre a curva já publicada, com cache Redis por `(curveId, referenceDate, versionId, tenor, interpolator)`.

*Alternativas consideradas*: (a) tudo síncrono — recalcularia a mesma curva a cada consulta e não deixaria rastro do que foi publicado; (b) tudo assíncrono — obrigaria pré-materializar todos os prazos possíveis, o que é infinito para prazo arbitrário.

### D4b — O prazo de publicação é requisito, e o desenho precisa conhecê-lo

Depois de alguns dias no ar, a preocupação operacional não é histórico nem integridade: é **a curva de hoje sair antes do fechamento do banco**. Nada na arquitetura sabia disso, e sem saber não há como distinguir "está demorando, mas dá tempo" de "não vai dar".

Passa a existir: `horario_limite_publicacao` na definição de curva, tempo restante até o corte como dado de primeira classe na execução, e orçamento de tempo decomposto por etapa — ingestão, construção, publicação — com o tempo real de cada uma registrado para calibrar com medição em vez de suposição.

*Consequência sobre alertas*: o alerta que importa é **preditivo**, não reativo. "Falhou" descoberto às 18h58 é inútil. O sinal útil é "a PRE de hoje ainda não publicou e faltam 12 minutos" — que não depende de falha nenhuma, apenas de ausência de sucesso dentro do orçamento.

*Consequência sobre retentativa*: o teto certo é **tempo restante**, não número de tentativas. Retentar por dez minutos às 14h é razoável; às 18h50 consome metade do que sobrou. A política de retentativa passa a ser função do relógio.

*Consequência sobre prioridade*: backfill e reprocessamento de pendência antiga não podem competir com o fluxo do dia. Existe janela de bloqueio antes do corte, e a faixa de massa é pausada nela.

*O que fica em aberto e é decisão de negócio*: o que a mesa usa se o corte chegar sem a curva. O desenho continua proibindo curva parcial — publicar número errado é pior que não publicar —, mas o comportamento no pior dia precisa ser definido por Tesouraria e Risco, não pela arquitetura.

### D4bis — Contingência é caminho declarado, não improviso

O desenho proíbe curva parcial, e continua certo. Mas isso deixava indefinido o que a mesa faz quando o corte chega sem curva — e indefinido significa improviso no pior dia.

A resposta é a **carga manual**: o operador sobe um arquivo CSV ou planilha com a curva, a partir de um modelo que a própria tela oferece para download. A versão resultante é marcada como `CARREGADA` e exige justificativa.

*Três coisas que a carga não pode relaxar*: passa pelo mesmo gate de validação, segue as mesmas regras de versionamento, e fica permanentemente distinguível de uma curva calculada. Curva digitada por gente sob pressão é mais sujeita a erro, não menos — dispensar a validação justamente aí seria inverter a lógica do gate.

*Quem publica*: a regra de autoridade passa a decorrer da **origem da versão**, não do modo da definição. O motor publica o que calculou; o processor publica o que chegou pronto, venha da B3 ou de um arquivo. É uma generalização da regra anterior, não uma exceção a ela — e evita um terceiro escritor das tabelas de curva.

*O modelo para download é gerado por curva*, não é arquivo estático: as colunas e os prazos esperados dependem das convenções daquela definição. Modelo genérico faria o operador adivinhar o leiaute exatamente quando não há tempo para isso.

### D4c — Validação de consistência é gate, não relatório

Entre construir e publicar existe uma etapa de validação. Ela é uma peça própria, conceitualmente separada do motor: o motor sabe *montar* a curva, o validador sabe *desconfiar* dela.

O mecanismo é um estado a mais no ciclo de vida. A versão é gravada como `EM_VALIDACAO` — com vértices e procedência, na mesma transação —, o validador roda, e só então ela é promovida a `PUBLICADA` ou marcada como `REPROVADA`. Consumidores nunca enxergam versão que não passou pelo gate.

*Por que gate e não relatório posterior*: validar depois de publicar significa que a curva errada já foi consumida. O ponto de uma validação é impedir, não documentar.

*Por que estado no banco e não checagem em memória antes do commit*: assim o resultado da validação fica persistido e auditável mesmo quando reprova — que é justamente o caso em que alguém vai querer entender o que aconteceu. Uma checagem em memória que aborta a transação não deixa rastro nenhum.

*Bloqueante versus aviso*: nem todo teste pode reprovar. Violação de arbitragem e erro de reprecificação são defeito e bloqueiam. Já a variação contra o dia anterior acima do limiar pode ser movimento legítimo de mercado — bloquear ali significaria não publicar curva justamente no dia de choque, que é quando a mesa mais precisa dela. Esses testes emitem **aviso**: a curva é publicada, sinalizada, e o aviso aparece na tela.

*Onde a peça roda*: como módulo próprio, empacotado junto com o motor na POC. Separá-la em um pod acrescentaria um salto de rede e um ponto de falha exatamente dentro da janela crítica, imediatamente antes da publicação. O módulo é escrito de forma extraível, para que virar serviço depois seja decisão de operação e não reescrita.

### D5 — Reprocessamento gera versão nova; publicação nunca sobrescreve

Cada construção bem-sucedida cria uma `versao_curva` nova para o par `(definicao_curva, data_referencia, momento_curva)`. A anterior vira `SUPERSEDED`. Consultas sem versão explícita retornam a `PUBLISHED` mais recente; consultas com `asOf` retornam a versão vigente naquele instante.

*Alternativa considerada*: `UPDATE` in-place nos vértices. Rejeitada — apaga a resposta para "que curva o sistema devolveu ontem às 15h?", que é justamente a pergunta que Risco e auditoria fazem.

### D6 — Insumo faltante falha alto e nomeado

Se um fixing ou cotação exigido pela definição da curva não está no `ponto_dado_mercado`, a construção falha com `MissingMarketDataException` nomeando índice e data, o `execucao_curva` vai para `FAILED` e **nada é publicado**. Jamais interpolar, repetir o valor anterior ou usar default para tapar buraco de insumo. É o gate que impede curva silenciosamente errada.

### D7 — SQL Server 2022 local, Flyway, mesmo dialeto do Azure SQL

`mcr.microsoft.com/mssql/server:2022-latest` roda sob Podman e fala o mesmo T-SQL do Azure SQL, então as migrações Flyway escritas aqui valem lá.

*Alternativa considerada*: PostgreSQL ou H2 local. Rejeitada — divergência de dialeto e de tipos numéricos (`DECIMAL(28,12)` para taxas) reapareceria na migração para Azure, que é o destino declarado.

### D8 — Precisão: `DECIMAL(28,12)` no banco, `BigDecimal` em todo o caminho Java

Taxa, preço, fator e cotação nunca transitam como `double`, nem como intermediário, nem na serialização JSON (os DTOs serializam como *string* numérica). `double` só é permitido dentro de numérica genuinamente iterativa sem arredondamento de mercado (root-finding, splines), confinado ao interior do `curve-kernel`.

*Trade-off aceito*: JSON com números como string é menos ergonômico no front; o Angular converte na borda. O oposto — perder dígito significativo no `double` de JavaScript — é inaceitável para taxa.

### D9 — BFF é a fronteira de segurança; front nunca fala com as APIs de domínio

O front autentica via OIDC e só conhece o BFF. O BFF valida o token, resolve perfil (`CURVE_VIEWER`, `CURVE_OPERATOR`, `CURVE_ADMIN`) e chama as APIs internas com credencial de serviço. Na POC local, o provedor OIDC é um Keycloak em container; em produção, o IdP corporativo.

### D10 — Front como micro-frontend Module Federation no shell Liquid

O app Angular expõe um `remoteEntry.js` e é carregado pelo shell Liquid em runtime. Contrato com o shell: rota base, tema/tokens, contexto de usuário e barramento de eventos de navegação. O app também sobe standalone em dev (sem o shell), com um host de desenvolvimento mínimo.

*Alternativa considerada*: iframe. Rejeitada — quebra tema, navegação e acessibilidade compartilhados, e o shell Liquid existe justamente para unificá-los.

### D11 — Podman rootless com `podman compose`, portas altas e sem privilégio

Todos os serviços declaram healthcheck; a ordem de subida é resolvida por `depends_on: condition: service_healthy`. Migração Flyway e bootstrap de tópicos rodam como containers de inicialização que terminam com exit 0. Testcontainers, quando usado, aponta para o socket Podman (`DOCKER_HOST=unix:///run/user/$UID/podman/podman.sock`).

*Risco reconhecido*: healthcheck e `depends_on` têm suporte irregular entre versões de `podman-compose`. Mitigação em Riscos.

### D11b — Modelo de construção é escolha da definição de curva, não código fixo do motor

Cada definição de curva aponta para um **modelo de construção**, e existem dois tipos:

- **`BUILTIN`** — modelo fixo, implementado em Java dentro do motor, versionado junto com ele. É o padrão: a curva PRE de DI1 da B3 nasce apontando para o modelo padrão dela, e assim funciona sem que ninguém precise importar nada.
- **`GROOVY`** — modelo importado como script Groovy, registrado no catálogo de modelos e disponível para ser escolhido.

Trocar qual modelo uma curva usa é **mudança de configuração feita na tela**, não mudança de código: aponta-se a definição para outro modelo e a próxima construção já usa o novo. Voltar atrás é apontar de volta.

*Por que assim e não com pacote versionado formal*: o valor está em poder experimentar uma metodologia alternativa sem release da plataforma, e em conseguir comparar o resultado dela contra o modelo padrão. Manifesto, SDK versionado e cerimônia de promoção resolveriam um problema de governança que esta POC ainda não tem, ao custo de tornar a coisa toda pesada demais para o que ela precisa provar.

*O que não se abre mão*: o Groovy roda em contenção (sem I/O, com limite de tempo e memória), e a proveniência de toda curva construída registra qual modelo a produziu — sem isso, uma curva que muda de valor sem insumo novo vira mistério.

### D11c — Nenhum caminho de erro pode terminar sem avançar o offset

A forma mais comum de uma plataforma como esta parar é uma partição travar numa mensagem que nunca é superada: o consumidor falha, não confirma o offset, retenta para sempre, e a fila morre atrás dela.

A invariante que torna isso impossível: **ou o processamento tem sucesso, ou a mensagem vai para a dead-letter e o offset é confirmado.** Nunca "retenta indefinidamente".

Quatro caminhos precisam respeitá-la, e cada um tem defesa própria:

1. **Falha na desserialização** — acontece antes do código de negócio, então o tratamento de erro nunca é chamado. Exige desserializador que encapsule a falha em vez de lançá-la. É o caso que, sem tratamento explícito, produz exatamente o laço infinito.
2. **Retentativa sem terminador** — todo tratamento de erro termina obrigatoriamente em um recuperador que publica na dead-letter e avança. Retentativa infinita é proibida por configuração.
3. **Mensagem lenta demais** — se o processamento excede o intervalo máximo de poll, o broker declara o consumidor morto, provoca rebalance e a mensagem é reentregue para outro que também vai demorar: *livelock*. Defesa: mensagens pequenas (D1b), quantidade por poll dimensionada, e intervalo máximo medido sobre o pior caso real.
4. **Chamada externa sem timeout** — nenhum mecanismo do Kafka resolve; é disciplina de aplicação. Toda chamada externa em consumidor tem timeout, sempre menor que o intervalo máximo de poll.

*Rede de segurança para o que não foi previsto*: **offset parado com lag maior que zero**, monitorado por partição. Se o offset não avança há N minutos e ainda há mensagens à frente, algo travou — mesmo sem saber o quê. Vale mais que a soma dos alarmes específicos, e a média agregada esconderia exatamente a partição morta.

*Saída de emergência*: pular o offset problemático é operação destrutiva, exige aprovação, e precisa estar **documentada e ensaiada** — no dia em que a alternativa é a curva não sair, ter o procedimento escrito é a diferença entre cinco minutos e uma hora.

### D12 — Duas origens de curva, um só modelo de dados

Uma curva chega à plataforma por um de dois caminhos, e cada caminho produz uma **curva distinta no catálogo**:

- **`BOOTSTRAPPED`** — o feeder traz **dado individual por instrumento** (contratos DI1, por exemplo), o `curve-processor` persiste esses pontos em `ponto_dado_mercado`, e o `curve-engine` monta a curva por bootstrap, gravando os vértices.
- **`IMPORTED`** — o feeder traz a **curva já pronta** do endpoint de curva da B3 (as taxas de referência publicadas, vértice a vértice). Não há bootstrap: o `curve-processor` normaliza os vértices recebidos e a curva é publicada diretamente.

As duas gravam nas mesmas tabelas `versao_curva` / `vertice_curva` / `procedencia_curva`, respondem pelas mesmas APIs de consulta, aceitam a mesma interpolação síncrona e seguem a mesma regra de versionamento. O que muda é apenas quem publica e o que a proveniência registra: no `BOOTSTRAPPED`, os instrumentos e a versão da definição; no `IMPORTED`, o arquivo de origem e seu hash.

*Consequência arquitetural*: o `curve-engine` deixa de ser o único autor de vértices. O `curve-processor` também publica — mas exclusivamente para definições marcadas como `IMPORTED`, e sob as mesmas regras de versão e proveniência.

*Por que duas curvas distintas e não uma*: elas não são a mesma coisa. A construída é o que a metodologia da plataforma produz a partir do mercado observado; a importada é o que a B3 divulgou. Colapsá-las em uma esconderia justamente a diferença que interessa — e é essa diferença que permite comparar a curva própria contra a oficial, dia a dia, como controle contínuo em vez de conferência manual.

*Alternativa considerada*: tratar a curva pronta da B3 apenas como oráculo de teste, sem existir no catálogo. Rejeitada — usuários de mesa querem consultar a curva oficial pela mesma tela e pela mesma API, e um oráculo que só vive na suíte de testes não atende a isso.

### D13 — Reconciliação contra a B3 é o critério de aceite do fluxo

A POC não se declara pronta porque "rodou"; ela roda o reconciliador do `curve-kernel` comparando vértice a vértice a curva publicada contra a taxa de referência que a própria B3 divulga para a mesma data de pregão, arredondando o oráculo pela mesma `RoundingPolicy` de produção e comparando exato.

## Risks / Trade-offs

- **`podman-compose` com suporte irregular a healthcheck/`depends_on`** → o compose declara healthchecks, mas a subida é feita por `deploy/podman/up.sh`, que faz espera ativa por serviço (porta + query de sanidade) antes de liberar o próximo estágio; assim o fluxo funciona mesmo onde o `depends_on` é ignorado. Validar também `podman kube play` como plano B.
- **SQL Server em container consome ~2 GB de RAM e demora a ficar pronto** → healthcheck via `sqlcmd` com retry generoso; documentar o requisito de memória; oferecer perfil `lite` do compose sem Redis/Keycloak para máquinas apertadas.
- **Kernel próprio reimplementado a partir de uma metodologia já validada em outro lugar** → o risco não é mais deriva entre repositórios acoplados, e sim reintroduzir aqui um erro que já estava resolvido lá. Mitigação: portar junto os casos de teste e as fixtures reais, e exigir que a curva construída reconcilie exato contra a taxa de referência oficial da B3 antes de o kernel ser considerado pronto — o oráculo é externo aos dois repositórios.
- **Endpoints públicos da B3 mudam sem aviso e sem contrato** → o feeder isola o acesso em um cliente com fixtures gravadas; os testes rodam contra fixture, e há um teste de contrato marcado como opcional que bate na B3 real e é o único que pode falhar por causa externa.
- **Retenção do tópico `raw` versus custo de storage** → 7 dias na POC. Depois disso, reprocessar exige rebaixar da fonte; aceitável porque o dado normalizado está no SQL.
- **`correlationId` só é útil se ninguém quebrar a corrente** → propagação é requisito de spec em cada componente e é verificada por um teste de integração ponta a ponta, não por convenção.
- **Cache Redis pode devolver curva obsoleta após republicação** → a chave de cache inclui `versionId`; publicar nova versão invalida por construção, sem depender de TTL.
- **Escopo grande dividido em oito mudanças** → risco de os contratos derivarem entre elas. Mitigação: `contracts/` é a fonte de verdade e as sete mudanças-irmãs dependem desta; alterar contrato é alterar esta mudança.
- **Orçamento de tempo calibrado por suposição em vez de medição** → o horário real de divulgação da B3 por conjunto de dados é a variável que define se o prazo é viável, e hoje é desconhecido. Mitigação: registrar o horário efetivo de divulgação observado a cada dia e a margem para o corte, e tratar o orçamento inicial como provisório até haver série medida.
- **Bloqueio de cabeça de fila consumir orçamento da janela crítica** → retentativa bloqueante preserva ordem, mas trava a partição enquanto dura, e chaves distintas podem colidir na mesma partição. Mitigação: faixas separadas (D1c), teto de **tempo** na retentativa além do teto de tentativas, e a invariante de D11c garantindo que a mensagem sai da partição em tempo limitado.
- **Lote incompleto por falha do feeder no meio do arquivo** → com a quebra em blocos, parte dos registros pode chegar e parte não. Mitigação: contagem de blocos declarada no envelope; lote incompleto após o tempo limite é falha explícita e não gera pedido de construção — melhor não construir do que construir com metade dos contratos.
- **Faixa prioritária virar o caminho padrão** → ela é desvio, não conserto: a causa da travada continua lá e volta amanhã. Mitigação: pendência em dead-letter mantém a causa visível, e a invariante de D11c mantém a rotina destravando sozinha.

## Migration Plan

1. **Fundação** — estrutura do monorepo, `contracts/` (schemas de evento e OpenAPI), migrações Flyway iniciais e compose Podman com Kafka, SQL Server e Redis subindo verdes.
2. **Ingestão** — `feeder-b3-marketdata` quebra o arquivo em blocos e publica na faixa de rotina; `curve-processor` normaliza e persiste por bloco, consolidando o lote pela contagem. Critério: uma data de pregão B3 inteira no `ponto_dado_mercado`, com lote completo.
3. **Domínio** — `curve-api` (cadastro) permite definir as duas curvas: a PRE construída a partir de DI1 e a PRE oficial importada da B3. O `curve-engine` constrói e publica a primeira; o `curve-processor` publica a segunda. A comparação entre as duas passa a ser possível pela própria API.
4. **Automação** — `curve-orchestrator` agenda a ingestão diária, expõe disparo sob demanda na faixa prioritária com rastreio de execução, e passa a conhecer o prazo de publicação de cada curva.
5. **Produto** — `curve-bff` e `curve-web-ui` fecham o ciclo até a tela, incluindo disparo manual e monitoramento.
6. **Aceite** — smoke test ponta a ponta em um comando: sobe o ambiente, dispara a ingestão de uma data, espera a curva publicada, consulta interpolada e reconcilia contra a B3.

**Rollback**: cada estágio é independente e o ambiente é descartável (`podman compose down -v`). Não há dado de produção envolvido; reverter é apagar volumes e voltar ao commit anterior.

## Open Questions

- O shell Liquid disponibiliza contrato de tema e de contexto de usuário documentado, ou o app Angular precisa descobrir isso por integração? Impacta `curve-web-ui`.
- Em produção, o transporte é Kafka gerenciado ou Azure Event Hubs com API Kafka? Muda configuração de segurança e cotas, não o código.
- Curva intradiária entra no escopo da POC ou só abertura e fechamento? O desenho suporta os três `momento_curva`, mas só abertura e fechamento têm dado de teste.
- Qual o horário de fechamento do banco que serve de corte para cada curva? Sem ele, o orçamento de tempo não tem âncora.
- Qual o tamanho de bloco adequado para os arquivos reais da B3? Precisa ser medido, não estimado.
- O alerta preditivo de risco de atraso deve notificar por canal externo, ou basta o painel do dia?
- Multi-tenant / segregação por mesa: há necessidade de escopo de visibilidade por curva, ou todo usuário autenticado vê todas as curvas?
- Bloomberg e LSEG entregam por arquivo, API ou stream? Determina se o contrato de feeder atual (pull agendado) cobre os três ou se falta um modo push.

## Why

A plataforma só existe se o dado de mercado entrar. Hoje os feeders são Azure Functions em Node.js que jogam dado dos provedores no Kafka, mas **não há contrato escrito do que um feeder é obrigado a fazer**: não há garantia de idempotência, de propagação de `correlationId`, de falha explícita quando a fonte cai, nem de como um feeder novo (Bloomberg, LSEG) entra sem alterar o consumidor.

Esta mudança define o **contrato genérico de feeder** e implementa o **feeder B3** — a única fonte da POC — como worker Node.js/TypeScript que roda tanto como Azure Function em produção quanto como container sob Podman no ambiente local.

## What Changes

- **Contrato de feeder** comum a todas as fontes: entrada (`source`, `dataset`, `referenceDate`, `correlationId`, faixa de ingestão), saída (eventos em blocos no envelope comum, publicados na faixa recebida), semântica de falha e de retentativa, e reporte de progresso ao `curve-orchestrator`.
- **Feeder B3 implementado** para dois tipos de insumo, que alimentam curvas distintas:
  - **dado individual por instrumento** — arquivo de Preços de Referência (PR), BVBG.086 (preços e ajustes) e BVBG.028 (cadastro de instrumentos), de onde saem os contratos DI1 que a plataforma usa para montar a curva construída;
  - **curva já pronta** — o endpoint de Taxas de Referência da B3, que entrega a curva oficial vértice a vértice e alimenta a curva importada.
- **Quebra em blocos**: o feeder divide o conteúdo adquirido em blocos de registros e publica um evento por bloco, com `loteId`, `sequencia` e `totalBlocos`. Elimina o limite de 1 MB por mensagem — que o Azure Event Hubs no tier Standard impõe sem possibilidade de configuração —, reduz latência e faz um registro defeituoso contaminar um bloco, não o dia inteiro.
- **Corte estrutural, não semântico**: o feeder conhece o formato de container (XML com elemento repetido, texto com linhas) e nada do domínio financeiro. Nenhum código do feeder sabe o que é taxa, vencimento ou contrato.
- **Publicação na faixa recebida**: o disparo informa a faixa — rotina, prioritária ou massa — e o feeder publica no tópico correspondente, sem decidir por conta própria.
- **Download resiliente**: timeout, retentativa com backoff, verificação de integridade do arquivo e detecção de "data sem publicação" distinta de "fonte indisponível".
- **Idempotência na origem**: `loteId` derivado de `(source, dataset, referenceDate, hash do conteúdo)` e `eventId` de `(loteId, sequencia)`, para que redisparar o mesmo feeder não gere trabalho duplicado a jusante.
- **Dois modos de execução com o mesmo código**: handler de Azure Function em produção e worker de container em local, com a lógica de negócio isolada de ambos.
- **Ponto de extensão declarado** para Bloomberg e LSEG: um novo feeder implementa a mesma interface, publica nas mesmas faixas, e o `curve-processor` só precisa de um parser de dataset.
- **Fixtures gravadas** de respostas reais da B3, para que a suíte de testes rode sem internet; um único teste de contrato opcional bate na B3 real.

## Capabilities

### New Capabilities

- `feeder-runtime`: contrato genérico que todo feeder SHALL cumprir — assinatura de execução, quebra em blocos com identificação de lote, publicação na faixa recebida, envelope de saída, propagação de `correlationId`, idempotência, política de retentativa, reporte de progresso, health e modo de execução (Azure Function ou container).
- `b3-market-data-feeder`: aquisição dos datasets B3 de dado individual (PR, BVBG.086, BVBG.028) e de curva pronta (Taxas de Referência), classificação explícita do tipo de insumo no evento, corte estrutural do arquivo em blocos, tratamento de calendário de pregão, detecção de data sem publicação e verificação de integridade.

### Modified Capabilities

<!-- Nenhuma. -->

## Impact

- **Novo serviço**: `services/feeder-marketdata/` em Node.js 20 + TypeScript, com `package.json`, build para container e handler de Azure Function.
- **Depende de** `curves-solution-architecture`: envelope de evento, catálogo de tópicos e regra de `correlationId` vêm de `contracts/`.
- **Fontes externas**: endpoints públicos da B3 — arquivos de preços e o proxy de taxas de referência com payload em Base64. São endpoints sem contrato formal e podem mudar sem aviso.
- **Dados versionados**: fixtures de resposta da B3 entram no repositório em `services/feeder-marketdata/test/fixtures/`.
- **Não implementa**: feeders de Bloomberg e LSEG; apenas o ponto de extensão fica pronto.
- **Não faz**: nenhuma normalização de domínio nem acesso a banco — o feeder publica o bruto e para por aí.

# Feeder de Market Data (`function-marketdata`)

Serviço responsável por adquirir dados de mercado de fontes externas (hoje: **B3** e **ANBIMA**, com contrato arquitetural preparado para futuras integrações como Bloomberg e LSEG) e publicá-los no Apache Kafka, realizando a quebra estrutural de arquivos volumosos em blocos identificados por lote.

Desenvolvido em **Node.js 20 + TypeScript estrito**, formato **ESM**, testes com **vitest** e servidor de saúde HTTP nativo (`node:http`, sem frameworks externos).

---

## Estado Atual

### Implementado e testado, contra fonte real

- **Núcleo de aquisição**: interface `Feeder` com retorno padronizado em 3 estados (`PUBLISHED`, `NO_DATA`, `FAILED`).
- **Identificadores determinísticos**: cálculo determinístico de `loteId` e `eventId`.
- **Quebra em blocos**: divisão de arquivos volumosos com corte estrutural — por elemento XML repetido (`src/xml-estrutural.ts`, B3) ou por linha (`src/linhas.ts`, ANBIMA). Ambos operam sobre `Buffer`, nunca decodificam o arquivo inteiro para `string` antes de cortar — o BVBG.028 real chega a ~800MB, acima do limite de comprimento de string do V8.
- **Cliente HTTP**: retentativas com backoff exponencial só para falha de transporte.
- **Integridade**: conteúdo vazio, tamanho declarado (`Content-Length`, quando a fonte o envia), arquivo ZIP bem formado (`src/zip.ts`, B3).
- **Calendário de pregão**: feriados nacionais e decisão de dia de pregão (B3/ANBIMA).
- **Produtor Kafka real**: `kafkajs` (`src/kafka-producer-real.ts`), com validação estrita de schema (`ajv`) antes do envio.
- **Datasets B3** (`src/feeders/b3-arquivo-pesquisa-pregao.ts`): `PR_DI1`/`BVBG.086` (mesmo arquivo real) e `BVBG.028`, via `https://www.b3.com.br/pesquisapregao/download?filelist=<PREFIXO><AAMMDD>.zip`. A resposta real é um ZIP duas vezes aninhado contendo revisões intraday — usa-se a mais recente.
- **Dataset ANBIMA** (`src/feeders/anbima-mercado-secundario.ts`): `ANBIMA_MERCADO_SECUNDARIO`, via `https://www.anbima.com.br/informacoes/merc-sec/arqs/ms<AAMMDD>.txt` — texto `@`-delimitado, ISO-8859-1, taxas indicativas/PU de títulos públicos federais (LTN/LFT/NTN-B/NTN-C/NTN-F).
- **Adaptador de container** (`src/main.ts`): lê parâmetros de ambiente, roda uma aquisição, sai com código 0 (`PUBLISHED`/`NO_DATA`) ou 1 (`FAILED`). Verificado de ponta a ponta contra Kafka real.
- **Handler de Azure Function** (`src/azure-function-handler.ts`): mesmo núcleo, parâmetros por corpo JSON HTTP, resposta HTTP.
- **Servidor de saúde**: endpoint HTTP nativo para checagem de saúde.
- **Roteamento de datasets**: catálogo único via `RegistroFeeders`, montado por `src/registro-feeders-completo.ts` a partir dos registros por fonte (`registro-feeders-b3.ts`, `registro-feeders-anbima.ts`).

### Pendente (bloqueado por falta de fixture real)

- **Dataset de curva pronta da B3** (endpoint de taxas de referência): nenhum arquivo real desse endpoint foi encontrado ainda — o `TS260821.ex_` investigado é um dataset diferente (preços de títulos públicos, não vértices de curva).
- **Reporte de orquestração**: `curve-orchestrator` ainda não expõe nenhum endpoint HTTP real para reportar início/resultado.

---

## Datasets do Catálogo

| Dataset | Fonte | Descrição | Status |
| :--- | :--- | :--- | :--- |
| `PR_DI1` / `BVBG.086` | B3 | Preços de referência / ajustes (mesmo arquivo real) | Implementado |
| `BVBG.028` | B3 | Cadastro de instrumentos | Implementado |
| curva de referência | B3 | Curva pronta | Bloqueado — sem fixture real |
| `ANBIMA_MERCADO_SECUNDARIO` | ANBIMA | Taxas indicativas / PU de títulos públicos (LTN/LFT/NTN-B/NTN-C/NTN-F) | Implementado |
| `BLOOMBERG_JUROS_CAMBIO` | Bloomberg | Juros, câmbio e outros insumos de curva, via Bloomberg Data License (arquivo em lote assíncrono — submeter pedido, aguardar geração, buscar arquivo pronto) | Implementado, **NÃO verificado contra a Bloomberg real** (sem credenciais/ambiente disponíveis) — construído e testado só contra fixture simulada (`fixtures/bloomberg-arquivo-fixture.csv`). Só roda pelo caminho CLI/agendado (`main.ts`); nunca pelo caminho HTTP síncrono (`main-http.ts`/Azure Function), porque a espera pode levar minutos a horas. |

---

## Tópicos Kafka e Faixas de Ingestão

Os tópicos de destino são definidos em `contracts/events/topics.yaml` e resolvidos via `src/faixa-topico.ts`. A **faixa de ingestão** deve ser obrigatoriamente informada em toda publicação (não há faixa padrão).

| Faixa de Ingestão | Tópico Kafka de Destino |
| :--- | :--- |
| `ROTINA` | `marketdata.rotina.v1` |
| `PRIORITARIA` | `marketdata.prioritaria.v1` |
| `MASSA` | `marketdata.massa.v1` |

- **Chave de Partição**: toda mensagem enviada utiliza a chave no formato:
  ```text
  source|dataset|referenceDate
  ```
- **Envelope de Evento**: o formato do envelope segue o contrato comum `contracts/events/envelope.schema.json`, validado em tempo de execução via `ajv` pelo produtor Kafka antes do envio. Os tipos TypeScript correspondentes estão definidos em `src/envelope.ts`.

---

## Rodando uma aquisição real

### Via `main.ts` (adaptador de container)

Variáveis de ambiente obrigatórias: `ACQUISITION_DATASET`, `ACQUISITION_REFERENCE_DATE` (YYYY-MM-DD), `ACQUISITION_FAIXA` (`ROTINA`/`PRIORITARIA`/`MASSA`), `KAFKA_BOOTSTRAP_SERVERS`. Opcionais: `ACQUISITION_CORRELATION_ID` (gera um UUID se omitido), `HEALTH_PORT` (padrão `8090`).

```bash
ACQUISITION_DATASET=BVBG.086 \
ACQUISITION_REFERENCE_DATE=2026-08-21 \
ACQUISITION_FAIXA=ROTINA \
KAFKA_BOOTSTRAP_SERVERS=localhost:19092 \
npx tsx src/main.ts
```

### Via container Podman (ambiente local)

`function-marketdata` não é um serviço de pé como os demais do `compose.yaml` — ele roda uma aquisição por execução e termina ("pull agendado", ver "Extensibilidade" abaixo). Por isso fica atrás de um profile dedicado, não sobe com `up.sh`/`up.ps1`:

```bash
cd deploy/podman
ACQUISITION_REFERENCE_DATE=2026-08-21 \
podman compose -f compose.yaml --profile feeder-on-demand run --rm function-marketdata
```

---

## Desenvolvimento e Testes Locais

### Scripts NPM
Execute a partir do diretório do serviço (`services/function-marketdata/`):

- `npm install` — Instala as dependências do serviço.
- `npm run sync-contracts` — Copia os schemas JSON (`contracts/events/*.schema.json`) da raiz do monorepo para a pasta local `contracts/events/` deste serviço (necessário para leitura e validação em runtime pelo `ajv`). Este script é executado automaticamente antes do `build` e do `test`.
- `npm run build` — Executa a checagem de tipos estrita (`tsc --noEmit`).
- `npm test` — Executa a suíte de testes com o `vitest`. Não depende de rede nem de Kafka real (tarefa 6.10).
- `npm run test:contract` — Executa os testes de contrato opcionais contra a B3 e a ANBIMA **reais** (config dedicada `vitest.contract.config.ts`), fora do build padrão — depende de rede.
- `npm run lint` — Executa a verificação estática com ESLint.
- `npm run format` / `npm run format:check` — Formata ou verifica a formatação de código com Prettier.

### Execução Isolada (Sem Kafka e Sem Rede)
A suíte de testes padrão pode ser executada localmente sem depender de Kafka ou de conexões externas de rede:
```bash
npm install
npm test
```
Todo cliente HTTP (`fetch`), publicador Kafka e rotinas de espera/backoff são injetáveis como fakes nos testes.

### Fixtures Reais
Para testes de parsing e quebra em blocos sem acesso à rede, o serviço conta com fixtures em `fixtures/`:
- `fixtures/BVBG.086.01_fixture.xml`: recorte real contendo 5 elementos `<BizGrp>` extraídos de arquivos de produção da B3.
- `fixtures/BVBG.028.02_fixture.xml`: recorte real contendo 5 elementos `<BizGrp>` extraídos de arquivos de produção da B3.
- `fixtures/ms260821_fixture.txt`: arquivo real **completo** de mercado secundário da ANBIMA (6.812 bytes, pequeno o bastante para versionar por inteiro).

A proveniência e os detalhes dessas fixtures estão documentados em `fixtures/README.md`.

---

## Servidor de Saúde

O módulo `src/servidor-saude.ts` disponibiliza um servidor HTTP nativo (`node:http`, sem dependência de frameworks):
- **Endpoint**: `GET /health`
- **Resposta**: `{"status":"UP"}`
- **Porta**: recebida como parâmetro na inicialização (`HEALTH_PORT` em `main.ts`, padrão `8090`).

---

## Build de Container

O build da imagem de container é definido em `services/function-marketdata/Containerfile`.

> **Importante**: O **contexto de build** deve ser a raiz do monorepo (e não o diretório do serviço), pois o processo copia os schemas de `contracts/events/` da raiz para dentro da imagem.

Comando para build (executado a partir da raiz `D:\Workspace\poc-curvas`):
```bash
podman build -f services/function-marketdata/Containerfile -t function-marketdata .
```

O `ENTRYPOINT` é `node dist/main.js` (o adaptador de container) — `podman run` funciona de ponta a ponta, verificado de verdade contra o Kafka local (ver "Rodando uma aquisição real" acima).

---

## Extensibilidade

A interface `Feeder` localizada em `src/feeder.ts` é o ponto de extensão central do serviço. O contrato foi desenhado para acomodar múltiplos provedores de dados de mercado — hoje B3 e ANBIMA, com Bloomberg e LSEG como extensões futuras — sem mudar nenhum adaptador de execução; ver `docs/extensao-feeders.md` para o que um feeder novo precisa implementar.

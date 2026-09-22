## Context

Ver `proposal.md` para o motivo. Pontos de contexto técnico levantados na inspeção do repositório, necessários para as decisões abaixo:

- **Não existe driver de banco no conector hoje.** `services/conector/package.json` está vazio (0 bytes) — nenhuma dependência de DB foi declarada ainda.
- **O H2 local (`db/h2/schema.sql`) já roda de um jeito específico e compartilhado**: `scripts/start-h2-local.ps1`/`.sh` sobe um servidor H2 TCP **nativo** (processo Java, fora de container/Podman), banco único `curvasdb` na porta `11433`, com `jdbc:h2:tcp://localhost:11433/curvasdb;MODE=MSSQLServer;CASE_INSENSITIVE_IDENTIFIERS=TRUE`. Esse mesmo banco já é compartilhado por `curve_processor_app`, `curve_api_app`, `curve_engine_app`, `curve_orchestrator_app` (usuários criados no próprio `schema.sql`) — todos serviços Java/Spring conectando via JDBC nativo.
- **Protocolo TCP do H2 é proprietário e só tem driver JDBC (Java).** Não existe driver Node.js/TypeScript para esse protocolo. O conector é Node.js/TypeScript — não pode conectar como os demais serviços conectam.
- A tabela-alvo é `tBtrsCurvaPrimr` (V22/V25): PK é um surrogate `cldtfdUnic` (sem constraint natural de unicidade por ticker+data), com FK obrigatória para `tCurvaMercd.cTickerIndcd`.

## Goals / Non-Goals

**Goals**
- Conector consegue ler `TaxaSwap.txt` do Blob (hoje só escreve) e gravar vértices brutos no mesmo `curvasdb` que os demais serviços já usam localmente.
- Reprocessamento por data é seguro de rodar múltiplas vezes sem duplicar linhas.
- Nenhuma credencial real de banco é definida nesta change — tudo por variável de ambiente, com defaults de desenvolvimento local equivalentes ao que os outros serviços já usam.
- Código novo desta change (acesso a dados, orquestração de ingestão, endpoints HTTP) sai com ≥ 90% de cobertura de teste, verificado via `jest --coverage` — ver `tasks.md` Seção 8.

**Non-Goals**
- Não implementa o schema "real" versionado (`definicao_curva`/`versao_curva`/`ponto_dado_mercado`) descrito no contexto do projeto — grava apenas na tabela legada `tBtrsCurvaPrimr`, que é um schema diferente e já existente.
- Não implementa cálculo/bootstrap de curva (isso é do `curve-engine`) nem cadastro de novas curvas no catálogo (`tCurvaMercd`) — o ticker precisa já existir lá.
- Não altera os pipelines já existentes do conector (`b3HttpTrigger`, `b3ContingencyHttpTrigger`).
- Não decide autenticação real do webhook — mantém o padrão já usado nos outros triggers do conector (`authLevel: "anonymous"`), por ser escopo de POC local.

## Decisions

### 1. Conectividade Node.js → H2: expor protocolo PG no mesmo servidor H2 compartilhado
**Decisão**: estender `scripts/start-h2-local.ps1`/`.sh` para também iniciar o listener PostgreSQL-compatível do H2 (`org.h2.tools.Server -pg -pgPort <porta> -pgAllowOthers`), no mesmo processo/banco `curvasdb` já usado pelos serviços Java. O conector conecta usando o driver `pg` (npm), padrão de mercado para Node.js, apontando para essa porta PG.

**Por quê**: o protocolo TCP nativo do H2 só tem driver Java. Rodar um segundo H2 separado só para o conector fragmentaria o `curvasdb` compartilhado (dados de catálogo `tCurvaMercd` cadastrados pelos outros serviços não apareceriam para o conector). Expor `-pg` no mesmo processo mantém um único banco, uma única fonte de verdade, sem introduzir infraestrutura nova.

**Alternativas consideradas e rejeitadas**:
- *Bridge JDBC↔HTTP dedicado*: um processo Java auxiliar que expõe o H2 via HTTP para o conector chamar. Rejeitado por adicionar mais um serviço a operar só para isso.
- *ORM/driver JDBC via binding nativo Node↔Java*: inexistente de forma madura para esse caso; complexidade desproporcional para uma POC.

**Risco conhecido**: a emulação PG do H2 pode ter incompatibilidades com tipos/DDL específicos do `MODE=MSSQLServer` usado no `curvasdb` (ex.: `DECIMAL(28,16)`, colunas `IDENTITY`). **Mitigação**: validar com uma query real de leitura/escrita em `tBtrsCurvaPrimr` logo no início da implementação (tarefa dedicada em `tasks.md`); se a emulação PG não for compatível o suficiente, a alternativa de bridge HTTP fica como plano B — não muda o restante do design.

### 2. Driver e camada de acesso a dados: `pg` cru, sem ORM
**Decisão**: usar o pacote `pg` diretamente, com SQL parametrizado escrito à mão (sem Knex/Prisma/TypeORM).

**Por quê**: consistente com o estilo enxuto já usado no resto do conector (sem framework de acesso a dados até agora); evita introduzir uma dependência pesada para uma única tabela de escrita.

### 3. Configuração de conexão: só variáveis de ambiente, sem valores reais nesta change
**Decisão**: nova variável `B3_DB_URL` (connection string estilo Postgres, ex.: `postgres://usuario:senha@host:porta/curvasdb`) seguindo o mesmo padrão de configuração via env já usado para Blob (`B3_BLOB_CONNECTION_STRING`) e Kafka (`KAFKA_BROKERS`). Nenhum valor de exemplo com credencial real é gravado nesta change — segue para os arquivos `local.settings.*.example.json` já existentes no conector, no mesmo padrão dos demais.

### 4. Reprocessamento idempotente por data: DELETE + INSERT transacional
**Decisão**: ao (re)processar uma data de referência, apagar as linhas existentes em `tBtrsCurvaPrimr` para os tickers daquela `dBaseReft` antes de inserir o conjunto recém-parseado, dentro de uma transação (tudo ou nada).

**Por quê**: `tBtrsCurvaPrimr` é uma tabela de vértices **brutos** (staging), sem conceito de versionamento no schema legado — diferente da regra geral do projeto de "reprocessamento gera nova versão", que vale para a curva **publicada** (`versao_curva`, fora do escopo desta change). Aqui, "substituir" é o comportamento correto para não acumular lixo a cada reprocessamento manual.

### 5. Catálogo ausente (`tCurvaMercd`) não é criado automaticamente
**Decisão**: se um ticker do arquivo não existe em `tCurvaMercd`, o vértice correspondente não é gravado, é reportado como pendência na resposta da execução, e o processamento dos demais tickers continua normalmente.

**Por quê**: segue a regra do projeto de nunca fabricar dado ausente — cadastro de curva é responsabilidade de outro fluxo, não deste.

## Risks / Trade-offs

- [Emulação PG do H2 incompatível com `MODE=MSSQLServer`] → validar cedo com teste de integração real contra o `curvasdb` local; plano B é bridge HTTP dedicado (não muda specs nem tasks de alto nível, só a tarefa de conectividade).
- [Webhook sem autenticação real] → aceitável para escopo de POC local; documentado como limitação, não como decisão definitiva de produção.
- [DELETE + INSERT em `tBtrsCurvaPrimr` perde histórico de reprocessamentos anteriores] → aceitável porque a tabela é staging bruta, não a curva publicada (que mantém histórico via `versao_curva`, fora do escopo aqui).

## Migration Plan

1. Estender `scripts/start-h2-local.ps1` e `.sh` para adicionar o listener `-pg` ao mesmo processo H2 (mudança pequena e aditiva — não quebra os serviços Java já conectados via TCP nativo).
2. Adicionar `pg` como dependência do conector e a nova variável `B3_DB_URL` aos arquivos `local.settings.*.example.json`.
3. Implementar o serviço de acesso a dados e os dois novos endpoints, reaproveitando parser/normalizador existentes.
4. Nenhum rollback especial necessário: mudança é aditiva; desligar os novos endpoints não afeta os pipelines existentes.

## Open Questions

- Formato exato do payload do webhook (quem o envia e qual JSON manda) não foi especificado pelo usuário — `tasks.md` vai assumir um payload mínimo razoável (`{ "data": "YYYY-MM-DD" }`) e isso pode ser ajustado sem impacto no restante do design quando o emissor real do webhook for definido.

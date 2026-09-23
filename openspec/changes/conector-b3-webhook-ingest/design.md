## Context

Ver `proposal.md` para o motivo. Pontos de contexto técnico levantados na inspeção do repositório, necessários para as decisões abaixo:

- **Não existe driver de banco no conector hoje.** `services/conector/package.json` está vazio (0 bytes) — nenhuma dependência de DB foi declarada ainda.
- **O schema oficial do projeto é o H2 transcrito de fotos do sistema real (V22/V25)** — o caminho exato do `.sql` correspondente na fonte real não está fixado nesta change; a implementação deve localizar o script real em vez de presumir um caminho deste repositório. A tabela-alvo é `tBtrsCurvaPrimr`: PK é um surrogate `cldtfdUnic` (sem constraint natural de unicidade por ticker+data), com FK obrigatória para `tCurvaMercd.cTickerIndcd`.
- **Esta change é só planejamento.** A implementação não será feita nesta sessão — por isso a conectividade Node.js → H2 (driver, protocolo) fica deliberadamente em aberto aqui, para ser resolvida quando a implementação for retomada, em vez de travada como decisão de design agora.

## Goals / Non-Goals

**Goals**
- Conector consegue ler `TaxaSwap.txt` do Blob (hoje só escreve) e gravar vértices brutos em `tBtrsCurvaPrimr`.
- Reprocessamento por data é seguro de rodar múltiplas vezes sem duplicar linhas.
- Nenhuma credencial real de banco é definida nesta change — tudo por variável de ambiente.
- Código novo desta change sai com ≥ 90% de cobertura de teste, verificado via `jest --coverage` — ver `tasks.md`.

**Non-Goals**
- Não implementa cálculo/bootstrap de curva (isso é do `curve-engine`) nem cadastro de novas curvas no catálogo (`tCurvaMercd`) — o ticker precisa já existir lá.
- Não altera os pipelines já existentes do conector (`b3HttpTrigger`, `b3ContingencyHttpTrigger`).
- Não decide autenticação real do webhook — mantém o padrão já usado nos outros triggers do conector (`authLevel: "anonymous"`), por ser escopo de POC local.
- Não decide o driver/protocolo de conexão Node.js → H2 nesta change — fica para a implementação, dado que ela não ocorre nesta sessão.
- Não implementa o código desta change nesta sessão — fica só o planejamento (proposal/specs/design/tasks) para retomar depois.

## Decisions

### 1. Persistência: `tBtrsCurvaPrimr`, sem inventar schema novo
**Decisão**: gravar cada vértice parseado como uma linha em `tBtrsCurvaPrimr` (`cTickerIndcd`, `cDiaCorri`, `cDiaUtil`, `dBaseReft`, `vPrecoTx`, e os fatores diário/acumulado já calculados pela lógica existente do conector).

**Por quê**: é a tabela que o próprio schema oficial já reserva para isso — `tBtrsCurvaPrimr` é documentada como "vértices brutos das 5 curvas TS B3 (fonte: TaxaSwap.txt)".

### 2. Configuração de conexão: só variáveis de ambiente, sem valores reais nesta change
**Decisão**: nova variável `B3_DB_URL` (ou equivalente, a definir na implementação), seguindo o mesmo padrão de configuração via env já usado para Blob (`B3_BLOB_CONNECTION_STRING`) e Kafka (`KAFKA_BROKERS`). Nenhum valor de exemplo com credencial real é gravado nesta change.

**Nota**: o driver/protocolo específico para o Node.js conectar no H2 fica como decisão de implementação, não travada aqui — ver Open Questions.

### 3. Reprocessamento idempotente por data: DELETE + INSERT transacional
**Decisão**: ao (re)processar uma data de referência, apagar as linhas existentes em `tBtrsCurvaPrimr` para os tickers daquela `dBaseReft` antes de inserir o conjunto recém-parseado, dentro de uma transação (tudo ou nada).

**Por quê**: `tBtrsCurvaPrimr` é uma tabela de vértices brutos (staging), sem conceito de versionamento no schema — diferente da regra geral do projeto de "reprocessamento gera nova versão", que vale para a curva publicada (fora do escopo desta change). Aqui, "substituir" é o comportamento correto para não acumular lixo a cada reprocessamento manual.

### 4. Catálogo ausente (`tCurvaMercd`) não é criado automaticamente
**Decisão**: se um ticker do arquivo não existe em `tCurvaMercd`, o vértice correspondente não é gravado, é reportado como pendência na resposta da execução, e o processamento dos demais tickers continua normalmente.

**Por quê**: segue a regra do projeto de nunca fabricar dado ausente — cadastro de curva é responsabilidade de outro fluxo, não deste.

## Risks / Trade-offs

- [Conectividade Node.js → H2 não decidida nesta change] → resolver na implementação; ver Open Questions para o ponto de partida.
- [Webhook sem autenticação real] → aceitável para escopo de POC local; documentado como limitação, não como decisão definitiva de produção.
- [DELETE + INSERT em `tBtrsCurvaPrimr` perde histórico de reprocessamentos anteriores] → aceitável porque a tabela é staging bruta, não a curva publicada (que mantém histórico via `versao_curva`, fora do escopo aqui).

## Migration Plan

Não aplicável nesta change — é só planejamento, sem implementação nesta sessão. Quando a implementação for retomada: decidir driver/conectividade H2 (Open Questions), adicionar a dependência ao `package.json`, implementar o serviço de acesso a dados e os dois novos endpoints, reaproveitando parser/normalizador existentes. Mudança é aditiva; não há rollback especial.

## Open Questions

- **Driver/protocolo Node.js → H2**: o protocolo TCP nativo do H2 só tem driver Java (JDBC). Isso precisa ser resolvido na implementação — opções a avaliar nesse momento incluem expor o H2 compartilhado (`scripts/start-h2-local.ps1`/`.sh`, banco `curvasdb`) também via protocolo PostgreSQL (`-pg`) para usar o driver `pg` do Node, ou outra alternativa. Não travado aqui porque a implementação não ocorre nesta sessão.
- Formato exato do payload do webhook (quem o envia e qual JSON manda) não foi especificado pelo usuário — quando a implementação for retomada, assumir um payload mínimo razoável (`{ "data": "YYYY-MM-DD" }`) até o emissor real ser definido.

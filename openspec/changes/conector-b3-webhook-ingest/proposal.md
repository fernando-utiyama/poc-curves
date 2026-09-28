## Why

O `TaxaSwap.txt` da B3 chega ao projeto por dois gatilhos no `services/conector`, e nenhum entrega o que o engine precisa:
- **Processamento do `TaxaSwap.txt`** (`b3HttpTrigger`, rota `swap-process`): lê o arquivo já gravado no Blob (a B3 não publica o `.txt` por URL direta), interpreta o arquivo em Node e publica no Kafka uma mensagem por vértice. Classifica a curva pela descrição (`normalizeCurveType`), o que publica DCL e DPL como DOL e descarta PTX e INP. Manda o valor como número de ponto flutuante, com fatores que o engine não usa. O processor recebe vértice a vértice, sem saber quando a carga termina, e grava por (ticker, data), o que deixa uma linha por curva em vez de 278.
- **Download do arquivo compactado `TSaammdd.ex_`** (hoje `b3ContingencyHttpTrigger`, rota `swap-contingency`): baixa o arquivo, extrai o texto e o salva no Blob em `b3/{AAAAMMDD}/TaxaSwap.txt`, e para aí. Grava na pasta da data do download, que pode não ser a data do arquivo. A versão anterior deste change propunha o conector gravando direto no banco, o que criaria um segundo gravador do mesmo dado, fora do processor e sem avisar o engine.

O `.ex_` é o único download da B3, e o `b3/taxa-swap/reprocessamento` só processa o que já está no Blob. Também não há como um usuário enviar o arquivo.

## What Changes

- **Conector só obtém e entrega.** Em todos os caminhos, o conector obtém o arquivo, converte para uma forma canônica (Latin-1, fim de linha `\n`, sem linhas vazias), calcula o hash e o `idCarga`, arquiva essa forma canônica no Blob (`b3/{AAAAMMDD}/cargas/{idCarga}/TaxaSwap.txt`), sem alterar o conteúdo das linhas, e publica um aviso de carga que aponta para ele. Não interpreta o conteúdo, não calcula fatores e nunca grava no banco. Saem do caminho de publicação o parser em Node, `normalizeCurveType`, `shouldPublishB3Curve` e `curveB3Factors`.
- **Uma mensagem por carga no tópico que já existe**, `tp-event-b3-curve`, no lugar de uma por vértice: `idCarga`, data-base, caminho e SHA-256 do arquivo e a origem. A chave é a data (`B3-TS-{AAAAMMDD}`), para que as cargas de uma data sejam processadas em ordem. Nenhum tópico novo é criado.
- **`idCarga` determinístico** a partir da data de geração e do hash do arquivo: o mesmo arquivo gera sempre o mesmo `idCarga`, por qualquer caminho, e um arquivo novo da B3 para a mesma data gera outro.
- **Três caminhos equivalentes**, sem nenhum tratado como exceção:
  - download do `.ex_` (`b3TaxaSwapDownloadHttpTrigger`, rota `b3/taxa-swap/download`, renomeados; o termo "contingência" sai de nomes, rotas e logs), o único download da B3, que passa a publicar sozinho e a gravar na pasta da data do arquivo;
  - reprocessamento do `TaxaSwap.txt` já gravado no Blob (`b3TaxaSwapReprocessamentoHttpTrigger`, hoje `b3HttpTrigger`, rota `b3/taxa-swap/reprocessamento`): o da data informada pelo front ou pelo orquestrador ou, sem data, o arquivo colocado em `recebidos/TaxaSwap.txt`; é o caminho para forçar o processamento e recuperar falhas;
  - **upload do arquivo por um usuário** (`POST /api/b3/taxa-swap/upload`, `.txt` ou `.ex_`).
- **Processor interpreta e grava, num lugar só, em Java.**
  - Lê o arquivo bruto e confere o hash.
  - Interpreta pelo leiaute oficial, com código exato e valor em `BigDecimal` direto do texto.
  - Valida: linha ilegível rejeita o arquivo; campo inválido rejeita só aquele código.
  - Grava os vértices em `tBtrsCurvaPrimr` sob as curvas de mercado ligadas a cada código em `tCurvaPrvdr`, numa única transação.
  - Depois do commit, avisa o engine (`POST /api/v1/cargas`).
  - O caminho antigo (`B3KafkaConsumer` → `mkt.B3CurveRaw`) é substituído.
- **Falha definitiva sem tópico de falhas:** o processor registra `CARGA_FALHOU` (log de erro e métrica, para alerta) e segue; a recuperação é reprocessar a data pelo `b3/taxa-swap/reprocessamento`.
- **BREAKING:** o formato da mensagem em `tp-event-b3-curve` muda; conector e processor mudam juntos.
- As rotas do conector deixam de ser anônimas.

## Capabilities

### New Capabilities
- `b3-taxaswap-publicacao` (conector): obtenção do arquivo pelos três caminhos (download do `.ex_`, leitura do Blob pela data e upload), forma canônica, identidade da carga, arquivamento no Blob e aviso de carga no Kafka.
- `b3-carga-processor` (processor): consumo do aviso, leitura e conferência do arquivo bruto, parse pelo leiaute oficial, validação, gravação transacional em `tBtrsCurvaPrimr` pelo mapeamento de `tCurvaPrvdr`, aviso ao engine, idempotência, repetição e registro de falha definitiva.

### Modified Capabilities
<!-- Nenhuma: não há specs arquivadas dos pipelines do conector. -->

## Impact

- **services/conector:**
  - o gatilho do `.ex_` (renomeado de `b3ContingencyHttpTrigger` para `b3TaxaSwapDownloadHttpTrigger`, rota `b3/taxa-swap/download`) passa a arquivar e publicar o aviso;
  - o `b3HttpTrigger` (hoje rota `swap-process`), renomeado para `b3TaxaSwapReprocessamentoHttpTrigger` (rota `b3/taxa-swap/reprocessamento`), passa a ler do Blob `b3/{AAAAMMDD}/TaxaSwap.txt` (com data) ou `recebidos/TaxaSwap.txt` (sem data); `B3_SWAP_URL` deixa de existir;
  - rota nova de upload;
  - o parser, o catálogo de tipos e o cálculo de fatores saem do caminho de publicação.
- **services/processor:** consumo do aviso em `tp-event-b3-curve`, leitura do Blob, parser do leiaute B3 em Java, gravação em `tBtrsCurvaPrimr`, cliente HTTP do engine com token do Entra ID; substituição de `B3KafkaConsumer`, `B3CurveRaw`, `B3CurveRawEntity` e do repositório e adaptador de `mkt.B3CurveRaw`.
- **Kafka:** sem tópico novo; muda o formato da mensagem em `tp-event-b3-curve`.
- **Blob:** pasta `b3/{AAAAMMDD}/cargas/{idCarga}/`, escrita pelo conector e lida pelo processor.
- **Banco:** sem mudança de schema; o processor lê `tCurvaPrvdr` e grava `tBtrsCurvaPrimr` sob o nome da curva de mercado; a geração de `cldtfdUnic` no banco real está em aberto (ver design).
- **Engine:** o aviso segue o contrato da spec `curve-load-trigger` do change `engine-modelos-curva`.
- **Orquestrador:** é quem dispara o download e, quando preciso, o `b3/taxa-swap/reprocessamento` de uma data (horário, novas tentativas e alerta de carga não recebida); configurar essas tarefas é do change do orquestrador.

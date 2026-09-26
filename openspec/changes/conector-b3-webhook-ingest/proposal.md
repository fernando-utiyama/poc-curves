## Why

O `TaxaSwap.txt` da B3 chega ao projeto por dois caminhos de download no `services/conector`, e nenhum entrega o que o engine precisa:
- **Download do `TaxaSwap.txt`** (`b3HttpTrigger`, rota `swap-process`): baixa da B3, interpreta o arquivo em Node e publica no Kafka uma mensagem por vértice. Classifica a curva pela descrição (`normalizeCurveType`), o que publica DCL e DPL como DOL e descarta PTX e INP. Manda o valor como número de ponto flutuante, com fatores que o engine não usa. O processor recebe vértice a vértice, sem saber quando a carga termina, e grava por (ticker, data), o que deixa uma linha por curva em vez de 278.
- **Download do arquivo compactado `TSaammdd.ex_`** (hoje `b3ContingencyHttpTrigger`, rota `swap-contingency`): baixa o arquivo, extrai o texto e o salva no Blob em `b3/{AAAAMMDD}/TaxaSwap.txt`, e para aí. Grava na pasta da data do download, que pode não ser a data do arquivo. A versão anterior deste change propunha o conector gravando direto no banco, o que criaria um segundo gravador do mesmo dado, fora do processor e sem avisar o engine.

Os dois downloads são caminhos equivalentes de obter o mesmo arquivo; o do `.ex_` pode até virar o principal. Também não há como um usuário enviar o arquivo.

## What Changes

- **Conector só obtém e entrega.** Em todos os caminhos, o conector obtém o arquivo, converte para uma forma canônica (Latin-1, fim de linha `\n`, sem linhas vazias), calcula o hash e o `idCarga`, arquiva essa forma canônica no Blob (`b3/{AAAAMMDD}/cargas/{idCarga}/TaxaSwap.txt`), sem alterar o conteúdo das linhas, e publica um aviso de carga que aponta para ele. Não interpreta o conteúdo, não calcula fatores e nunca grava no banco. Saem do caminho de publicação o parser em Node, `normalizeCurveType`, `shouldPublishB3Curve` e `curveB3Factors`.
- **Uma mensagem por carga no tópico que já existe**, `tp-event-b3-curve`, no lugar de uma por vértice: `idCarga`, data-base, caminho e SHA-256 do arquivo e a origem. A chave é a data (`B3-TS-{AAAAMMDD}`), para que as cargas de uma data sejam processadas em ordem. Nenhum tópico novo é criado.
- **`idCarga` determinístico** a partir da data de geração e do hash do arquivo: o mesmo arquivo gera sempre o mesmo `idCarga`, por qualquer caminho, e um arquivo republicado gera outro.
- **Quatro caminhos equivalentes**, sem nenhum tratado como exceção:
  - download do `TaxaSwap.txt` (`b3HttpTrigger`, `swap-process`);
  - download do `.ex_` (`b3SwapExHttpTrigger`, rota `swap-ex`, renomeados; o termo "contingência" sai de nomes, rotas e logs), que passa a publicar sozinho e a gravar na pasta da data do arquivo;
  - **upload do arquivo por um usuário** (`POST /api/b3/taxaswap/upload`, `.txt` ou `.ex_`);
  - republicação de uma data a partir do Blob (`POST /api/b3/taxaswap/publicacao`), que é o caminho de recuperação.
- **Processor interpreta e grava, num lugar só, em Java.**
  - Lê o arquivo bruto e confere o hash.
  - Interpreta pelo leiaute oficial, com código exato e valor em `BigDecimal` direto do texto.
  - Valida: linha ilegível rejeita o arquivo; campo inválido rejeita só aquele código.
  - Grava os vértices em `tBtrsCurvaPrimr` sob as curvas de mercado ligadas a cada código em `tCurvaPrvdr`, numa única transação.
  - Depois do commit, avisa o engine (`POST /api/v1/cargas`).
  - O caminho antigo (`B3KafkaConsumer` → `mkt.B3CurveRaw`) é substituído.
- **Falha definitiva sem tópico de falhas:** o processor registra `CARGA_FALHOU` (log de erro e métrica, para alerta) e segue; a recuperação é republicar a data.
- **BREAKING:** o formato da mensagem em `tp-event-b3-curve` muda; conector e processor mudam juntos.
- As rotas do conector deixam de ser anônimas.

## Capabilities

### New Capabilities
- `b3-taxaswap-publicacao` (conector): obtenção do arquivo pelos quatro caminhos (download do `.txt`, download do `.ex_`, upload e republicação), forma canônica, identidade da carga, arquivamento no Blob e aviso de carga no Kafka.
- `b3-carga-processor` (processor): consumo do aviso, leitura e conferência do arquivo bruto, parse pelo leiaute oficial, validação, gravação transacional em `tBtrsCurvaPrimr` pelo mapeamento de `tCurvaPrvdr`, aviso ao engine, idempotência, repetição e registro de falha definitiva.

### Modified Capabilities
<!-- Nenhuma: não há specs arquivadas dos pipelines do conector. -->

## Impact

- **services/conector:**
  - `b3HttpTrigger` e o gatilho do `.ex_` (renomeado de `b3ContingencyHttpTrigger` para `b3SwapExHttpTrigger`, rota `swap-ex`) passam a arquivar e publicar o aviso;
  - rotas novas de upload e de republicação;
  - o parser, o catálogo de tipos e o cálculo de fatores saem do caminho de publicação.
- **services/processor:** consumo do aviso em `tp-event-b3-curve`, leitura do Blob, parser do leiaute B3 em Java, gravação em `tBtrsCurvaPrimr`, cliente HTTP do engine com token do Entra ID; substituição de `B3KafkaConsumer`, `B3CurveRaw`, `B3CurveRawEntity` e do repositório e adaptador de `mkt.B3CurveRaw`.
- **Kafka:** sem tópico novo; muda o formato da mensagem em `tp-event-b3-curve`.
- **Blob:** pasta `b3/{AAAAMMDD}/cargas/{idCarga}/`, escrita pelo conector e lida pelo processor.
- **Banco:** sem mudança de schema; o processor lê `tCurvaPrvdr` e grava `tBtrsCurvaPrimr` sob o nome da curva de mercado, com a sequência `seq_tbtrscurvaprimr_cidtfdunic` (V23).
- **Engine:** o aviso segue o contrato da spec `curve-load-trigger` do change `engine-modelos-curva`.
- **Orquestrador:** é quem dispara os downloads (qual rota, horário, novas tentativas e alerta de carga não recebida); configurar essas tarefas é do change do orquestrador.

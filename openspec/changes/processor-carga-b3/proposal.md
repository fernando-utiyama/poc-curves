## Why

Hoje o `services/processor` recebe do conector uma mensagem por vértice do `TaxaSwap.txt`, sem saber quando a carga termina, e grava por (ticker, data) em `mkt.B3CurveRaw`, o que deixa uma linha por curva em vez de 278. O valor chega como número de ponto flutuante, com fatores que o engine não usa, e a curva já vem classificada pela descrição (DCL e DPL como DOL, PTX e INP descartados). Nada disso chega a `tBtrsCurvaPrimr`, que é o que o engine lê.

O conector passa a só obter o arquivo e entregar um aviso por carga (change `conector-b3-webhook-ingest`). Este change é o outro lado: o processor passa a ser o único ponto que interpreta, valida e grava o `TaxaSwap.txt`, e o único que avisa o engine de que a carga está completa.

## What Changes

- **Processor interpreta e grava, num lugar só, em Java.**
  - Consome o aviso de carga em `tp-event-b3-curve`, com confirmação manual e uma mensagem por vez.
  - Lê o arquivo bruto arquivado no Blob e confere o tamanho e o SHA-256.
  - Interpreta pelo leiaute oficial, com código exato e valor em `BigDecimal` direto do texto.
  - Valida: linha ilegível rejeita o arquivo; campo inválido rejeita só aquele código.
  - Grava os vértices em `tBtrsCurvaPrimr` sob as curvas de mercado ligadas a cada código em `tCurvaPrvdr`, numa única transação, sob a trava da curva em `tCurvaMercd`.
  - Depois do commit, avisa o engine (`POST /api/v1/cargas`), sem autenticação.
  - O caminho antigo (`B3KafkaConsumer` → `mkt.B3CurveRaw`) é substituído.
- **Repetição por janela de tempo e falha definitiva sem tópico de falhas:** o processor repete leitura, gravação e aviso por janelas (5, 5 e 10 minutos), emite `AVISO_ATRASADO` aos 2 minutos sem aviso aceito e, esgotada a janela, registra `CARGA_FALHOU` (log de erro e métrica, para alerta) e segue; a recuperação é reprocessar a data pelo `b3/taxa-swap/reprocessamento` do conector.
- **BREAKING:** o formato da mensagem em `tp-event-b3-curve` muda (aviso por carga, definido pela spec `b3-taxaswap-publicacao` do change `conector-b3-webhook-ingest`); mensagens no formato antigo são rejeitadas como falha definitiva. Conector e processor mudam e são implantados juntos.

## Capabilities

### New Capabilities
- `b3-carga-processor` (processor): consumo do aviso, leitura e conferência do arquivo bruto, parse pelo leiaute oficial, validação, gravação transacional em `tBtrsCurvaPrimr` pelo mapeamento de `tCurvaPrvdr`, aviso ao engine, idempotência, repetição e registro de falha definitiva.

### Modified Capabilities
<!-- Nenhuma: não há specs arquivadas dos pipelines do processor. -->

## Impact

- **services/processor:** consumo do aviso em `tp-event-b3-curve`, leitura do Blob, parser do leiaute B3 em Java, gravação em `tBtrsCurvaPrimr`, cliente HTTP do engine, sem autenticação; substituição de `B3KafkaConsumer`, `B3CurveRaw`, `B3CurveRawEntity` e do repositório e adaptador de `mkt.B3CurveRaw`.
- **Kafka:** sem tópico novo; o processor passa a consumir o formato novo da mensagem em `tp-event-b3-curve`.
- **Blob:** lê a pasta `b3/{AAAAMMDD}/cargas/{idCarga}/`, escrita pelo conector.
- **Banco:** sem mudança de schema; o processor lê `tCurvaPrvdr` e grava `tBtrsCurvaPrimr` sob o nome da curva de mercado; o `cIdtfdUnic` é `MAX + 1` lido com `UPDLOCK, HOLDLOCK` na mesma transação (a coluna não tem identity), e a gravação trava a linha da curva em `tCurvaMercd`, a mesma trava da edição manual no curves e da construção no engine.
- **Engine:** o aviso segue o contrato da spec `curve-load-trigger` do change `engine-construcao-curvas`.
- **Conector:** change `conector-b3-webhook-ingest`, par deste: publica o aviso que o processor consome e é o caminho de recuperação (`b3/taxa-swap/reprocessamento`).

## Why

O `services/conector` tem o pipeline de contingência (`b3ContingencyHttpTrigger`) que baixa o `TaxaSwap.txt` da B3 e salva no Blob Storage — mas para aí. Falta a continuação desse mesmo fluxo: uma vez que o arquivo está no Blob, alguém precisa avisar o conector disso, ele precisa buscar o arquivo de volta, fazer o parse conforme o layout posicional da B3 e persistir os vértices brutos no banco. Hoje essa persistência simplesmente não existe — o pipeline de contingência termina no upload para o Blob e não vai além disso.

## What Changes

- Novo endpoint HTTP webhook que recebe o aviso de que o `TaxaSwap.txt` de uma data de referência X está disponível no Blob Storage (caminho `b3/{data}/TaxaSwap.txt` — o mesmo caminho já usado pelo `b3ContingencyHttpTrigger` ao salvar) e dispara o processamento daquela data: a continuação do pipeline de contingência.
- Novo endpoint HTTP de reprocessamento manual, independente do webhook, que roda o mesmo processamento para uma data informada por parâmetro.
- Novo serviço de download do Blob Storage (o conector hoje só tem `uploadSwapText`, usado pelo `b3ContingencyHttpTrigger` para salvar; falta o `download`/`get` equivalente, que é o lado de leitura desse mesmo par).
- Reaproveitamento do parser posicional de B3 já existente (`parseB3Line`/`processLines`) e da normalização de tipo de curva (`normalizeCurveType`) para transformar o texto bruto em registros tipados — sem duplicar essa lógica.
- Nova camada de persistência: primeira escrita em banco do conector, direcionada à tabela legada `tBtrsCurvaPrimr` (V22/V25, `db/h2/schema.sql`) — vértices brutos das curvas TS de B3.
- Conexão com o banco configurada genericamente por variáveis de ambiente, seguindo o mesmo padrão já usado para Blob/Kafka no conector (`B3_BLOB_CONNECTION_STRING`, `KAFKA_BROKERS` etc.) — pensada para subir localmente, sem nenhuma credencial real documentada nesta change.
- **Gap explícito de infraestrutura**: `services/conector/package.json` está vazio hoje — não existe nenhum driver de banco configurado no projeto. Esta change precisa escolher e introduzir um.
- **Requisito de qualidade**: código novo desta change sai com ≥ 90% de cobertura de teste (linhas/statements/funções/branches), verificado via `jest --coverage`.
- Nenhuma mudança no pipeline Kafka existente (`b3HttpTrigger`) — capacidade aditiva sobre o pipeline de contingência.

## Capabilities

### New Capabilities
- `b3-taxaswap-ingest`: continuação do pipeline de contingência de B3 — leitura do `TaxaSwap.txt` já disponível no Blob Storage (colocado lá pelo `b3ContingencyHttpTrigger`) e persistência no banco de dados legado, disparada por webhook ou por reprocessamento manual via endpoint.

### Modified Capabilities
(nenhuma — não há specs existentes para os pipelines atuais de B3 do conector; esta change não altera comportamento já especificado)

## Impact

- **Código**: `services/conector/src/functions/b3/` (2 novos HTTP triggers), `services/conector/src/services/b3/` (novo serviço de download do Blob — par do `uploadSwapText` já usado pela contingência —, novo serviço de persistência), possivelmente novo módulo de mapeamento ticker → `cTickerIndcd`/catálogo `tCurvaMercd`.
- **Dependências**: novo driver de banco de dados no `package.json` do conector (hoje inexistente) — decisão de qual driver fica para `design.md`.
- **Banco**: escreve em `tBtrsCurvaPrimr` (schema `db/h2/schema.sql`); depende de `cTickerIndcd` já existir em `tCurvaMercd` (FK obrigatória) — resolução/erro de catálogo ausente é uma decisão de design. Conexão via variáveis de ambiente genéricas, sem credenciais reais nesta change.
- **Sem impacto** no pipeline Kafka (`b3HttpTrigger`) já existente. O pipeline de contingência (`b3ContingencyHttpTrigger`) não é alterado no código — esta change apenas continua o fluxo que ele começa.

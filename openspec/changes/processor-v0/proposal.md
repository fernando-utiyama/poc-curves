## Why

As curvas do primeiro objetivo precisam de insumo diário, e o caminho definitivo ainda não existe: as functions de download (B3, ANBIMA e Bloomberg) e o consumo do aviso de carga pelo processor (change `processor-carga-b3`) dependem de changes que não estão no ar. Sem dados brutos em `tBtrsCurvaPrimr`, `tAnbmaCurvaPrimr` e `tBbergCurvaPrimr`, o engine não constrói nada.

Esta é a **versão 0**: o próprio `services/processor` busca os três arquivos, arquiva o original no Blob para auditoria e grava os dados no banco, chamado pelo orquestrador com as mesmas respostas que as functions teriam. Quando as functions existirem, o orquestrador passa a apontar para elas trocando o destino e os caminhos das tarefas, sem mudar código, e esta versão sai.

## What Changes

- **Processor ganha uma rota de download e uma de reprocessamento, com a fonte no caminho** (`/api/v1/cargas/{fonte}/download` e `.../reprocessamento`, `{fonte}` = `b3`, `anbima` ou `bloomberg`), com as respostas que o orquestrador já espera das functions (200 com `dataBase` e `idCarga`; 503 quando o arquivo do dia ainda não saiu):
  - B3, por download do site: `TS{AAMMDD}.ex_` da B3 → `TaxaSwap.txt` → `tBtrsCurvaPrimr`;
  - ANBIMA, por download do site: `ms{AAMMDD}.txt` (mercado secundário, só NTN-B inteira) → `tAnbmaCurvaPrimr`, com o prazo em dias corridos até o vencimento (sem calendário no processor);
  - Bloomberg, pela API do Data License: pedido de histórico (`HistoryRequest`) com os tickers da SOFR → `tBbergCurvaPrimr`.
- **Upload pelo front**, pelo bff até o processor: o operador envia o arquivo da fonte (`TaxaSwap.txt` ou `.ex_`, `ms{AAMMDD}.txt`, ou o arquivo de resposta do Data License), e ele segue o mesmo caminho do download (`origem` = `UPLOAD`, com o usuário). Serve para quando a fonte está fora do ar ou o download falhou.
- **Original no Blob para auditoria**, imutável, por carga: `{fonte}/{AAAAMMDD}/cargas/{idCarga}/{arquivo}`. O reprocessamento relê esse original sem baixar de novo.
- **Gravação numa transação por carga**, sob a trava da curva em `tCurvaMercd`, pelo mapeamento de `tCurvaPrvdr`, com as mesmas regras da change `processor-carga-b3`.
- **Aviso ao engine** (`POST /api/v1/cargas`) depois do commit, sem segurar a resposta ao orquestrador.
- **Lista de tickers da SOFR cravada como reserva**: usada só quando a chamada não traz `tickers`. Fica numa única classe, marcada para ser retirada no futuro.
- **Sem infraestrutura nova:** nenhum tópico, fila, tabela ou mudança de schema. O consumo Kafka atual do processor não é alterado.

## Capabilities

### New Capabilities
- `carga-arquivos-processor` (processor): rotas de download, upload e reprocessamento das três fontes, obtenção do arquivo, arquivamento do original no Blob, parse e validação, gravação transacional nas tabelas brutas, aviso ao engine e respostas no contrato do orquestrador.
- `upload-carga-bff` (bff e front): tela de upload em pt-BR e rota autenticada do bff que repassa o arquivo ao processor com o usuário.

### Modified Capabilities
<!-- Nenhuma: não há specs arquivadas dos pipelines do processor. -->

## Impact

- **services/processor:** rotas REST novas sem autenticação, clientes HTTP da B3, da ANBIMA e do Bloomberg Data License, escrita e leitura no Blob, parsers em Java, gravação JDBC nas três tabelas brutas, cliente do webhook do engine. Sem calendário: o prazo da NTN-B é gravado em dias corridos. Os consumidores Kafka existentes ficam como estão.
- **bff:** rota autenticada de upload (multipart), que repassa ao processor com `X-Usuario` e `X-Correlation-Id`; só o bff autentica.
- **Front:** tela "Carga manual de arquivo", em pt-BR, com a fonte, o arquivo e o resultado da carga.
- **Orquestrador (`orquestrador-v0-disparo-manual`):** sem código novo além da v0. As três tarefas apontam o `destino` para o processor (`orquestrador.http.destinos`) e usam os caminhos `/api/v1/cargas/{fonte}/...` (guia da v0, seção 12).
- **Blob:** escreve e lê `b3/`, `anbima/` e `bloomberg/`, por Managed Identity. Só originais, como a regra do projeto.
- **Banco:** sem mudança de schema. Lê `tCurvaPrvdr`, trava `tCurvaMercd`, grava `tBtrsCurvaPrimr`, `tAnbmaCurvaPrimr` e `tBbergCurvaPrimr`. Na Bloomberg grava o ticker completo em `cTickerBberg`, coluna já `VARCHAR(50)` no `001_SCRIPT_INICIAL.sql`.
- **Bloomberg Data License:** credencial (id e segredo) no Key Vault; identificador do catálogo da conta por configuração.
- **Engine:** recebe o aviso no contrato da spec `curve-load-trigger` (change `engine-construcao-curvas`).
- **Changes relacionadas:** `processor-carga-b3` continua sendo o destino final do B3 (as regras de leiaute, validação e gravação são as mesmas, para o código ser reaproveitado); `conector-b3-webhook-ingest` e as futuras functions ANBIMA e Bloomberg substituem as rotas de download desta versão.

## Purpose

No `services/conector`, obter o `TaxaSwap.txt` (pelo download do `.ex_` da B3, pela leitura do arquivo já gravado no Blob ou pelo upload), arquivá-lo no Blob e publicar no Kafka um aviso de carga que aponta para o arquivo. Os caminhos são equivalentes; nenhum é tratado como exceção. O conector não interpreta o conteúdo: parse, validação e gravação são do processor (spec `b3-carga-processor` do change `processor-carga-b3`).

## ADDED Requirements

### Requirement: Caminhos de obtenção do arquivo
O conector SHALL ter três formas de obter o arquivo, todas terminando no mesmo passo de arquivamento e publicação:

| Caminho | Gatilho | Rota | `origem` |
|---|---|---|---|
| Download do arquivo compactado `TS{AAMMDD}.ex_` da B3 (`B3_SWAP_EX_URL`) e extração do texto: o **único download** da B3, que não publica o `.txt` por URL direta | `b3TaxaSwapDownloadHttpTrigger` (hoje `b3ContingencyHttpTrigger`, renomeado) | `b3/taxa-swap/download` (hoje `swap-contingency`) | `DOWNLOAD` |
| Reprocessamento do `TaxaSwap.txt` já gravado no Blob: o da data-base informada ou, sem data, o da pasta `recebidos/` | `b3TaxaSwapReprocessamentoHttpTrigger` (hoje `b3HttpTrigger`, renomeado) | `b3/taxa-swap/reprocessamento` | `REPROCESSAMENTO` |
| Upload do arquivo por um usuário | `b3TaxaSwapUploadHttpTrigger` | `b3/taxa-swap/upload` | `UPLOAD` |

Nomes de gatilhos, handlers, rotas, métodos, variáveis e logs MUST NOT usar o termo "contingência" (`contingency`). Os parâmetros atuais do download (`date`, `file`) SHALL ser mantidos.

#### Scenario: Caminhos equivalentes
- **WHEN** o arquivo de `2026-09-14` é obtido pelo download e, depois, pelo upload do mesmo `.ex_`
- **THEN** os dois produzem o mesmo arquivo arquivado, o mesmo `idCarga` e mensagens iguais, exceto por `origem` e `geradoEm`

### Requirement: Forma canônica do arquivo
Antes de qualquer outro passo, o texto obtido SHALL ser convertido na forma canônica:
1. separar as linhas por `\r\n`, `\n` ou `\r`;
2. remover as linhas vazias ou só com espaços;
3. manter cada linha restante exatamente como está, sem cortar espaços;
4. juntar as linhas com `\n` e terminar com um `\n`;
5. codificar em Latin-1 (ISO-8859-1).

Um caractere fora do Latin-1 MUST interromper a execução sem arquivar nem publicar. O hash, o arquivamento e a cópia de trabalho SHALL usar os bytes da forma canônica. É isso que faz o mesmo conteúdo gerar o mesmo `idCarga` por qualquer caminho, independentemente de fim de linha ou de codificação de origem.

#### Scenario: Fins de linha diferentes
- **WHEN** o upload entrega um `.txt` com `\r\n`, e o download entrega o mesmo conteúdo com `\n`
- **THEN** as duas formas canônicas são idênticas byte a byte, e o `idCarga` é o mesmo

### Requirement: Identidade da carga
Sobre a forma canônica, o conector SHALL calcular:
- `hashArquivo`: SHA-256 em hexadecimal minúsculo (64 caracteres);
- `dataBase`: as posições 12–19 (`AAAAMMDD`) da primeira linha, que no leiaute oficial são a data de geração do arquivo, validadas como data de calendário;
- `idCarga`: `B3-TS-{AAAAMMDD}-{12 primeiros caracteres de hashArquivo}`.

Esta é a única leitura de conteúdo feita pelo conector. Arquivo sem linhas, ou primeira linha com menos de 19 caracteres ou sem data válida nas posições 12–19, MUST interromper a execução sem arquivar nem publicar.

#### Scenario: Data de geração inválida
- **WHEN** a primeira linha tem `20260231` nas posições 12–19
- **THEN** a execução é interrompida com erro informando a data, e nada é arquivado nem publicado

### Requirement: Arquivamento e cópia de trabalho
Antes de publicar, o conector SHALL gravar a forma canônica no container `B3_BLOB_CONTAINER`, nos dois lugares:
- `b3/{AAAAMMDD}/cargas/{idCarga}/TaxaSwap.txt`: cópia imutável, com escrita condicional `If-None-Match: *`; se já existir, a gravação é tratada como sucesso, sem sobrescrever;
- `b3/{AAAAMMDD}/TaxaSwap.txt`: cópia de trabalho da data, sobrescrita pelo download, pelo upload e pelo reprocessamento sem data (a partir de `recebidos/`), que é a que o reprocessamento com data lê (e não regrava).

`{AAAAMMDD}` SHALL ser sempre a data de geração do arquivo, nunca a data em que o download foi feito. Falha do Blob MUST interromper a execução antes da publicação.

#### Scenario: Arquivo de dia anterior obtido pelo `.ex_`
- **WHEN** o download é chamado em `2026-09-15` e, pela busca de dias anteriores, obtém o arquivo com data de geração `20260914`
- **THEN** as duas cópias são gravadas em `b3/20260914/`, e a mensagem sai com `dataBase` = `2026-09-14`

### Requirement: Aviso de carga no tópico existente
O conector SHALL publicar uma única mensagem por carga no tópico já existente `KAFKA_TOPIC` (`tp-event-b3-curve` (no poc; no real, o tópico configurado em `spring.kafka.topics.b3.name`)), substituindo o formato atual de uma mensagem por vértice. A mensagem SHALL ter:
- chave `B3-TS-{AAAAMMDD}` (texto UTF-8), para que todas as cargas de uma data caiam na mesma partição e sejam processadas em ordem;
- cabeçalho `X-Correlation-Id` com o identificador de correlação da execução (o recebido na requisição ou um UUID gerado);
- valor em JSON UTF-8, com todos os campos obrigatórios:

```json
{
  "idCarga": "B3-TS-20260914-1a2b3c4d5e6f",
  "fonte": "B3",
  "produto": "TS",
  "dataBase": "2026-09-14",
  "arquivo": { "caminho": "b3/20260914/cargas/B3-TS-20260914-1a2b3c4d5e6f/TaxaSwap.txt", "sha256": "<64 caracteres hexadecimais>", "bytes": 2159380 },
  "origem": "DOWNLOAD",
  "usuario": null,
  "geradoEm": "2026-09-14T20:15:00.000-03:00"
}
```

`usuario` SHALL ser o valor do cabeçalho `X-Usuario` da requisição (o usuário que o bff autenticou) quando `origem` = `UPLOAD`, e nulo nos demais casos ou se o cabeçalho não vier. O produtor SHALL ser idempotente, com `acks=all` e tempo limite de envio de 30 segundos. `geradoEm` SHALL estar no horário de Brasília, com o deslocamento. O conector MUST NOT publicar vértices, fatores nem tipos de curva.

#### Scenario: Duas cargas da mesma data
- **WHEN** duas cargas diferentes da mesma data são publicadas em seguida
- **THEN** as duas mensagens têm a chave `B3-TS-20260914`, caem na mesma partição e chegam ao processor na ordem em que foram publicadas

### Requirement: Upload do arquivo pelo usuário
`POST /api/b3/taxa-swap/upload` SHALL receber um arquivo em `multipart/form-data`, no campo `arquivo`, com no máximo 20 MB, e o parâmetro opcional `dataBase` (`AAAA-MM-DD`). O arquivo SHALL ser tratado pela extensão do nome enviado:
- `.txt`: o próprio `TaxaSwap.txt`, com os bytes lidos como Latin-1 (a codificação da B3);
- `.ex_`: o arquivo compactado da B3, do qual o texto é extraído como no download.

Outra extensão, arquivo vazio ou acima do limite MUST resultar em 400. Em seguida, o arquivo SHALL passar pela forma canônica, pela identidade da carga, pelo arquivamento (as duas cópias) e pela publicação, com `origem` = `UPLOAD` e o usuário na mensagem. Se `dataBase` for informado e diferente da data de geração do arquivo, a resposta MUST ser 422, sem arquivar nem publicar.

#### Scenario: Upload de um arquivo corrigido
- **WHEN** um operador envia pelo upload o `TaxaSwap.txt` de `2026-09-14` com `dataBase` = `2026-09-14`
- **THEN** o arquivo é arquivado em `b3/20260914/`, a mensagem sai com `origem` = `UPLOAD` e o usuário, e a resposta traz o `idCarga`

#### Scenario: Upload do arquivo de outra data
- **WHEN** o operador envia o arquivo de `20260914` informando `dataBase` = `2026-09-15`
- **THEN** a resposta é 422 informando as duas datas, e nada é arquivado nem publicado

### Requirement: Reprocessamento de um arquivo já gravado no Blob
`b3/taxa-swap/reprocessamento` (`b3TaxaSwapReprocessamentoHttpTrigger`, hoje `b3HttpTrigger`) SHALL aceitar o parâmetro opcional `dataBase` (`AAAA-MM-DD`), informado pelo front ou pelo orquestrador:
- **com `dataBase`:** SHALL ler `b3/{AAAAMMDD}/TaxaSwap.txt`, gravado antes pelo download do `.ex_`, pelo upload ou por um reprocessamento sem data;
- **sem `dataBase`:** SHALL ler `recebidos/TaxaSwap.txt`, um arquivo colocado diretamente na pasta `recebidos/`, na raiz do container `B3_BLOB_CONTAINER`. A data-base é a data de geração do próprio arquivo, e a cópia de trabalho `b3/{AAAAMMDD}/TaxaSwap.txt` SHALL ser gravada, como no download e no upload. O arquivo em `recebidos/` MUST NOT ser alterado nem apagado pela rota.

A pasta `recebidos/` SHALL existir sempre: na subida da function e a cada chamada sem `dataBase`, se não houver nenhum arquivo com o prefixo `recebidos/`, o conector SHALL criá-la gravando o arquivo vazio `recebidos/.keep` (no Blob, uma pasta só existe se tiver algum arquivo). A criação é idempotente e não falha se outra instância criar ao mesmo tempo.

Nos dois casos, o conteúdo SHALL passar pela forma canônica, pela identidade da carga, pelo arquivamento da cópia imutável e pela publicação, com `origem` = `REPROCESSAMENTO`. A rota não baixa nada da B3: `B3_SWAP_URL` deixa de existir. É o caminho para forçar o processamento de uma data, inclusive para recuperar uma falha do processor ou gravar uma curva ligada depois da carga.

#### Scenario: Forçar o processamento de uma data
- **WHEN** o front chama `b3/taxa-swap/reprocessamento` com `dataBase` = `2026-09-14`, e `b3/20260914/TaxaSwap.txt` existe
- **THEN** a carga é publicada com `origem` = `REPROCESSAMENTO` e o mesmo `idCarga` do download que gravou o arquivo, e a resposta traz o `idCarga`

#### Scenario: Arquivo de outra data
- **WHEN** `b3/taxa-swap/reprocessamento` é chamado com `dataBase` = `2026-09-15`, e o arquivo em `b3/20260915/` tem data de geração `20260914`
- **THEN** a resposta é 422 com `DATA_BASE_DIVERGENTE`, informando as duas datas, e nada é publicado

#### Scenario: Arquivo colocado na pasta recebidos
- **WHEN** o arquivo de `2026-09-14` é colocado em `recebidos/TaxaSwap.txt`, e a rota é chamada sem `dataBase`
- **THEN** a data-base `2026-09-14` vem do próprio arquivo, a cópia de trabalho é gravada em `b3/20260914/TaxaSwap.txt`, a cópia imutável é arquivada, a carga é publicada com `origem` = `REPROCESSAMENTO`, e `recebidos/TaxaSwap.txt` continua lá, sem alteração

#### Scenario: Primeira execução sem a pasta
- **WHEN** a function sobe pela primeira vez, e o container não tem a pasta `recebidos/`
- **THEN** o conector grava `recebidos/.keep`, e a pasta aparece no Blob para receber o `TaxaSwap.txt`; uma chamada sem `dataBase` nesse momento responde 404 com `ARQUIVO_NAO_ENCONTRADO`, informando `recebidos/TaxaSwap.txt`

#### Scenario: Sem arquivo no Blob
- **WHEN** `b3/taxa-swap/reprocessamento` é chamado para uma data sem `b3/{AAAAMMDD}/TaxaSwap.txt`, ou sem data e sem `recebidos/TaxaSwap.txt`
- **THEN** a resposta é 404 com `ARQUIVO_NAO_ENCONTRADO`, informando o caminho

### Requirement: Respostas das rotas
As três rotas SHALL responder:
- 200 com `{ "idCarga", "dataBase", "hashArquivo", "origem", "correlationId" }`;
- 400 para parâmetro ou corpo inválido;
- 404 no `b3/taxa-swap/reprocessamento`, se o arquivo a ler (`b3/{AAAAMMDD}/TaxaSwap.txt` ou `recebidos/TaxaSwap.txt`) não existir, informando o caminho;
- 422 para arquivo sem linhas, sem data de geração válida ou com caractere fora do Latin-1, e, no `b3/taxa-swap/reprocessamento` e no upload, para data de geração diferente da `dataBase` pedida;
- 502 se o download do `.ex_` na B3 falhar;
- 503 se o Blob ou o Kafka estiverem indisponíveis.

Toda resposta SHALL trazer o cabeçalho `X-Correlation-Id`. Erros MUST NOT expor stack trace. Como o front chama estas rotas (upload e `b3/taxa-swap/reprocessamento`), elas SHALL seguir o mesmo contrato do engine e do `services/curves`: `origem` é um de `DOWNLOAD`, `REPROCESSAMENTO` e `UPLOAD`; `dataBase` em `AAAA-MM-DD`; e todo erro tem o corpo `{ "codigoErro", "mensagem", "correlationId", "detalhes": [ { "campo", "linha", "valor", "motivo" } ] }`, com a mensagem em pt-BR, com acentuação, e os códigos `PARAMETRO_INVALIDO` (400), `ARQUIVO_NAO_ENCONTRADO` (404), `ARQUIVO_INVALIDO` (422: sem linhas, sem data de geração válida ou fora do Latin-1), `DATA_BASE_DIVERGENTE` (422: data de geração diferente da pedida, com as duas datas em `detalhes`), `B3_INDISPONIVEL` (502) e `DEPENDENCIA_INDISPONIVEL` (503: Blob ou Kafka).

#### Scenario: B3 indisponível
- **WHEN** o download do `.ex_` falha na B3
- **THEN** a rota `b3/taxa-swap/download` responde 502, e nada é arquivado nem publicado

### Requirement: Rastreabilidade e acesso ao Blob
O conector não faz autenticação própria: quem expõe as rotas ao usuário (o bff e o `services/curves`) autentica, e o conector só recebe o `X-Usuario`. O acesso ao Blob SHALL usar Managed Identity (`B3_BLOB_ACCOUNT_URL`), com `B3_BLOB_CONNECTION_STRING` só no ambiente local. Cada execução SHALL registrar em log JSON, com `correlationId`, `idCarga` e horário de Brasília: origem, usuário, data-base, `hashArquivo`, tamanho em bytes, caminhos gravados, resultado e duração.

#### Scenario: Upload com usuário
- **WHEN** o bff envia o upload com o cabeçalho `X-Usuario` = `maria.silva`
- **THEN** a mensagem publicada e o log da execução trazem `usuario` = `maria.silva`

#### Scenario: Upload sem cabeçalho de usuário
- **WHEN** o upload chega sem `X-Usuario`
- **THEN** a carga é processada normalmente, com `usuario` nulo na mensagem e no log

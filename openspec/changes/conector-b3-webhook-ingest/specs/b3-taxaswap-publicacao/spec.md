## Purpose

No `services/conector`, obter o `TaxaSwap.txt` da B3 por qualquer um dos caminhos de download, arquivá-lo no Blob e publicar no Kafka um aviso de carga que aponta para o arquivo. Os caminhos são equivalentes; nenhum é tratado como exceção. O conector não interpreta o conteúdo: parse, validação e gravação são do processor (spec `b3-carga-processor`).

## ADDED Requirements

### Requirement: Caminhos de obtenção do arquivo
O conector SHALL ter quatro formas de obter o arquivo, todas terminando no mesmo passo de arquivamento e publicação:

| Caminho | Gatilho | Rota | `origem` |
|---|---|---|---|
| Download do `TaxaSwap.txt` (`B3_SWAP_URL`) | `b3HttpTrigger` | `swap-process` | `TXT` |
| Download do arquivo compactado `TSaammdd.ex_` (`B3_SWAP_EX_URL`) e extração do texto | `b3SwapExHttpTrigger` (hoje `b3ContingencyHttpTrigger`, renomeado) | `swap-ex` (hoje `swap-contingency`) | `EX` |
| Upload do arquivo por um usuário | `b3UploadHttpTrigger` | `b3/taxaswap/upload` | `UPLOAD` |
| Republicação de uma data a partir do Blob | `b3RepublicacaoHttpTrigger` | `b3/taxaswap/publicacao` | `REPUBLICACAO` |

Nomes de gatilhos, handlers, rotas, métodos, variáveis e logs MUST NOT usar o termo "contingência" (`contingency`). Os parâmetros atuais do caminho `EX` (`date`, `file`) SHALL ser mantidos.

#### Scenario: Caminhos equivalentes
- **WHEN** o arquivo de `2026-09-14` é obtido pelo caminho `TXT` e, depois, pelo caminho `EX`
- **THEN** os dois produzem o mesmo arquivo arquivado, o mesmo `idCarga` e mensagens iguais, exceto por `origem` e `geradoEm`

### Requirement: Forma canônica do arquivo
Antes de qualquer outro passo, o texto obtido SHALL ser convertido na forma canônica:
1. separar as linhas por `\r\n`, `\n` ou `\r`;
2. remover as linhas vazias ou só com espaços;
3. manter cada linha restante exatamente como está, sem cortar espaços;
4. juntar as linhas com `\n` e terminar com um `\n`;
5. codificar em Latin-1 (ISO-8859-1).

Um caractere fora do Latin-1 MUST interromper a execução sem arquivar nem publicar. O hash, o arquivamento e a cópia de trabalho SHALL usar os bytes da forma canônica. É isso que faz o mesmo conteúdo gerar o mesmo `idCarga` pelos caminhos `TXT` e `EX`, independentemente de fim de linha ou de codificação de origem.

#### Scenario: Fins de linha diferentes
- **WHEN** o caminho `TXT` entrega o arquivo com `\r\n`, e o caminho `EX` entrega o mesmo conteúdo com `\n`
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
- `b3/{AAAAMMDD}/TaxaSwap.txt`: cópia de trabalho da data, sobrescrita a cada obtenção, que é a que a republicação lê (não gravada pela própria republicação).

`{AAAAMMDD}` SHALL ser sempre a data de geração do arquivo, nunca a data em que o download foi feito. Falha do Blob MUST interromper a execução antes da publicação.

#### Scenario: Arquivo de dia anterior obtido pelo `.ex_`
- **WHEN** o caminho `EX` é chamado em `2026-09-15` e, pela busca de dias anteriores, obtém o arquivo com data de geração `20260914`
- **THEN** as duas cópias são gravadas em `b3/20260914/`, e a mensagem sai com `dataBase` = `2026-09-14`

### Requirement: Aviso de carga no tópico existente
O conector SHALL publicar uma única mensagem por carga no tópico já existente `KAFKA_TOPIC` (`tp-event-b3-curve`), substituindo o formato atual de uma mensagem por vértice. A mensagem SHALL ter:
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
  "origem": "TXT",
  "usuario": null,
  "geradoEm": "2026-09-14T20:15:00.000-03:00"
}
```

`usuario` SHALL ser o usuário autenticado (`preferred_username`) quando `origem` = `UPLOAD`, e nulo nos demais casos. O produtor SHALL ser idempotente, com `acks=all` e tempo limite de envio de 30 segundos. `geradoEm` SHALL estar no horário de Brasília, com o deslocamento. O conector MUST NOT publicar vértices, fatores nem tipos de curva.

#### Scenario: Duas cargas da mesma data
- **WHEN** duas cargas diferentes da mesma data são publicadas em seguida
- **THEN** as duas mensagens têm a chave `B3-TS-20260914`, caem na mesma partição e chegam ao processor na ordem em que foram publicadas

### Requirement: Upload do arquivo pelo usuário
`POST /api/b3/taxaswap/upload` SHALL receber um arquivo em `multipart/form-data`, no campo `arquivo`, com no máximo 20 MB, e o parâmetro opcional `dataBase` (`AAAA-MM-DD`). O arquivo SHALL ser tratado pela extensão do nome enviado:
- `.txt`: o próprio `TaxaSwap.txt`, com os bytes lidos como Latin-1 (a codificação da B3);
- `.ex_`: o arquivo compactado da B3, do qual o texto é extraído como no caminho `EX`.

Outra extensão, arquivo vazio ou acima do limite MUST resultar em 400. Em seguida, o arquivo SHALL passar pela forma canônica, pela identidade da carga, pelo arquivamento (as duas cópias) e pela publicação, com `origem` = `UPLOAD` e o usuário na mensagem. Se `dataBase` for informado e diferente da data de geração do arquivo, a resposta MUST ser 422, sem arquivar nem publicar. O upload SHALL exigir o papel `Curvas.Operador`.

#### Scenario: Upload de um arquivo corrigido
- **WHEN** um operador envia pelo upload o `TaxaSwap.txt` de `2026-09-14` com `dataBase` = `2026-09-14`
- **THEN** o arquivo é arquivado em `b3/20260914/`, a mensagem sai com `origem` = `UPLOAD` e o usuário, e a resposta traz o `idCarga`

#### Scenario: Upload do arquivo de outra data
- **WHEN** o operador envia o arquivo de `20260914` informando `dataBase` = `2026-09-15`
- **THEN** a resposta é 422 informando as duas datas, e nada é arquivado nem publicado

### Requirement: Respostas das rotas
As quatro rotas SHALL responder:
- 200 com `{ "idCarga", "dataBase", "hashArquivo", "origem", "correlationId" }`;
- 400 para parâmetro ou corpo inválido;
- 401 sem token, 403 sem papel;
- 404 na republicação, se `b3/{AAAAMMDD}/TaxaSwap.txt` não existir, informando o caminho;
- 422 para arquivo sem linhas, sem data de geração válida ou com caractere fora do Latin-1, e, na republicação e no upload, para data de geração diferente da `dataBase` pedida;
- 502 se o download da B3 falhar;
- 503 se o Blob ou o Kafka estiverem indisponíveis.

Toda resposta SHALL trazer o cabeçalho `X-Correlation-Id`. Erros MUST NOT expor stack trace.

#### Scenario: B3 indisponível
- **WHEN** o download do `TaxaSwap.txt` falha na B3
- **THEN** a rota `swap-process` responde 502, e nada é arquivado nem publicado

### Requirement: Republicação de uma data
`POST /api/b3/taxaswap/publicacao`, com corpo `{ "dataBase": "AAAA-MM-DD" }`, SHALL ler `b3/{AAAAMMDD}/TaxaSwap.txt`, aplicar a forma canônica, arquivar a cópia imutável e publicar com `origem` = `REPUBLICACAO`. É o caminho de recuperação de qualquer falha do processor e de curvas ligadas depois da carga.

#### Scenario: Arquivo de outra data
- **WHEN** a republicação é pedida para `2026-09-15`, e o arquivo em `b3/20260915/` tem data de geração `20260914`
- **THEN** a resposta é 422 informando as duas datas, e nada é publicado

### Requirement: Segurança e rastreabilidade
As quatro rotas MUST exigir autenticação do Entra ID (autenticação do App Service), aceitando só identidades com o papel `Curvas.Operador` ou a identidade de serviço do orquestrador (`services/orchestrator`), que é quem dispara os downloads; `authLevel: "anonymous"` MUST NOT ser usado. O acesso ao Blob SHALL usar Managed Identity (`B3_BLOB_ACCOUNT_URL`), com `B3_BLOB_CONNECTION_STRING` só no ambiente local. Cada execução SHALL registrar em log JSON, com `correlationId`, `idCarga` e horário de Brasília: origem, usuário, data-base, `hashArquivo`, tamanho em bytes, caminhos gravados, resultado e duração.

#### Scenario: Chamada anônima
- **WHEN** a rota de republicação é chamada sem token
- **THEN** a resposta é 401, e nada é lido nem publicado

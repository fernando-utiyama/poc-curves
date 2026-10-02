## Context

A motivação está no proposal e o comportamento na spec. Este change é o lado do conector da divisão do antigo `conector-b3-webhook-ingest`: o conector obtém o arquivo e publica o aviso de carga; o processor (change `processor-carga-b3`) consome, interpreta, grava e avisa o engine. Estado atual verificado no código:

- **Conector, processamento do `TaxaSwap.txt`** (`b3HttpTrigger`, rota `swap-process`, anônima): lê o texto de `B3_SWAP_URL`, que aponta para o arquivo já gravado no Blob (a B3 não publica o `.txt` por URL direta), e faz `fetchSwapFile` → `parseFile` → `publishRecords`, uma mensagem por vértice (`{ "values": { ticker, refDate, diasCorridos, diasUteis, valor, fatorDiario, fatorAcumulado } }`) no tópico `tp-event-b3-curve` (no poc; no real, o tópico configurado em `spring.kafka.topics.b3.name`). O tipo é decidido por `normalizeCurveType` (que publica DCL e DPL como DOL e descarta PTX e INP), os valores são `number` do JavaScript, e os fatores vêm de `curveB3Factors`.
- **Conector, download do `.ex_`** (hoje `b3ContingencyHttpTrigger`, rota `swap-contingency`): baixa o `.ex_` (com busca de até 7 dias úteis anteriores), extrai o texto e grava em `b3/{AAAAMMDD}/TaxaSwap.txt` (`uploadSwapText`), com a data do download na pasta. Não publica nada.
- **Codificação:** a leitura do `.txt` devolve texto (o arquivo local é lido como UTF-8), o download do `.ex_` extrai texto, e o upload grava em Latin-1. O mesmo conteúdo pode chegar com bytes diferentes (fim de linha, codificação).
- **Processor** (change `processor-carga-b3`): hoje grava uma linha por curva e data em `mkt.B3CurveRaw`; passa a consumir o aviso de carga publicado aqui.

## Goals / Non-Goals

**Goals:**
- Um único caminho de obtenção, identificação, arquivamento e publicação para o `TaxaSwap.txt`, venha ele do download do `.ex_`, da leitura do Blob ou de um upload.
- O mesmo conteúdo gera o mesmo `idCarga` por qualquer caminho.
- Rastreabilidade por `idCarga`, do arquivo original até a curva construída.

**Non-Goals:**
- Interpretar, validar ou gravar o conteúdo: é do processor (change `processor-carga-b3`).
- ANBIMA e SOFR: seguem o mesmo contrato com o engine, em changes próprios.
- Construção de curva: é do engine.
- Infraestrutura nova: nenhum tópico, fila ou tabela novos, e nenhuma mudança de schema.

## Decisions

### D1. Conector obtém e entrega; processor interpreta e grava
Interpretar o leiaute é normalizar dado de provedor, função do processor. Com o parse no conector, o resultado precisaria viajar por um arquivo intermediário só para o processor reler (um "cotovelo"), a validação ficaria dividida entre os dois serviços, e o parse ficaria em Node, onde estão o catálogo de descrições errado e os valores em `number`. Agora:
- o conector só obtém o arquivo, identifica a carga, arquiva o bruto e publica um ponteiro;
- o processor é o único que interpreta, valida e grava, em Java, com `BigDecimal` direto do texto.

A única leitura de conteúdo no conector é a data de geração (posições 12–19), necessária para o `idCarga`, o caminho e a chave da mensagem. **Alternativa rejeitada:** conector interpretando e gravando um `vertices.json` no Blob para o processor reler.

### D2. Uma mensagem por carga, com o arquivo no Blob (*claim check*)
O arquivo tem 114 códigos de curva × 278 vértices, mais de 2 MB, acima do limite usual de mensagem (1 MB). O arquivo bruto fica no Blob, e a mensagem leva o caminho e o SHA-256. Com uma mensagem só por carga, o processor tem a carga inteira de uma vez. O formato da mensagem é definido na spec `b3-taxaswap-publicacao` deste change e é o contrato que o processor consome.

**Alternativas rejeitadas:**
- Mensagem por vértice (atual): o processor não sabe quando a carga termina, e a gravação fica parcial.
- Mensagem por curva mais uma de "fim de carga": exige acumular mensagens no processor, depender de ordem e tratar rebalanceamento no meio da carga.

### D3. Mesmo tópico, formato novo
O aviso usa o tópico que já existe, `tp-event-b3-curve`, só com o formato novo. Conector e processor mudam juntos. Mensagens no formato antigo que estiverem no tópico no deploy são registradas como falha e descartadas pelo processor; as datas afetadas são republicadas pelo conector (`b3/taxa-swap/reprocessamento`).

### D4. `idCarga` determinístico
`B3-TS-{AAAAMMDD}-{12 caracteres do SHA-256 do arquivo}`. O mesmo arquivo, por qualquer caminho e em qualquer repetição, gera o mesmo `idCarga`, e a cadeia inteira é idempotente. Um arquivo diferente para a mesma data (republicação pela B3) gera outro `idCarga`; o engine não reconstrói as curvas que já têm pontos e avisa `PONTOS_DIFERENTES_DA_FONTE` nas que a fonte nova mudaria.

### D5. Arquivamento imutável por `idCarga`
`b3/{AAAAMMDD}/TaxaSwap.txt` é a cópia de trabalho da data, sobrescrita pelo download e pelo upload, e pelo reprocessamento sem data (a partir de `recebidos/TaxaSwap.txt`), e é a que o reprocessamento com data lê. `{AAAAMMDD}` é sempre a data de geração do arquivo, nunca a do download. A cópia em `b3/{AAAAMMDD}/cargas/{idCarga}/TaxaSwap.txt` é imutável e é a que o processor lê. Toda curva construída pode ser rastreada até o arquivo exato.

### D6. Sem autenticação própria no conector
O conector não autentica: só os serviços que expõem a API ao front (o bff e o `services/curves`) autenticam. O `usuario` do upload e do reprocessamento vem do cabeçalho opcional `X-Usuario`, enviado pelo bff, e fica nulo se o cabeçalho não vier.

### D7. Chave da mensagem por data, não por carga
A chave `B3-TS-{AAAAMMDD}` põe todas as cargas de uma data (download, leitura do Blob ou upload) na mesma partição, processadas em ordem por uma instância do processor. Com a chave pelo `idCarga`, duas cargas da mesma data poderiam ser gravadas em paralelo nas mesmas curvas, com risco de deadlock ou de a carga mais antiga ganhar.

### D8. Caminhos equivalentes e forma canônica
O download do `.ex_` (o único que a B3 oferece), o reprocessamento do arquivo já gravado no Blob (`b3/taxa-swap/reprocessamento`: com a data informada pelo front ou pelo orquestrador, ou, sem data, o `TaxaSwap.txt` colocado na pasta `recebidos/`, na raiz do container, que o conector cria se não existir) e o upload são caminhos equivalentes, todos com o prefixo `b3/taxa-swap` para deixar claro que é a function da B3 para o Taxa Swap, e nenhum é tratado como exceção, nem no nome. Para que o mesmo conteúdo gere o mesmo `idCarga` por qualquer caminho, o conector converte o texto numa forma canônica antes do hash: linhas separadas por `\n`, sem linhas vazias, sem mexer no conteúdo das linhas, codificado em Latin-1. Sem isso, uma diferença de fim de linha faria o mesmo arquivo parecer outra carga.

### D9. Upload pelo usuário
O upload (`.txt` ou `.ex_`, até 20 MB) cobre o caso de a B3 estar inacessível para o download, ou de um arquivo corrigido recebido por outro meio. Passa pelo mesmo caminho dos outros e leva o usuário na mensagem e no log, para rastreio.

### D10. O orquestrador dispara
Quem dispara os downloads é o orquestrador (`services/orchestrator`), que orquestra todo o processo: ele chama o download (`b3/taxa-swap/download`) e, quando preciso, força o processamento de uma data (`b3/taxa-swap/reprocessamento`), em que horário, as novas tentativas quando a B3 ainda não publicou, e o alerta de "carga não recebida". O conector só executa o que é chamado e responde com o `idCarga`. O upload e o `b3/taxa-swap/reprocessamento` também podem ser chamados pelo front, por um operador, que informa a data. Configurar essas tarefas no orquestrador é do change dele.

### D11. No mínimo duas instâncias
O conector (Azure Functions) roda em no mínimo duas instâncias no Azure. Nada depende de estado em memória: a identidade da carga é o hash do arquivo, a cópia imutável é gravada com `If-None-Match`, e a pasta `recebidos/` é criada de forma idempotente.

## Risks / Trade-offs

- **Conector e processor mudam juntos (BREAKING).** → Deploy coordenado com o change `processor-carga-b3`; o formato antigo deixa de ser publicado e consumido no mesmo release.
- **A resposta HTTP do conector não diz quais códigos são inválidos.** → A informação fica no log da carga no processor, com o `idCarga`.
- **Um arquivo novo da B3 para a mesma data substitui a cópia de trabalho.** → As versões anteriores ficam no Blob por `idCarga`, e o engine decide sobre recálculo.

## Migration Plan

1. Sem tópico novo e sem mudança de schema. Dar ao conector acesso de escrita à pasta `b3/` do Blob (e ao `recebidos/`) por Managed Identity.
2. **Deploy conjunto de conector e processor** (change `processor-carga-b3`), porque o formato da mensagem em `tp-event-b3-curve` muda. Os dois sobem no mesmo release, para que não haja intervalo em que um publica num formato que o outro não entende; se houver, as datas afetadas são reprocessadas pelo `b3/taxa-swap/reprocessamento` depois.
3. Reprocessar pelo `b3/taxa-swap/reprocessamento` as datas que precisarem estar em `tBtrsCurvaPrimr`.
4. **Rollback:** voltar os dois deploys, conector e processor juntos.

## Open Questions

Nenhuma.

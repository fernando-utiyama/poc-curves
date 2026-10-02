## Context

A motivação está no proposal e o comportamento na spec. Este change é o lado do processor da divisão do antigo `conector-b3-webhook-ingest`: o conector obtém o arquivo e publica o aviso de carga (a spec `b3-taxaswap-publicacao` daquele change é a fonte da verdade do formato da mensagem); o processor consome, interpreta, grava e avisa o engine. Estado atual verificado no código:

- **Processor** (`B3KafkaConsumer`, tópico `tp-event-b3-curve`): lê a mensagem direto em `B3CurveRaw` sem desembrulhar `values`, guarda o valor em `Double` e grava em `mkt.B3CurveRaw` por *upsert* na chave (ticker, data). Como chega uma mensagem por vértice, cada vértice sobrescreve o anterior: sobra uma linha por curva e data, em vez de 278.
- **Mensagem de hoje** (publicada pelo conector no tópico `tp-event-b3-curve`; no poc; no real, o tópico configurado em `spring.kafka.topics.b3.name`): uma por vértice, `{ "values": { ticker, refDate, diasCorridos, diasUteis, valor, fatorDiario, fatorAcumulado } }`, com o tipo decidido por `normalizeCurveType` (DCL e DPL como DOL; PTX e INP descartados) e valores `number` do JavaScript.
- **Banco:** `tBtrsCurvaPrimr` tem FK de `cTickerIndcd` para `tCurvaMercd`; `tCurvaPrvdr` liga a curva de mercado ao provedor e ao ticker no provedor (`cTickerPrvdr`); `cIdtfdUnic` é `INT NOT NULL` sem identity nem sequência no `001_SCRIPT_INICIAL.sql`. O processor não escreve em `tCurvaMercd`.
- **Engine** (`engine-construcao-curvas`): lê `tBtrsCurvaPrimr` pelo nome da curva e constrói automaticamente ao receber `POST /api/v1/cargas` com a quantidade de linhas por código (spec `curve-load-trigger`); não guarda registro da carga.

## Goals / Non-Goals

**Goals:**
- Um único caminho de interpretação e gravação para o `TaxaSwap.txt`, venha ele do download do `.ex_`, da leitura do Blob ou de um upload (os três chegam ao processor como o mesmo aviso).
- Valores exatamente como publicados pela B3, código exato, sem fatores.
- Gravação atômica por carga e aviso ao engine só depois do commit.
- Rastreabilidade por `idCarga`, do arquivo original até a curva construída.

**Non-Goals:**
- Obter, canonizar e arquivar o arquivo, e publicar o aviso: é do conector (change `conector-b3-webhook-ingest`).
- ANBIMA e SOFR: seguem o mesmo contrato com o engine, em changes próprios.
- Construção de curva: é do engine.
- Cadastro em `tCurvaMercd` e `tCurvaPrvdr`: códigos sem provedor em `tCurvaPrvdr` são ignorados.
- Infraestrutura nova: nenhum tópico, fila ou tabela novos, e nenhuma mudança de schema.

## Decisions

### D1. Conector obtém e entrega; processor interpreta e grava
Interpretar o leiaute é normalizar dado de provedor, função do processor. Com o parse no conector, o resultado precisaria viajar por um arquivo intermediário só para o processor reler (um "cotovelo"), a validação ficaria dividida entre os dois serviços, e o parse ficaria em Node, onde estão o catálogo de descrições errado e os valores em `number`. Agora o processor é o único que interpreta, valida e grava, em Java, com `BigDecimal` direto do texto; o conector só obtém o arquivo, identifica a carga, arquiva o bruto e publica um ponteiro. **Alternativa rejeitada:** conector interpretando e gravando um `vertices.json` no Blob para o processor reler.

### D2. Uma mensagem por carga, com o arquivo no Blob (*claim check*)
O arquivo tem 114 códigos de curva × 278 vértices, mais de 2 MB, acima do limite usual de mensagem (1 MB). O arquivo bruto fica no Blob, e a mensagem leva o caminho e o SHA-256 (formato na spec `b3-taxaswap-publicacao` do conector). Com uma mensagem só por carga, o processor tem a carga inteira de uma vez: grava tudo numa transação e sabe exatamente quando avisar o engine.

**Alternativas rejeitadas:**
- Mensagem por vértice (atual): o processor não sabe quando a carga termina, e a gravação fica parcial.
- Mensagem por curva mais uma de "fim de carga": exige acumular mensagens no processor, depender de ordem e tratar rebalanceamento no meio da carga.

### D3. Mesmo tópico, formato novo
O processor consome o tópico que já existe, `tp-event-b3-curve`, só com o formato novo. Conector e processor mudam juntos. Mensagens no formato antigo que estiverem no tópico no deploy caem nas regras de validação do aviso, são registradas como falha e descartadas; as datas afetadas são republicadas pelo conector.

### D4. Valor em `BigDecimal` direto do texto
O campo posicional já é um decimal exato (sinal + 14 dígitos com 7 decimais). O processor o converte direto para `BigDecimal` com escala 7, sem passar por `double`.

### D5. Código exato, sem catálogo de descrições e sem filtro de tipo
O processor usa o código das posições 22–26 como está. Quem decide o que gravar é o cadastro (`tCurvaPrvdr`), e quem decide o que construir é o engine.

### D6. Identidade da carga e idempotência
O `idCarga` é gerado pelo conector (`B3-TS-{AAAAMMDD}-{12 caracteres do SHA-256 do arquivo}`): o mesmo arquivo, por qualquer caminho e em qualquer repetição, gera o mesmo `idCarga`, e a cadeia inteira é idempotente. O processor regrava as mesmas linhas e repete o aviso. Um arquivo diferente para a mesma data (republicação pela B3) gera outro `idCarga`; o engine não reconstrói as curvas que já têm pontos e avisa `PONTOS_DIFERENTES_DA_FONTE` nas que a fonte nova mudaria.

### D7. Validação: rejeitar o arquivo ou o código, nunca a linha
Uma linha descartada deixaria uma curva com 277 vértices que parece normal. Por isso:
- linha ilegível (tamanho errado, data divergente) rejeita o arquivo;
- campo inválido numa linha de código legível rejeita aquele código inteiro, que fica no log da carga.

O engine, sem aquele código na carga, responde `INSUMO_AUSENTE` para a curva correspondente, de forma explícita.

### D8. Transação única, conferência antes do commit, aviso depois
O processor grava os vértices de todos os códigos mapeados numa transação e confere as contagens antes do commit. Só depois do commit chama o engine, de modo que o engine nunca é avisado de uma carga que não está gravada. Se o aviso não for aceito dentro da janela (D10), os vértices ficam gravados, e o processor registra `CARGA_FALHOU` com o estado `GRAVADA_SEM_AVISO`; republicar a data regrava as mesmas linhas e repete o aviso, sem efeito colateral.

### D9. Mapeamento pelo `tCurvaPrvdr`, sob o nome da curva de mercado
`tCurvaPrvdr` liga a curva de mercado ao provedor e ao ticker da curva no provedor. O processor a usa para saber quais curvas de mercado recebem os vértices de cada código, e grava `tBtrsCurvaPrimr.cTickerIndcd` com o nome da curva de mercado. A FK para `tCurvaMercd` fica satisfeita pela própria curva de mercado, sem linhas de "curva da fonte" em `tCurvaMercd` e sem convenção de nome. O processor só lê `tCurvaPrvdr`. **Alternativa rejeitada:** uma linha em `tCurvaMercd` por código da fonte (ex.: `B3_TAXA_SWAP_PRE`), que duplica o cadastro e depende de uma convenção de nome.

### D10. Aviso ao engine por HTTP, com repetição curta e alerta cedo
Não há tópico Kafka para o engine, então o aviso é o webhook `POST /api/v1/cargas`, chamado pelo endereço do serviço. O balanceador do Azure entrega cada chamada a uma instância pronta. Qual instância atende não importa, porque o estado está no banco e a trava por curva serializa as construções.

As curvas devem estar construídas em minutos. O processor repete o aviso por até 10 minutos, com espera crescente até 1 minuto, o suficiente para sobreviver a um reinício ou a uma troca de instância do engine sem intervenção. Aos 2 minutos sem aviso aceito, emite `AVISO_ATRASADO` como alerta. Repetir é sempre seguro, inclusive depois de um tempo esgotado em que o engine continuou construindo: a repetição recebe 409 e depois `EXISTENTE`.

Tempos limite em cadeia: webhook do engine (120 s, definido na segunda parte do engine; na primeira só valem os 30 s da trava e os tempos do banco) < chamada do processor (150 s) < tempo ocioso do balanceador do Azure.

**O que a primeira parte do engine (`engine-construcao-curvas`) espera do processor.** Só isto: (1) as linhas de `tBtrsCurvaPrimr` com `cTickerIndcd` = nome da curva, `dBaseReft`, `cDiaCorri`, `cDiaUtil` publicado e `vPrecoTx` em percentual, gravadas numa transação sob a trava da curva; (2) o webhook `POST /api/v1/cargas` depois do commit, com `idCarga`, `fonte`, `produto`, `dataBase` e `linhasPorCodigo` (inclusive as linhas que o modelo descarta), repetido com o mesmo `idCarga` até 2xx; (3) o tratamento de 409 `CONSTRUCAO_EM_ANDAMENTO` como repetição. O engine não exige autenticação, então o processor chama o webhook sem token. A primeira parte do engine também não constrói por origem secundária e recusa curva derivada; a gravação sob curvas de origem secundária fica pronta sem uso até a segunda parte.

### D11. Ordem por data
O conector publica com a chave `B3-TS-{AAAAMMDD}`, o que põe todas as cargas de uma data (download, leitura do Blob ou upload) na mesma partição, processadas em ordem por uma instância do processor, com uma mensagem por vez (`max.poll.records` = 1). Com a chave pelo `idCarga`, duas cargas da mesma data poderiam ser gravadas em paralelo nas mesmas curvas, com risco de deadlock ou de a carga mais antiga ganhar.

### D12. Curva ligada depois da carga
Se o cadastro liga uma curva nova em `tCurvaPrvdr` depois que a carga do dia foi gravada, a curva não tem vértices, e o engine responde `INSUMO_AUSENTE`. O procedimento é reprocessar a data pelo `b3/taxa-swap/reprocessamento` do conector: o mesmo arquivo gera o mesmo `idCarga`, o processor regrava todas as curvas mapeadas, incluindo a nova, e o engine constrói só as que ainda não têm pontos.

### D13. Sem tópico novo e sem tópico de falhas
Falha definitiva no processor não vai para uma fila de falhas: vira o evento `CARGA_FALHOU` (log de erro e métrica, para alerta), e a mensagem é confirmada. Guardar a mensagem não é necessário, porque o arquivo está arquivado no Blob por `idCarga`, e o `b3/taxa-swap/reprocessamento` do conector reproduz a carga com o mesmo `idCarga`, de forma idempotente em toda a cadeia.

### D14. No mínimo duas instâncias
O processor roda em no mínimo duas instâncias no Azure. Nada depende de estado em memória. As instâncias ficam no mesmo consumer group: cada aviso é processado por uma só, e a chave `B3-TS-{AAAAMMDD}` põe as cargas da mesma data na mesma partição, em ordem; cargas de datas diferentes podem ser gravadas em paralelo sem conflito. Um aviso repetido (rebalanceamento entre instâncias) regrava as mesmas linhas e repete o aviso ao engine, que é idempotente.

## Risks / Trade-offs

- **Conector e processor mudam juntos (BREAKING).** → Deploy coordenado com o change `conector-b3-webhook-ingest`; o formato antigo deixa de ser publicado e consumido no mesmo release.
- **Processor passa a conhecer o leiaute da B3.** → É a função dele (normalizar dado de provedor); o parser fica num lugar só.
- **A resposta HTTP do conector não diz quais códigos são inválidos.** → A informação fica no log da carga no processor, com o `idCarga`.
- **Processor depende do Blob.** → O arquivo é imutável e conferido por hash. Se o Blob estiver fora, a leitura é repetida por até 5 minutos; depois, `CARGA_FALHOU` e reprocessamento da data pelo `b3/taxa-swap/reprocessamento`.
- **Código com linha inválida não chega ao engine.** → É intencional; aparece no log do processor e como `INSUMO_AUSENTE` no engine.
- **Um arquivo novo da B3 para a mesma data substitui os brutos.** → As versões anteriores ficam no Blob por `idCarga`, e o engine decide sobre recálculo.

## Migration Plan

1. Sem tópico novo e sem mudança de schema. Dar ao processor (leitura) acesso à pasta `b3/` do Blob por Managed Identity, e configurar o tempo ocioso do balanceador na frente do engine acima de 150 segundos.
2. Cadastro: ligar em `tCurvaPrvdr` cada curva de mercado ao seu código na fonte (`B3`/`TS`/código).
3. **Deploy conjunto de conector e processor** (change `conector-b3-webhook-ingest`), porque o formato da mensagem em `tp-event-b3-curve` muda. O consumo antigo sai no mesmo release; mensagens no formato antigo que sobrarem no tópico são registradas como `CARGA_FALHOU`. Os dois sobem no mesmo release, para que não haja intervalo em que um publica num formato que o outro não entende; se houver, as datas afetadas são reprocessadas pelo conector depois.
4. Reprocessar pelo `b3/taxa-swap/reprocessamento` do conector as datas que precisarem estar em `tBtrsCurvaPrimr`.
5. **Rollback:** voltar os dois deploys, conector e processor juntos. `mkt.B3CurveRaw` não é apagada por este change.

## Open Questions

Nenhuma. O `tBtrsCurvaPrimr.cIdtfdUnic` (`INT NOT NULL`, sem identity nem sequência) é gerado por `MAX(cIdtfdUnic) + 1` lido com `UPDLOCK, HOLDLOCK` na mesma transação da gravação, como o serviço de cadastro faz na edição manual. Antes de apagar e inserir, o processor trava a linha de cada curva mapeada em `tCurvaMercd` (`UPDLOCK, ROWLOCK`, em ordem do nome da curva, esperando até 60 segundos), a mesma trava da edição manual e da construção no engine; sem a trava, a falha é transitória.

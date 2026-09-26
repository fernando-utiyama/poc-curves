## Context

A motivação está no proposal e o comportamento nas specs. Estado atual verificado no código:

- **Conector, download do `TaxaSwap.txt`** (`b3HttpTrigger`, rota `swap-process`, anônima): `fetchSwapFile` → `parseFile` → `publishRecords`, uma mensagem por vértice (`{ "values": { ticker, refDate, diasCorridos, diasUteis, valor, fatorDiario, fatorAcumulado } }`) no tópico `tp-event-b3-curve`. O tipo é decidido por `normalizeCurveType` (que publica DCL e DPL como DOL e descarta PTX e INP), os valores são `number` do JavaScript, e os fatores vêm de `curveB3Factors`.
- **Conector, download do `.ex_`** (hoje `b3ContingencyHttpTrigger`, rota `swap-contingency`): baixa o `.ex_` (com busca de até 7 dias úteis anteriores), extrai o texto e grava em `b3/{AAAAMMDD}/TaxaSwap.txt` (`uploadSwapText`), com a data do download na pasta. Não publica nada.
- **Codificação:** o download do `.txt` devolve texto (o arquivo local é lido como UTF-8), o do `.ex_` extrai texto, e o upload grava em Latin-1. O mesmo conteúdo pode chegar com bytes diferentes (fim de linha, codificação).
- **Processor** (`B3KafkaConsumer`, tópico `tp-event-b3-curve`): lê a mensagem direto em `B3CurveRaw` sem desembrulhar `values`, guarda o valor em `Double` e grava em `mkt.B3CurveRaw` por *upsert* na chave (ticker, data). Como chega uma mensagem por vértice, cada vértice sobrescreve o anterior: sobra uma linha por curva e data, em vez de 278.
- **Banco:** `tBtrsCurvaPrimr` tem FK de `cTickerIndcd` para `tCurvaMercd`; `tCurvaPrvdr` liga a curva de mercado ao provedor e ao ticker no provedor (`cTickerPrvdr`); a sequência `seq_tbtrscurvaprimr_cidtfdunic` já existe (V23), e a credencial do processor só tem `SELECT, REFERENCES` em `tCurvaMercd`.
- **Engine** (`engine-modelos-curva`): lê `tBtrsCurvaPrimr` pelo nome da curva e só constrói depois de `POST /api/v1/cargas` com a quantidade de linhas por código (spec `curve-load-trigger`).

## Goals / Non-Goals

**Goals:**
- Um único caminho de interpretação e gravação para o `TaxaSwap.txt`, venha ele de qualquer download, de um upload ou de uma republicação.
- Valores exatamente como publicados pela B3, código exato, sem fatores.
- Gravação atômica por carga e aviso ao engine só depois do commit.
- Rastreabilidade por `idCarga`, do arquivo original até a curva construída.

**Non-Goals:**
- ANBIMA e SOFR: seguem o mesmo contrato com o engine, em changes próprios.
- Construção de curva: é do engine.
- Cadastro em `tCurvaMercd` e `tCurvaPrvdr`: códigos sem ligação em `tCurvaPrvdr` são ignorados.
- Infraestrutura nova: nenhum tópico, fila ou tabela novos, e nenhuma mudança de schema.

## Decisions

### D1. Conector obtém e entrega; processor interpreta e grava
Interpretar o leiaute é normalizar dado de provedor, função do processor. Com o parse no conector, o resultado precisaria viajar por um arquivo intermediário só para o processor reler (um "cotovelo"), a validação ficaria dividida entre os dois serviços, e o parse ficaria em Node, onde estão o catálogo de descrições errado e os valores em `number`. Agora:
- o conector só obtém o arquivo, identifica a carga, arquiva o bruto e publica um ponteiro;
- o processor é o único que interpreta, valida e grava, em Java, com `BigDecimal` direto do texto.

A única leitura de conteúdo no conector é a data de geração (posições 12–19), necessária para o `idCarga`, o caminho e a chave da mensagem. **Alternativa rejeitada:** conector interpretando e gravando um `vertices.json` no Blob para o processor reler.

### D2. Uma mensagem por carga, com o arquivo no Blob (*claim check*)
O arquivo tem cerca de 110 curvas × 278 vértices, mais de 2 MB, acima do limite usual de mensagem (1 MB). O arquivo bruto fica no Blob, e a mensagem leva o caminho e o SHA-256. Com uma mensagem só por carga, o processor tem a carga inteira de uma vez: grava tudo numa transação e sabe exatamente quando avisar o engine.

**Alternativas rejeitadas:**
- Mensagem por vértice (atual): o processor não sabe quando a carga termina, e a gravação fica parcial.
- Mensagem por curva mais uma de "fim de carga": exige acumular mensagens no processor, depender de ordem e tratar rebalanceamento no meio da carga.

### D3. Mesmo tópico, formato novo
O aviso usa o tópico que já existe, `tp-event-b3-curve`, só com o formato novo. Conector e processor mudam juntos. Mensagens no formato antigo que estiverem no tópico no deploy são registradas como falha e descartadas; as datas afetadas são republicadas.

### D4. Valor em `BigDecimal` direto do texto
O campo posicional já é um decimal exato (sinal + 14 dígitos com 7 decimais). O processor o converte direto para `BigDecimal` com escala 7, sem passar por `double`.

### D5. Código exato, sem catálogo de descrições e sem filtro de tipo
O processor usa o código das posições 22–26 como está. Quem decide o que gravar é o cadastro (`tCurvaPrvdr`), e quem decide o que construir é o engine.

### D6. `idCarga` determinístico
`B3-TS-{AAAAMMDD}-{12 caracteres do SHA-256 do arquivo}`. O mesmo arquivo, por qualquer caminho e em qualquer repetição, gera o mesmo `idCarga`, e a cadeia inteira é idempotente. Um arquivo diferente para a mesma data (republicação pela B3) gera outro `idCarga`, que o engine trata como republicação.

### D7. Validação: rejeitar o arquivo ou o código, nunca a linha
Uma linha descartada deixaria uma curva com 277 vértices que parece normal. Por isso:
- linha ilegível (tamanho errado, data divergente) rejeita o arquivo;
- campo inválido numa linha de código legível rejeita aquele código inteiro, que fica no log da carga.

O engine, sem aquele código na carga, responde `CARGA_NAO_CONCLUIDA` para a curva correspondente, de forma explícita.

### D8. Arquivamento imutável por `idCarga`
`b3/{AAAAMMDD}/TaxaSwap.txt` é a cópia de trabalho da data, sobrescrita por qualquer download ou upload, e é a que a republicação lê. `{AAAAMMDD}` é sempre a data de geração do arquivo, nunca a do download. A cópia em `b3/{AAAAMMDD}/cargas/{idCarga}/TaxaSwap.txt` é imutável e é a que o processor lê. Toda curva construída pode ser rastreada até o arquivo exato.

### D9. Processor: transação única, conferência antes do commit, aviso depois
O processor grava os vértices de todos os códigos mapeados numa transação e confere as contagens antes do commit. Só depois do commit chama o engine, de modo que o engine nunca é avisado de uma carga que não está gravada. Se o aviso não for aceito dentro da janela (D12), os vértices ficam gravados, e o processor registra `CARGA_FALHOU` com o estado `GRAVADA_SEM_AVISO`; republicar a data regrava as mesmas linhas e repete o aviso, sem efeito colateral.

### D10. Autenticação do Entra ID nas rotas do conector
As rotas do conector publicam dado de mercado usado em risco. Passam a exigir o Entra ID (autenticação do App Service), com o papel `Curvas.Operador` ou a identidade do orquestrador, no mesmo registro de aplicação do engine.

### D11. Mapeamento pelo `tCurvaPrvdr`, sob o nome da curva de mercado
`tCurvaPrvdr` liga a curva de mercado ao provedor e ao ticker da curva no provedor. O processor a usa para saber quais curvas de mercado recebem os vértices de cada código, e grava `tBtrsCurvaPrimr.cTickerIndcd` com o nome da curva de mercado. A FK para `tCurvaMercd` fica satisfeita pela própria ligação, sem linhas de "curva da fonte" em `tCurvaMercd` e sem convenção de nome. O processor só lê `tCurvaPrvdr`. **Alternativa rejeitada:** uma linha em `tCurvaMercd` por código da fonte (como `B3_TAXA_SWAP_PRE`, criada à mão nas migrations V23 e V24 do poc), que duplica o cadastro e depende de uma convenção de nome.

### D12. Aviso ao engine por HTTP, com repetição curta e alerta cedo
Não há tópico Kafka para o engine, então o aviso é o webhook `POST /api/v1/cargas`, chamado pelo endereço do serviço. O balanceador do Azure entrega cada chamada a uma instância pronta. Qual instância atende não importa, porque o estado está no banco e no Blob e a trava por curva serializa as construções.

As curvas devem estar construídas em minutos. O processor repete o aviso por até 10 minutos, com espera crescente até 1 minuto, o suficiente para sobreviver a um reinício ou a uma troca de instância do engine sem intervenção. Aos 2 minutos sem aviso aceito, emite `AVISO_ATRASADO` como alerta. Repetir é sempre seguro, inclusive depois de um tempo esgotado em que o engine continuou construindo: a repetição recebe 409 e depois `EXISTENTE`.

Tempos limite em cadeia: webhook do engine (120 s) < chamada do processor (150 s) < tempo ocioso do balanceador do Azure.

### D13. Chave da mensagem por data, não por carga
A chave `B3-TS-{AAAAMMDD}` põe todas as cargas de uma data (qualquer download, upload ou republicação) na mesma partição, processadas em ordem por uma instância do processor. Com a chave pelo `idCarga`, duas cargas da mesma data poderiam ser gravadas em paralelo nas mesmas curvas, com risco de deadlock ou de a carga mais antiga ganhar.

### D14. Curva ligada depois da carga
Se o cadastro liga uma curva nova em `tCurvaPrvdr` depois que a carga do dia foi gravada, a curva não tem vértices, e o engine responde `CARGA_NAO_CONCLUIDA`. O procedimento é republicar a data: o mesmo arquivo gera o mesmo `idCarga`, o processor regrava todas as curvas mapeadas, incluindo a nova, e o engine constrói só as que ainda não têm pontos.

### D15. Sem tópico novo e sem tópico de falhas
Falha definitiva no processor não vai para uma fila de falhas: vira o evento `CARGA_FALHOU` (log de erro e métrica, para alerta), e a mensagem é confirmada. Guardar a mensagem não é necessário, porque o arquivo está arquivado no Blob por `idCarga`, e a rota de republicação do conector reproduz a carga com o mesmo `idCarga`, de forma idempotente em toda a cadeia.

### D16. Caminhos equivalentes e forma canônica
Os downloads do `.txt` e do `.ex_`, o upload e a republicação são caminhos equivalentes: o do `.ex_` pode virar o principal, e nenhum é tratado como exceção, nem no nome. Para que o mesmo conteúdo gere o mesmo `idCarga` por qualquer caminho, o conector converte o texto numa forma canônica antes do hash: linhas separadas por `\n`, sem linhas vazias, sem mexer no conteúdo das linhas, codificado em Latin-1. Sem isso, uma diferença de fim de linha faria o mesmo arquivo parecer uma republicação.

### D17. Upload pelo usuário
O upload (`.txt` ou `.ex_`, até 20 MB) cobre o caso de a B3 estar inacessível para os dois downloads, ou de um arquivo corrigido recebido por outro meio. Passa pelo mesmo caminho dos outros e leva o usuário na mensagem e no log, para rastreio.

### D18. O orquestrador dispara
Quem dispara os downloads é o orquestrador (`services/orchestrator`), que orquestra todo o processo: ele define qual download roda (`swap-process`, `swap-ex` ou os dois), em que horário, as novas tentativas quando a B3 ainda não publicou, e o alerta de "carga não recebida". O conector só executa o que é chamado e responde com o `idCarga`. O upload e a republicação também podem ser chamados por um operador. Configurar essas tarefas no orquestrador é do change dele.

## Risks / Trade-offs

- **Conector e processor mudam juntos (BREAKING).** → Deploy coordenado; o formato antigo deixa de ser publicado e consumido no mesmo release.
- **Processor passa a conhecer o leiaute da B3.** → É a função dele (normalizar dado de provedor); o parser fica num lugar só.
- **A resposta HTTP do conector não diz quais códigos são inválidos.** → A informação fica no log da carga no processor, com o `idCarga`.
- **Processor depende do Blob.** → O arquivo é imutável e conferido por hash. Se o Blob estiver fora, a leitura é repetida por até 5 minutos; depois, `CARGA_FALHOU` e republicação da data.
- **Código com linha inválida não chega ao engine.** → É intencional; aparece no log do processor e como `CARGA_NAO_CONCLUIDA` no engine.
- **Republicação substitui os brutos da data.** → As versões anteriores ficam no Blob por `idCarga`, e o engine decide sobre recálculo.
- **Linhas `B3_TAXA_SWAP_*` em `tCurvaMercd`** (V23 e V24) deixam de ser usadas. → Podem ficar no banco; apagá-las é decisão do cadastro.

## Migration Plan

1. Sem tópico novo e sem mudança de schema. Dar ao conector (escrita) e ao processor (leitura) acesso à pasta `b3/` do Blob por Managed Identity, e configurar o tempo ocioso do balanceador na frente do engine acima de 150 segundos.
2. Cadastro: ligar em `tCurvaPrvdr` cada curva de mercado ao seu código na fonte (`B3`/`TS`/código).
3. Deploy conjunto de conector e processor, porque o formato da mensagem em `tp-event-b3-curve` muda. O consumo antigo sai no mesmo release.
4. Republicar pela rota nova as datas que precisarem estar em `tBtrsCurvaPrimr`.
5. **Rollback:** voltar os dois deploys. `mkt.B3CurveRaw` não é apagada por este change.

## Open Questions

Nenhuma.

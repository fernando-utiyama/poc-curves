## Purpose

No `services/processor`, consumir o aviso de carga do `TaxaSwap.txt` publicado pelo conector no tópico existente `tp-event-b3-curve`, ler o arquivo bruto arquivado no Blob, interpretá-lo pelo leiaute oficial da B3, gravar os vértices em `tBtrsCurvaPrimr` sob as curvas de mercado ligadas a cada código em `tCurvaPrvdr`, numa única transação, e, depois do commit, avisar o engine de que a carga está completa. O processor é o único ponto que interpreta e grava o arquivo.

## ADDED Requirements

### Requirement: Consumo do aviso de carga
O processor SHALL consumir o tópico existente `tp-event-b3-curve` com:
- confirmação manual: a mensagem só é confirmada depois de processada até o fim (gravação e aviso ao engine) ou de registrada como falha definitiva;
- `max.poll.records` = 1 e uma mensagem de cada vez por partição (as cargas de uma mesma data chegam na mesma partição, em ordem);
- `max.poll.interval.ms` maior que a soma das janelas de repetição com folga (padrão 1.200.000, 20 minutos), para que o Kafka não redistribua a partição enquanto a carga ainda está sendo processada.

A mensagem SHALL ter o formato da spec `b3-taxaswap-publicacao` e ser rejeitada como falha definitiva, sem gravar nada, quando:
- não for JSON válido, ou faltar campo obrigatório;
- `fonte` for diferente de `B3` ou `produto` diferente de `TS`;
- `idCarga` não casar com `^B3-TS-\d{8}-[0-9a-f]{12}$`, ou a data do `idCarga` for diferente de `dataBase`;
- `arquivo.caminho` for diferente de `b3/{AAAAMMDD}/cargas/{idCarga}/TaxaSwap.txt`;
- `arquivo.sha256` não tiver 64 caracteres hexadecimais minúsculos;
- `origem` não for `TXT`, `EX`, `UPLOAD` ou `REPUBLICACAO`.

Mensagens no formato antigo, por vértice, caem nessas regras. O cabeçalho `X-Correlation-Id` da mensagem SHALL ser usado em todos os logs da carga e repassado ao engine; na falta dele, o processor SHALL gerar um UUID.

#### Scenario: Mensagem malformada
- **WHEN** chega uma mensagem sem `idCarga`
- **THEN** o processor registra `CARGA_FALHOU` com o motivo, confirma a mensagem e nada é gravado

### Requirement: Leitura do arquivo bruto
O processor SHALL ler `arquivo.caminho` do Blob Storage (`processor.blob.endpoint` e `processor.blob.container`, com Managed Identity) e conferir que a quantidade de bytes é igual a `arquivo.bytes` e que o SHA-256 dos bytes lidos é igual a `arquivo.sha256`. Divergência MUST ser registrada como falha definitiva, sem gravar nada. Falha de acesso ao Blob SHALL seguir a política de repetição.

#### Scenario: Arquivo alterado no Blob
- **WHEN** o arquivo arquivado foi alterado depois da publicação e o SHA-256 não confere
- **THEN** o processor registra `CARGA_FALHOU` informando a divergência, e nada é gravado

### Requirement: Parse pelo leiaute oficial
O processor SHALL decodificar o arquivo como Latin-1, separar as linhas por `\n` (o arquivo já está na forma canônica da spec `b3-taxaswap-publicacao`) e interpretar cada linha pelo leiaute oficial "Taxas de Mercado para Swaps", com posições de 1 a 72:

| Campo | Posições | Uso |
|---|---|---|
| Data de geração do arquivo | 12–19 (`AAAAMMDD`) | data-base |
| Código da taxa | 22–26 | código da curva, sem espaços nas pontas |
| Descrição da taxa | 27–41 | só informativa |
| Dias corridos | 42–46 | inteiro |
| Dias úteis (número de saques) | 47–51 | inteiro |
| Sinal | 52 | `+` ou `-` |
| Taxa teórica | 53–66 | 14 dígitos, 7 decimais |
| Característica do vértice | 67 | `F` ou `M` (só informativa) |
| Código do vértice | 68–72 | só informativo |

O valor SHALL ser convertido direto do texto para `BigDecimal` com escala 7 (os 14 dígitos como inteiro, com o sinal, divididos por 10^7), sem passar por `double`. O código MUST ser usado exatamente como está no arquivo; a descrição MUST NOT ser usada para decidir o código. O processor MUST NOT calcular fatores.

#### Scenario: Primeiro vértice da DCL
- **WHEN** o arquivo de `2026-09-14` tem a linha `0049060010120260914T1DCL  CUPOM LIMPO - S0000100001-00001179600000F00001`
- **THEN** o vértice é código `DCL`, dias corridos 1, dias úteis 1 e valor -117.9600000

#### Scenario: Curvas pelo código exato
- **WHEN** o arquivo tem linhas com os códigos `DCL`, `DPL`, `PTX` e `INP`
- **THEN** cada uma é tratada pelo próprio código, nenhuma como `DOL`, e nenhuma é descartada por tipo

### Requirement: Validação do arquivo
O arquivo inteiro MUST ser rejeitado, registrando `CARGA_FALHOU` com o estado `NAO_GRAVADA` e sem gravar nada, quando:
- não tiver nenhuma linha;
- alguma linha não vazia não tiver exatamente 72 caracteres;
- as linhas tiverem datas de geração diferentes entre si, ou diferentes de `dataBase` da mensagem.

Uma linha cujo código (posições 22–26, sem espaços) fique vazio também rejeita o arquivo. Se o código de uma linha for legível mas outro campo for inválido, esse **código inteiro** SHALL ser marcado como inválido, com a linha e o motivo, e MUST NOT ser gravado; os demais códigos seguem. São campos inválidos:
- dias corridos ou dias úteis com caractere que não seja dígito;
- dias corridos menor que 1, dias úteis menor que 1, ou dias úteis maior que dias corridos;
- sinal diferente de `+` e `-`;
- taxa com caractere que não seja dígito;
- dias corridos repetidos dentro do mesmo código.

Nenhuma linha isolada SHALL ser descartada. Os códigos inválidos SHALL constar do log da carga, e uma carga com código inválido MUST NOT ser registrada como sucesso total.

#### Scenario: Linha corrompida
- **WHEN** uma linha do arquivo tem 60 caracteres
- **THEN** a carga é rejeitada com `CARGA_FALHOU` informando a linha, e nada é gravado

#### Scenario: Campo inválido num código
- **WHEN** uma linha do código `DPL` tem a taxa `0000ABC1859000`
- **THEN** o código `DPL` não é gravado e aparece como inválido no log, com a linha e o motivo, e os demais códigos são gravados

### Requirement: Gravação sob a curva de mercado mapeada em tCurvaPrvdr
Para cada código válido, o processor SHALL buscar em `tCurvaPrvdr` as linhas com `iPrvdrDados` = `B3`, `cPrvdrMercd` = `TS` e `cTickerPrvdr` = código; cada linha encontrada indica uma curva de mercado (`tCurvaPrvdr.cTickerIndcd`) que recebe os vértices daquele código. Um código pode alimentar mais de uma curva, e todas SHALL receber os vértices, qualquer que seja o `cPriorCsumo`. Códigos sem nenhuma linha em `tCurvaPrvdr` SHALL ser ignorados e contados, sem erro. O processor MUST NOT inserir, alterar ou apagar nada em `tCurvaMercd` ou `tCurvaPrvdr`: só lê `tCurvaPrvdr`, e o cadastro é responsabilidade do serviço de cadastro.

Numa **única transação** por carga, para cada curva de mercado mapeada, o processor SHALL apagar as linhas de `tBtrsCurvaPrimr` com `cTickerIndcd` = nome da curva e `dBaseReft` = data-base e inserir uma linha por vértice do código:

| Coluna | Valor |
|---|---|
| `cldtfdUnic` | `NEXT VALUE FOR seq_tbtrscurvaprimr_cidtfdunic` |
| `cTickerIndcd` | nome da curva de mercado (de `tCurvaPrvdr`) |
| `dBaseReft` | data-base |
| `cDiaCorri` | dias corridos |
| `cDiaUtil` | dias úteis |
| `vPrecoTx` | valor em `BigDecimal` |
| `vFatorAcum`, `vFatorDia` | nulo |

A FK de `tBtrsCurvaPrimr.cTickerIndcd` fica satisfeita porque `tCurvaPrvdr.cTickerIndcd` já aponta para uma curva existente. Depois das inserções e antes do commit, o processor SHALL conferir, por curva, que a quantidade de linhas gravadas é igual à quantidade de vértices do código no arquivo. Qualquer falha MUST desfazer a transação inteira.

#### Scenario: Carga com 110 códigos e 5 mapeados
- **WHEN** a carga de `2026-09-14` tem 110 códigos e só `PRE`, `DCL`, `DPL`, `INP` e `PTX` aparecem em `tCurvaPrvdr` (fonte `B3`, produto `TS`), mapeados para `DIxPRE`, `Cupom limpo de dólar`, `Cupom Limpo DI X IPCA`, `IBOVESPA` e `PTAX - USD`
- **THEN** `tBtrsCurvaPrimr` passa a ter 278 linhas para cada uma dessas 5 curvas em `2026-09-14`, com `vPrecoTx` igual ao publicado, e os outros 105 códigos são ignorados

#### Scenario: Código sem mapeamento
- **WHEN** a carga tem o código `SLP`, que não aparece em `tCurvaPrvdr`
- **THEN** o `SLP` é ignorado e contado no log, e nada é inserido em `tCurvaMercd`, `tCurvaPrvdr` ou `tBtrsCurvaPrimr` para ele

#### Scenario: Um código para duas curvas
- **WHEN** `tCurvaPrvdr` liga o código `PRE` às curvas `DIxPRE` e `DI_MERCADO`
- **THEN** as duas curvas recebem os 278 vértices do `PRE` em `tBtrsCurvaPrimr`

### Requirement: Aviso ao engine depois do commit
Depois do commit, o processor SHALL chamar `POST {processor.engine.url}/api/v1/cargas` (spec `curve-load-trigger` do change `engine-modelos-curva`) com o corpo:

```json
{ "idCarga": "B3-TS-20260914-1a2b3c4d5e6f", "fonte": "B3", "produto": "TS", "dataBase": "2026-09-14", "linhasPorCodigo": { "PRE": 278, "DCL": 278 } }
```

`linhasPorCodigo` SHALL ter uma entrada por código gravado em alguma curva, com a quantidade de vértices do código no arquivo. A chamada SHALL levar os cabeçalhos `Authorization: Bearer` (token do Entra ID por client credentials, escopo `processor.engine.escopo`, papel `Curvas.Processor`) e `X-Correlation-Id`, e ser feita pelo endereço do serviço do engine (atrás do balanceador do Azure), com tempo limite de `processor.engine.timeout-segundos` (padrão 150, maior que o tempo limite do webhook no engine). Se nenhum código foi gravado, o aviso MUST NOT ser feito, e o evento é registrado. Resposta 2xx confirma a mensagem. Erro de rede, tempo esgotado, 409 `CONSTRUCAO_EM_ANDAMENTO`, 429 e 5xx SHALL seguir a política de repetição: o aviso é idempotente, e o engine devolve `EXISTENTE` para o que já construiu. Outro 4xx MUST ser registrado como falha definitiva, com a resposta do engine. Os vértices já gravados permanecem gravados. O resultado de cada curva devolvido pelo engine SHALL constar do log da carga; uma curva que o engine não conseguiu construir é resultado do engine (tem alerta próprio lá) e não falha a carga no processor.

#### Scenario: Engine fora por poucos minutos
- **WHEN** o engine fica fora por 3 minutos depois do commit da carga
- **THEN** o processor registra `AVISO_ATRASADO` aos 2 minutos, repete o aviso até o engine voltar, e o aviso é aceito

#### Scenario: Tempo esgotado com construção em andamento
- **WHEN** o aviso estoura o tempo limite enquanto o engine ainda constrói, e a repetição cai em outra instância e recebe 409 `CONSTRUCAO_EM_ANDAMENTO`
- **THEN** o processor repete de novo e recebe 200 com as curvas como `EXISTENTE`

#### Scenario: Janela de aviso esgotada
- **WHEN** o engine continua fora depois de 10 minutos de repetição
- **THEN** o processor registra `CARGA_FALHOU` com o estado `GRAVADA_SEM_AVISO`, os vértices continuam em `tBtrsCurvaPrimr`, e republicar a data pela rota do conector só repete a gravação (idêntica) e o aviso

### Requirement: Idempotência e republicação
Reprocessar a mesma carga (mesmo `idCarga`) SHALL regravar as mesmas linhas e repetir o aviso, que o engine trata como repetido. Uma carga nova para a mesma data-base (`idCarga` diferente, arquivo republicado pela B3) SHALL substituir as linhas das curvas mapeadas; recalcular curvas já construídas continua sendo decisão do engine. A recuperação de qualquer falha definitiva SHALL ser republicar a data pela rota de republicação do conector (spec `b3-taxaswap-publicacao`).

#### Scenario: Curva ligada depois da carga
- **WHEN** a carga de `2026-09-14` já foi gravada e avisada, e depois o cadastro liga o código `SLP` a uma curva nova em `tCurvaPrvdr`
- **THEN** republicar a data pela rota do conector gera o mesmo `idCarga`, o processor grava também a curva nova, e o engine constrói só as curvas que ainda não têm pontos

#### Scenario: Mensagem entregue duas vezes
- **WHEN** o Kafka entrega a mesma mensagem duas vezes
- **THEN** `tBtrsCurvaPrimr` termina com as mesmas linhas, sem duplicar, e o engine recebe o mesmo `idCarga` duas vezes

### Requirement: Política de repetição e falha definitiva
São **transitórias**: erro de rede ou tempo esgotado no Blob, no banco ou no engine; resposta 408, 429 ou 5xx do Blob; perda de conexão, deadlock ou tempo esgotado de comando no SQL Server; e 409 `CONSTRUCAO_EM_ANDAMENTO`, 429 e 5xx do engine. São **definitivas**: mensagem inválida, arquivo inexistente no Blob (404), divergência de tamanho ou de hash, arquivo rejeitado pela validação, violação de restrição no banco (FK, chave) e qualquer outro 4xx do engine.

Falhas transitórias SHALL ser repetidas por janela de tempo, com espera exponencial a partir de 1 segundo, dobrando a cada tentativa até o máximo de 60 segundos, mais variação aleatória de até 10%:
- leitura do Blob e gravação no banco: por até `processor.repeticao.gravacao-minutos` (padrão 5);
- aviso ao engine: por até `processor.repeticao.aviso-minutos` (padrão 10).

As curvas devem estar construídas em minutos depois da carga. Por isso, se o aviso não for aceito em `processor.repeticao.alerta-aviso-minutos` (padrão 2) desde o commit, o processor SHALL registrar `AVISO_ATRASADO` com nível `ERRO` e métrica, para alerta, e continuar repetindo até o fim da janela. Como há uma carga B3 por dia, segurar o consumidor durante a janela não atrasa outras cargas. Cada repetição SHALL gerar log de nível `AVISO`.

Esgotada a janela, ou numa falha definitiva, o processor SHALL registrar o evento `CARGA_FALHOU` com nível `ERRO` e métrica, contendo `idCarga`, data-base, motivo, etapa (`LEITURA`, `VALIDACAO`, `GRAVACAO` ou `AVISO`), estado (`NAO_GRAVADA` ou `GRAVADA_SEM_AVISO`), quantidade de tentativas e horário de Brasília, e então confirmar a mensagem. Nenhum tópico novo SHALL ser criado: a mensagem não precisa ser guardada, porque a carga está arquivada no Blob e pode ser republicada pela rota do conector com o mesmo `idCarga`.

#### Scenario: Banco indisponível
- **WHEN** o banco não responde durante a gravação por 5 minutos
- **THEN** nada fica gravado, o processor registra `CARGA_FALHOU` com etapa `GRAVACAO` e estado `NAO_GRAVADA`, e a carga é recuperada republicando a data pela rota do conector

### Requirement: Substituição do caminho antigo
O tratamento atual do tópico `tp-event-b3-curve` (`B3KafkaConsumer` lendo uma mensagem por vértice em `B3CurveRaw`), a entidade `B3CurveRawEntity` (tabela `mkt.B3CurveRaw`), o repositório e o adaptador correspondentes MUST ser substituídos pelo consumo desta spec no mesmo tópico. Nenhum componente do processor SHALL gravar vértices da B3 em outra tabela que não `tBtrsCurvaPrimr`.

#### Scenario: Sem gravação em mkt.B3CurveRaw
- **WHEN** uma carga B3 é processada
- **THEN** nenhuma linha é gravada em `mkt.B3CurveRaw`

### Requirement: Log e horário
Cada carga SHALL gerar log JSON com `correlationId`, `idCarga`, origem, usuário (quando houver), data-base, linhas lidas, códigos gravados (com as curvas de mercado de cada um), códigos ignorados, códigos inválidos (com linha e motivo), duração de cada etapa e resultado do aviso, com instantes no horário de Brasília (`America/Sao_Paulo`), sem depender do fuso do servidor. O processor SHALL publicar as métricas `processor_b3_carga_total` (tag `resultado`: `SUCESSO`, `SUCESSO_COM_CODIGOS_INVALIDOS`, `FALHOU`), `processor_b3_carga_falhou_total` (tags `etapa` e `estado`), `processor_b3_aviso_atrasado_total` e o histograma `processor_b3_carga_duracao_segundos`.

#### Scenario: Log de carga
- **WHEN** uma carga é processada com sucesso
- **THEN** o log tem um evento com o `idCarga`, os 5 códigos gravados com as curvas de cada um, os 105 ignorados, nenhum inválido e a resposta do engine

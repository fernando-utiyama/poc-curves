## ADDED Requirements

### Requirement: Upsert idempotente de market data

O processor SHALL gravar cada ponto canônico por upsert sobre a chave `(fonte, conjunto_dados, data_referencia, chave_instrumento)`. Processar o mesmo evento mais de uma vez SHALL deixar o banco em estado idêntico ao de um único processamento.

#### Scenario: Mesmo evento consumido duas vezes

- **WHEN** o mesmo `eventId` é consumido duas vezes
- **THEN** o conjunto de linhas em `ponto_dado_mercado` SHALL ser idêntico ao de um único consumo, sem duplicata

#### Scenario: Replay do tópico inteiro

- **WHEN** o grupo de consumo é reposicionado ao início e todo o histórico é reprocessado
- **THEN** o estado final do banco SHALL ser idêntico ao anterior ao replay

### Requirement: Reconhecimento de evento já processado

O processor SHALL reconhecer um `eventId` já processado com sucesso e SHALL ignorar o lote correspondente, sem regravar pontos e sem emitir evento derivado adicional.

#### Scenario: Evento repetido é reconhecido

- **WHEN** um `eventId` já registrado como processado é recebido novamente
- **THEN** o processor SHALL registrar a repetição e SHALL NOT reexecutar a gravação

### Requirement: Transação por bloco e consolidação do lote

Cada bloco recebido SHALL ser gravado em uma transação própria e atômica. O `lote_ingestao` SHALL ser criado no primeiro bloco e consolidado quando a quantidade de blocos recebidos igualar o `totalBlocos` declarado. A ordem de chegada dos blocos MUST NOT afetar a consolidação.

#### Scenario: Blocos fora de ordem

- **WHEN** os blocos de um lote chegam fora da ordem de sequência
- **THEN** o lote SHALL ser consolidado corretamente quando todos tiverem chegado

#### Scenario: Falha em um bloco

- **WHEN** a gravação de um bloco falha
- **THEN** apenas aquele bloco SHALL ser revertido, os demais SHALL permanecer, e o lote SHALL continuar aberto

### Requirement: Lote incompleto não gera construção

Enquanto a contagem de blocos recebidos for menor que `totalBlocos`, o lote SHALL permanecer aberto e o processor MUST NOT emitir o evento de dado normalizado para aquele lote. Passado o tempo limite configurado sem completar, o lote SHALL ser marcado como incompleto, nomeando as sequências faltantes.

#### Scenario: Feeder morreu no meio do arquivo

- **WHEN** parte dos blocos chega e o restante nunca chega
- **THEN** o lote SHALL ser marcado como incompleto após o tempo limite, e nenhum pedido de construção SHALL ser gerado a partir dele

#### Scenario: Insumo parcial nunca vira curva

- **WHEN** um lote está incompleto
- **THEN** o evento de dado normalizado MUST NOT ser publicado, ainda que os pontos já gravados sejam válidos

#### Scenario: Lote completo

- **WHEN** todos os blocos declarados são gravados com sucesso
- **THEN** o lote SHALL ser consolidado e o evento de dado normalizado SHALL ser publicado uma única vez

### Requirement: Consumo isolado por faixa

O processor SHALL consumir as faixas de rotina, prioritária e massa por listeners independentes, com grupos de consumo e pools de thread próprios. Bloqueio em uma faixa MUST NOT impedir o consumo das demais.

#### Scenario: Rotina travada, prioritária livre

- **WHEN** o consumo da faixa de rotina fica bloqueado por uma mensagem em retentativa
- **THEN** a faixa prioritária SHALL continuar sendo consumida normalmente, no mesmo serviço

#### Scenario: Faixa de massa pausada

- **WHEN** a faixa de massa é pausada
- **THEN** rotina e prioritária SHALL continuar operando sem alteração

#### Scenario: Mesmo conteúdo em faixas diferentes

- **WHEN** o mesmo lote é publicado na rotina e na faixa prioritária
- **THEN** o estado final do banco SHALL ser idêntico ao de um único processamento, sem duplicata

### Requirement: Avanço obrigatório do offset

Todo caminho de tratamento de erro SHALL terminar em sucesso ou em envio à dead-letter com o offset confirmado. O processor MUST NOT configurar retentativa sem recuperador terminal, e MUST NOT executar chamada externa sem timeout.

#### Scenario: Mensagem que falha sempre

- **WHEN** um bloco falha em todas as tentativas permitidas
- **THEN** ele SHALL ir para a dead-letter, o offset SHALL avançar, e o consumo SHALL prosseguir

#### Scenario: Falha de desserialização

- **WHEN** uma mensagem não pode ser desserializada
- **THEN** ela SHALL ser encaminhada à dead-letter sem laço infinito, e o offset SHALL avançar

#### Scenario: Teto de tempo na retentativa

- **WHEN** a retentativa de uma falha transitória é iniciada
- **THEN** ela SHALL respeitar um teto de tempo além do teto de tentativas, derivado do tempo restante até o horário limite de publicação

### Requirement: Transacionalidade do lote

Todos os pontos de um mesmo evento, junto com o registro do lote de ingestão, SHALL ser gravados de forma atômica. Falha em qualquer ponto SHALL reverter o lote inteiro, e o banco MUST NOT ficar com insumo parcialmente gravado.

#### Scenario: Falha no meio da gravação

- **WHEN** a gravação falha após alguns pontos terem sido escritos
- **THEN** a transação SHALL ser revertida por completo, e nenhum ponto do lote SHALL permanecer no banco

#### Scenario: Lote muito grande

- **WHEN** o número de pontos excede o limite configurado por transação
- **THEN** a gravação SHALL ser dividida em blocos atômicos, e o lote de ingestão SHALL consolidar as contagens de todos os blocos

### Requirement: Registro do lote de ingestão

Cada evento processado SHALL gerar um registro em `lote_ingestao` contendo `correlationId`, `eventId`, fonte, dataset, data de referência, hash do payload, quantidade de pontos recebidos, quantidade gravada e a lista de divergências.

#### Scenario: Lote registrado

- **WHEN** um evento é processado com sucesso
- **THEN** SHALL existir um registro de lote com as contagens e o hash do payload

#### Scenario: Rastrear a origem de um ponto

- **WHEN** um ponto de market data é inspecionado
- **THEN** SHALL ser possível chegar, pelo lote, ao evento, ao `correlationId` e ao run que o originaram

### Requirement: Detecção e registro de divergência de valor

Quando um ponto já existente é regravado com valor diferente, o processor SHALL atualizar o valor e SHALL registrar a divergência no lote, com o valor anterior, o valor novo e o identificador do instrumento. Divergência MUST NOT ser aplicada silenciosamente.

#### Scenario: Fonte republica dado corrigido

- **WHEN** um ponto já gravado chega com valor diferente
- **THEN** o valor novo SHALL prevalecer, e a divergência SHALL constar no registro do lote

#### Scenario: Regravação com valor idêntico

- **WHEN** um ponto já gravado chega com o mesmo valor
- **THEN** nenhuma divergência SHALL ser registrada

### Requirement: Emissão do evento de dado normalizado após o commit

O processor SHALL publicar `marketdata.normalized.v1` somente após o commit da transação do lote, preservando o `correlationId` do evento de origem e informando fonte, dataset, data de referência e quantidade de pontos gravados.

#### Scenario: Publicação após persistência

- **WHEN** o lote é confirmado no banco
- **THEN** o evento de dado normalizado SHALL ser publicado, e o insumo SHALL estar visível para leitura no momento em que o consumidor o receber

#### Scenario: Falha antes do commit

- **WHEN** a transação falha
- **THEN** nenhum evento de dado normalizado SHALL ser publicado

### Requirement: Retentativa e dead-letter na persistência

Falha transitória de banco SHALL ser retentada com backoff exponencial até o limite configurado. Esgotado o limite, o evento SHALL ir para a dead-letter com motivo e contexto, sem perda silenciosa.

#### Scenario: Banco temporariamente indisponível

- **WHEN** o banco fica indisponível durante o processamento e volta em seguida
- **THEN** o processor SHALL retentar e concluir com sucesso, sem intervenção manual

#### Scenario: Falha persistente de banco

- **WHEN** o limite de retentativas é esgotado
- **THEN** o evento SHALL ir para a dead-letter com o motivo da falha, e o consumo dos demais eventos SHALL continuar

### Requirement: Fronteira de escrita do processor

O processor MUST NOT escrever em nenhuma tabela além de `ponto_dado_mercado` e `lote_ingestao`, e MUST NOT acessar fonte externa de mercado.

#### Scenario: Tentativa de escrita fora da fronteira

- **WHEN** o processor tenta gravar em uma tabela de curva
- **THEN** o teste de fronteira SHALL falhar o build, e a credencial de banco do serviço SHALL NOT conceder essa permissão

## ADDED Requirements

### Requirement: Envelope comum de evento

Todo evento publicado em Kafka SHALL usar o envelope comum com os campos obrigatórios `eventId` (UUID v4), `correlationId`, `source`, `dataset`, `referenceDate` (ISO-8601, data), `producedAt` (ISO-8601, instante UTC), `schemaVersion` e `payload`. Campos numéricos com política de arredondamento de mercado MUST ser serializados como string, nunca como número JSON.

#### Scenario: Evento válido

- **WHEN** um produtor publica um evento com todos os campos do envelope preenchidos e payload conforme o schema do dataset
- **THEN** o evento SHALL ser aceito e o consumidor SHALL processá-lo

#### Scenario: Envelope incompleto

- **WHEN** um evento chega sem `eventId`, `correlationId`, `source`, `dataset`, `referenceDate` ou `schemaVersion`
- **THEN** o consumidor SHALL rejeitá-lo para a dead-letter com o motivo `INVALID_ENVELOPE`, sem tentar processar o payload

#### Scenario: Taxa serializada como número

- **WHEN** um payload serializa uma taxa como número JSON em vez de string
- **THEN** a validação de schema SHALL falhar, e o evento SHALL ser rejeitado

### Requirement: Catálogo de tópicos

A plataforma SHALL usar os tópicos abaixo e as dead-letters correspondentes com sufixo `.dlq`. Nenhum componente SHALL criar tópico fora deste catálogo sem alteração desta especificação.

A ingestão SHALL ser dividida em três faixas, cada uma com tópico e grupo de consumo próprios:

| Faixa | Tópico | Origem |
|---|---|---|
| Rotina | `marketdata.rotina.v1` | agendamento diário |
| Prioritária | `marketdata.prioritaria.v1` | disparo manual pela tela |
| Massa | `marketdata.massa.v1` | carga histórica |

Além delas: `marketdata.normalized.v1` e `curve.published.v1` (publicado exclusivamente pelo `curve-processor`, para curvas `IMPORTADA`/`CARREGADA` — o `curve-engine` não usa Kafka; despacho de construção e conclusão trafegam por REST/callback, ver `platform-topology`).

#### Scenario: Bootstrap do ambiente

- **WHEN** o ambiente local é inicializado
- **THEN** todos os tópicos do catálogo SHALL existir com o número de partições e a retenção declarados, antes de qualquer produtor iniciar

#### Scenario: Faixas isoladas

- **WHEN** uma mensagem trava o consumo da faixa de rotina
- **THEN** as faixas prioritária e de massa SHALL continuar sendo consumidas normalmente

#### Scenario: Disparo manual sempre na faixa prioritária

- **WHEN** uma ingestão é disparada manualmente
- **THEN** ela SHALL ser publicada na faixa prioritária, independentemente de existir ou não algo travado na rotina

#### Scenario: Carga histórica na faixa de massa

- **WHEN** uma carga histórica é executada
- **THEN** ela SHALL usar a faixa de massa, que pode ser pausada sem afetar as demais

#### Scenario: Tópico não catalogado

- **WHEN** um componente tenta produzir em um tópico que não está no catálogo
- **THEN** a criação automática SHALL estar desabilitada no broker e a produção SHALL falhar

### Requirement: Chave de partição determinística

A chave de partição de todo evento SHALL ser `source|dataset|referenceDate`, garantindo ordenação total por dataset e data de referência.

#### Scenario: Ordenação preservada

- **WHEN** dois eventos do mesmo `source`, `dataset` e `referenceDate` são publicados em sequência
- **THEN** ambos SHALL cair na mesma partição e SHALL ser consumidos na ordem de produção

#### Scenario: Paralelismo entre datasets

- **WHEN** eventos de datasets diferentes são publicados simultaneamente
- **THEN** eles SHALL poder ser processados em paralelo por consumidores distintos do mesmo grupo

### Requirement: Fonte de carga manual

O contrato de evento SHALL aceitar `source` igual a `MANUAL`, identificando conteúdo submetido por um usuário em vez de adquirido de provedor externo. Evento de origem `MANUAL` SHALL usar a faixa prioritária e SHALL carregar o autor e a justificativa da carga.

#### Scenario: Carga manual publicada

- **WHEN** um usuário submete uma curva por arquivo
- **THEN** o evento SHALL declarar `source` igual a `MANUAL`, na faixa prioritária, com autor e justificativa

#### Scenario: Carga manual sem justificativa

- **WHEN** um evento de origem `MANUAL` é publicado sem justificativa
- **THEN** a validação de schema SHALL falhar e o evento SHALL NOT ser enviado

### Requirement: Identificação de lote e bloco

Quando o conteúdo de uma aquisição é dividido em blocos, cada evento SHALL declarar `loteId`, `sequencia` e `totalBlocos`, além do envelope comum. O `loteId` SHALL ser determinístico a partir da fonte, do conjunto de dados, da data de referência e do hash do conteúdo adquirido.

#### Scenario: Blocos de um mesmo arquivo

- **WHEN** um arquivo é dividido em blocos
- **THEN** todos os eventos SHALL carregar o mesmo `loteId`, sequências distintas e o mesmo `totalBlocos`

#### Scenario: Reaquisição do mesmo conteúdo

- **WHEN** o mesmo arquivo é adquirido novamente
- **THEN** o `loteId` e os `eventId` SHALL ser idênticos aos da aquisição anterior

#### Scenario: Bloco sem identificação de lote

- **WHEN** um evento de bloco chega sem `loteId`, `sequencia` ou `totalBlocos`
- **THEN** ele SHALL ser rejeitado para a dead-letter com motivo `INVALID_ENVELOPE`

### Requirement: Avanço obrigatório do offset

Nenhum caminho de tratamento de erro SHALL terminar sem avançar o offset. O processamento SHALL concluir em sucesso, ou a mensagem SHALL ser enviada à dead-letter **e o offset confirmado**. Retentativa sem terminador MUST NOT existir em nenhum consumidor.

#### Scenario: Mensagem que sempre falha

- **WHEN** uma mensagem falha em todas as tentativas permitidas
- **THEN** ela SHALL ir para a dead-letter, o offset SHALL ser confirmado, e a partição SHALL prosseguir para as mensagens seguintes

#### Scenario: Retentativa infinita proibida

- **WHEN** um consumidor é configurado com retentativa sem limite e sem recuperador terminal
- **THEN** a configuração SHALL ser considerada inválida e o teste de conformidade SHALL falhar

#### Scenario: Partição nunca fica presa

- **WHEN** qualquer combinação de falhas ocorre em uma partição
- **THEN** o offset confirmado SHALL avançar dentro do tempo máximo derivado da política de retentativa

### Requirement: Limites de tempo no consumo

Todo consumidor SHALL declarar quantidade máxima de registros por poll e intervalo máximo entre polls dimensionados de forma que o processamento de um poll conclua com folga dentro do intervalo. Toda chamada externa feita durante o processamento SHALL ter timeout, sempre menor que o intervalo máximo entre polls.

#### Scenario: Processamento dentro do intervalo

- **WHEN** um poll com a quantidade máxima de registros é processado
- **THEN** a duração SHALL ficar com folga abaixo do intervalo máximo configurado, evitando rebalance por consumidor considerado morto

#### Scenario: Chamada externa sem timeout

- **WHEN** um consumidor executa chamada externa sem timeout declarado
- **THEN** a revisão SHALL rejeitar a implementação, porque uma chamada pendurada trava a partição sem que nenhum mecanismo do broker a resgate

#### Scenario: Retentativa limitada também no tempo

- **WHEN** a retentativa de uma falha transitória é configurada
- **THEN** SHALL existir teto de tempo além do teto de tentativas, de forma que a mensagem deixe a partição em prazo conhecido

### Requirement: Idempotência do consumidor

Todo consumidor SHALL ser idempotente em relação a `eventId`, e SHALL produzir o mesmo estado final quando o mesmo evento é entregue mais de uma vez. A entrega é garantida como *at-least-once*, e o consumidor MUST NOT depender de entrega única.

#### Scenario: Entrega duplicada

- **WHEN** o mesmo `eventId` é entregue duas vezes ao consumidor
- **THEN** o segundo processamento SHALL ser reconhecido como duplicado e SHALL NOT alterar o estado nem emitir evento derivado adicional

### Requirement: Dead-letter e retentativa

Falha transitória SHALL ser retentada com backoff exponencial até o limite configurado; esgotado o limite, ou em falha permanente de validação, o evento SHALL ser enviado ao tópico `.dlq` correspondente, acompanhado do motivo, do `correlationId` e do carimbo de tempo da falha. Evento em dead-letter MUST NOT ser descartado silenciosamente.

#### Scenario: Falha transitória

- **WHEN** o processamento falha por indisponibilidade temporária de dependência
- **THEN** o consumidor SHALL retentar com backoff exponencial e SHALL processar com sucesso quando a dependência voltar, sem intervenção

#### Scenario: Falha permanente

- **WHEN** o payload é estruturalmente inválido para o schema declarado
- **THEN** o evento SHALL ir direto para a dead-letter sem retentativa, com motivo `SCHEMA_VALIDATION_FAILED`

#### Scenario: Reprocessamento a partir da dead-letter

- **WHEN** um operador reenvia um evento da dead-letter após a causa ser corrigida
- **THEN** o fluxo SHALL processá-lo normalmente, preservando o `correlationId` original

### Requirement: Retenção e cabeçalhos da dead-letter

Todo tópico de dead-letter SHALL ter retenção **maior** que a do tópico de origem, porque mensagem em dead-letter espera intervenção humana. Toda mensagem enviada a uma dead-letter SHALL carregar, em cabeçalhos, o motivo, o detalhe, o `correlationId`, o `eventId`, o tópico, a partição e o offset de origem, o instante da falha, a contagem de tentativas, o grupo de consumo e a versão da aplicação que falhou. O payload original SHALL ser preservado sem alteração.

#### Scenario: Retenção suficiente para intervenção

- **WHEN** as retenções são configuradas
- **THEN** cada dead-letter SHALL reter por período maior que o do seu tópico de origem

#### Scenario: Contexto suficiente para reprocessar

- **WHEN** uma mensagem em dead-letter é inspecionada
- **THEN** SHALL ser possível identificar a causa, a origem exata e a versão da aplicação que falhou, sem consultar outra fonte

#### Scenario: Payload intocado

- **WHEN** uma mensagem é enviada à dead-letter
- **THEN** o payload SHALL ser byte a byte igual ao recebido, para que o reprocessamento passe pela mesma validação

### Requirement: Dead-letter por grupo de consumo

Cada grupo de consumo SHALL ter a sua própria dead-letter. Dead-letter MUST NOT ser compartilhada entre consumidores distintos do mesmo tópico.

#### Scenario: Dois consumidores do mesmo tópico

- **WHEN** dois grupos de consumo diferentes consomem o mesmo tópico e ambos falham
- **THEN** cada falha SHALL ir para a dead-letter do seu próprio grupo, mantendo a responsabilidade identificável

### Requirement: Falha de desserialização não bloqueia o consumo

Mensagem que falha na desserialização SHALL ser encaminhada à dead-letter, e o consumidor SHALL avançar o offset. O consumidor MUST NOT entrar em laço infinito sobre a mesma mensagem.

#### Scenario: Mensagem malformada

- **WHEN** uma mensagem não pode ser desserializada
- **THEN** ela SHALL ir para a dead-letter e o consumo SHALL prosseguir com as mensagens seguintes

### Requirement: Reprocessamento sempre explícito

O reprocessamento de mensagem em dead-letter SHALL ser acionado explicitamente. Nenhum componente SHALL reenviar mensagem de dead-letter de forma automática ou periódica.

#### Scenario: Sem laço de reenvio

- **WHEN** uma mensagem permanece em dead-letter
- **THEN** nenhum componente SHALL republicá-la por conta própria, evitando o ciclo de falha e reenvio

### Requirement: Schemas versionados como código

Os schemas de todos os eventos SHALL residir em `contracts/events/*.schema.json`, versionados no repositório, e SHALL ser validados tanto no produtor quanto no consumidor. A validação de contrato SHALL fazer parte da suíte de testes.

#### Scenario: Produtor divergente do schema

- **WHEN** um produtor é alterado para emitir um campo que não consta no schema
- **THEN** o teste de contrato SHALL falhar no build, antes de o código chegar ao ambiente

### Requirement: Evolução compatível de contrato

Alteração de schema SHALL ser compatível para trás: apenas adição de campos opcionais. Alteração incompatível SHALL criar um tópico com versão incrementada (`.v2`), e os dois tópicos SHALL coexistir durante a migração, com o consumidor antigo continuando a operar.

#### Scenario: Campo novo opcional

- **WHEN** um campo opcional é adicionado ao payload
- **THEN** consumidores existentes SHALL continuar processando os eventos sem alteração de código

#### Scenario: Mudança incompatível

- **WHEN** um campo obrigatório muda de tipo ou é removido
- **THEN** um tópico `.v2` SHALL ser criado, e o `.v1` SHALL permanecer ativo até a migração de todos os consumidores

### Requirement: Extensão para novos feeders

O contrato de evento SHALL acomodar novas fontes (`BLOOMBERG`, `LSEG`) sem alteração do `curve-processor` além do parser específico do dataset. O roteamento no consumidor SHALL se basear em `source` e `dataset`, nunca no formato do payload.

#### Scenario: Fonte nova publica dataset já conhecido

- **WHEN** um feeder novo publica um dataset já mapeado, com `source` diferente
- **THEN** o `curve-processor` SHALL processá-lo com o parser do dataset, sem alteração no roteamento

#### Scenario: Dataset desconhecido

- **WHEN** chega um evento com `dataset` sem parser registrado
- **THEN** o evento SHALL ir para a dead-letter com motivo `UNKNOWN_DATASET`, e o consumidor SHALL continuar processando os demais

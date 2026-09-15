## Purpose

Preservar o arquivo bruto original adquirido de fontes externas (B3, ANBIMA) em armazenamento de objeto, organizado de forma previsível por fonte e data, para servir como trilha de auditoria/reprocessamento e como fonte de conteúdo para a ingestão — substituindo o transporte do conteúdo embutido em mensagens Kafka.

## ADDED Requirements

### Requirement: Gravação do arquivo bruto em blob storage

Todo feeder de arquivo (dataset cujo conteúdo vem de um arquivo baixado, não de uma resposta de API já estruturada por vértice) SHALL gravar o conteúdo bruto adquirido — já desempacotado do(s) container(es) de compactação, antes de qualquer interpretação de formato — em blob storage, no caminho `<fonte>/<data-referencia>/<nome-arquivo-original>`, antes de publicar qualquer evento.

#### Scenario: Gravação bem-sucedida

- **WHEN** um feeder de arquivo adquire com sucesso o conteúdo de uma fonte externa para uma data de referência
- **THEN** o conteúdo bruto SHALL ser gravado em blob storage no caminho `<fonte>/<data-referencia>/<nome-arquivo-original>` antes de qualquer publicação no tópico

#### Scenario: Falha ao gravar no blob

- **WHEN** a gravação no blob storage falha (indisponibilidade, permissão, etc.)
- **THEN** o feeder SHALL retornar falha e MUST NOT publicar nenhum evento referenciando um blob que não existe

### Requirement: Publicação por referência, não por conteúdo

O evento publicado por um feeder de arquivo SHALL conter a referência ao blob gravado (container/caminho, hash de conteúdo, tamanho, encoding) em vez do conteúdo bruto embutido. O feeder MUST NOT dividir o conteúdo em múltiplos blocos para transporte — uma aquisição bem-sucedida SHALL resultar em exatamente um evento.

#### Scenario: Um evento por aquisição

- **WHEN** um feeder de arquivo publica o resultado de uma aquisição bem-sucedida
- **THEN** exatamente um evento SHALL ser publicado, referenciando o blob gravado, e MUST NOT conter o conteúdo do arquivo embutido

#### Scenario: Hash de integridade preservado

- **WHEN** o evento é publicado
- **THEN** ele SHALL trazer o hash SHA-256 do conteúdo bruto gravado no blob, permitindo verificar a integridade ponta a ponta sem reler o blob

### Requirement: Leitura do blob pelo consumidor

O curve-processor SHALL buscar o conteúdo do blob referenciado no evento antes de invocar o parser do dataset correspondente, e SHALL verificar que o conteúdo lido bate com o hash declarado no evento antes de processar.

#### Scenario: Leitura e verificação bem-sucedidas

- **WHEN** o processor consome um evento com referência de blob válida
- **THEN** ele SHALL buscar o conteúdo do blob e, com o hash conferido, seguir para o parsing normalmente

#### Scenario: Blob referenciado não existe

- **WHEN** o processor tenta buscar um blob referenciado que não existe (removido, caminho errado)
- **THEN** o evento SHALL ir para a dead-letter com motivo nomeando o blob ausente, sem tentar processar

#### Scenario: Hash não bate

- **WHEN** o conteúdo lido do blob não bate com o hash declarado no evento
- **THEN** o evento SHALL ir para a dead-letter com motivo de integridade, e o processor MUST NOT prosseguir com o parsing

### Requirement: Caminho de blob previsível e determinístico

O caminho de um blob SHALL ser determinístico a partir de (fonte, data de referência, nome do arquivo original), permitindo localizar e reprocessar manualmente um arquivo já adquirido sem depender do evento Kafka original.

#### Scenario: Reaquisição da mesma data

- **WHEN** um arquivo da mesma fonte e data de referência é adquirido novamente (revisão intraday, reprocessamento)
- **THEN** o blob SHALL ser sobrescrito no mesmo caminho, preservando a convenção `<fonte>/<data-referencia>/<nome-arquivo-original>`

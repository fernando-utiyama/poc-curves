## ADDED Requirements

### Requirement: Publicação atômica

A publicação de uma curva construída SHALL gravar `versao_curva`, `vertice_curva` e `procedencia_curva` em uma única transação, com a versão no estado `EM_VALIDACAO`. Falha em qualquer parte SHALL reverter tudo, e o banco MUST NOT ficar com versão sem vértices ou sem procedência.

#### Scenario: Publicação bem-sucedida

- **WHEN** a construção conclui e a gravação executa sem erro
- **THEN** a versão, os vértices e a procedência SHALL estar gravados, e a versão SHALL estar no estado `EM_VALIDACAO`, aguardando o gate

#### Scenario: Falha na gravação da procedência

- **WHEN** a gravação da procedência falha
- **THEN** a transação inteira SHALL ser revertida, e nenhuma versão SHALL permanecer publicada

#### Scenario: Falha na gravação dos vértices

- **WHEN** a gravação dos vértices falha no meio
- **THEN** a versão criada SHALL ser revertida junto, sem deixar curva parcial

### Requirement: Promoção após o gate de validação

Uma versão SHALL ser promovida de `EM_VALIDACAO` para `PUBLICADA` apenas após todos os testes bloqueantes de consistência passarem. Versão com teste bloqueante reprovado SHALL passar a `REPROVADA` e MUST NOT ser publicada.

#### Scenario: Promoção

- **WHEN** a bateria de validação aprova a curva
- **THEN** a versão SHALL passar a `PUBLICADA`, a anterior SHALL passar a `SUBSTITUIDA`, e o evento de curva publicada SHALL ser emitido

#### Scenario: Reprovação

- **WHEN** um teste bloqueante falha
- **THEN** a versão SHALL passar a `REPROVADA`, nenhum evento de curva publicada SHALL ser emitido, e a versão anteriormente publicada SHALL permanecer publicada e inalterada

#### Scenario: Versão em validação não é vigente

- **WHEN** uma consulta de curva vigente ocorre com uma versão em `EM_VALIDACAO`
- **THEN** SHALL ser retornada a versão anterior publicada

#### Scenario: Substituição só na promoção

- **WHEN** uma versão é gravada em `EM_VALIDACAO`
- **THEN** a versão anterior MUST NOT ser marcada como substituída antes da promoção

### Requirement: Numeração incremental de versão

Cada publicação para o mesmo `(definicao_curva_id, data_referencia, momento_curva)` SHALL receber `numero_versao` incremental, começando em um.

#### Scenario: Primeira publicação da data

- **WHEN** a curva é publicada pela primeira vez para uma data e momento
- **THEN** `numero_versao` SHALL ser um

#### Scenario: Republicação

- **WHEN** a mesma curva, data e momento é publicada novamente
- **THEN** `numero_versao` SHALL ser o anterior mais um

### Requirement: Substituição da versão anterior

Ao publicar, a versão anteriormente publicada para o mesmo `(definicao_curva_id, data_referencia, momento_curva)` SHALL passar ao estado `SUBSTITUIDA`, na mesma transação. Os vértices da versão substituída MUST NOT ser alterados nem removidos.

#### Scenario: Anterior preservada

- **WHEN** uma nova versão é publicada
- **THEN** a anterior SHALL ficar `SUBSTITUIDA` com todos os seus vértices intactos e consultáveis

#### Scenario: Uma única versão publicada por chave

- **WHEN** o banco é consultado por `(definicao_curva_id, data_referencia, momento_curva)`
- **THEN** SHALL existir no máximo uma versão no estado `PUBLICADA`

### Requirement: Procedência obrigatória e completa

A procedência gravada SHALL identificar a execução, o número de versão da definição aplicada, o modelo de construção usado, o checksum do modelo quando ele for Groovy, as referências dos insumos consumidos, o hash do conjunto de insumos e a versão do motor. Versão sem procedência completa MUST NOT chegar a `PUBLICADA`.

#### Scenario: Rastrear a origem de um vértice

- **WHEN** a procedência de uma curva construída é consultada
- **THEN** a resposta SHALL permitir identificar a execução, os insumos, a versão da definição e o modelo que produziram aqueles números

#### Scenario: Modelo Groovy identificado pelo que rodou

- **WHEN** a curva foi construída por um modelo Groovy
- **THEN** a procedência SHALL registrar o checksum do código efetivamente executado, e não apenas o identificador do modelo

### Requirement: Emissão do evento de curva publicada

Após o commit da publicação, o motor SHALL emitir `curve.published.v1` preservando o `correlationId` da execução, identificando curva, data de referência, momento e identificador da versão publicada. Nenhum evento SHALL ser emitido se a transação falhar.

#### Scenario: Evento após commit

- **WHEN** a publicação é confirmada no banco
- **THEN** o evento SHALL ser emitido, e a versão SHALL estar visível para leitura quando um consumidor o receber

#### Scenario: Contrato idêntico ao da curva importada

- **WHEN** um assinante recebe o evento
- **THEN** ele MUST NOT conseguir distinguir pelo formato do evento se a curva foi construída ou importada

### Requirement: Publicação restrita ao modo de origem

O motor SHALL publicar exclusivamente para definições com modo de origem `BOOTSTRAPPED`. Tentativa de publicar para definição `IMPORTED` SHALL ser recusada nomeando a definição e o modo.

#### Scenario: Recusa para curva importada

- **WHEN** o motor tenta publicar versão para uma definição `IMPORTED`
- **THEN** a operação SHALL ser recusada, e a credencial de banco do serviço SHALL reforçar a restrição

### Requirement: Publicação idempotente por execução

Reprocessar a mesma execução após uma falha parcial MUST NOT criar duas versões para o mesmo resultado. A publicação SHALL reconhecer que a execução já publicou e não repetir.

#### Scenario: Retentativa após falha de rede pós-commit

- **WHEN** a transação já foi confirmada mas o evento não chegou a ser emitido, e a execução é retentada
- **THEN** o motor SHALL reconhecer a publicação existente, reemitir o evento e MUST NOT criar versão duplicada

### Requirement: Estado final da execução

Ao concluir a publicação, a execução SHALL ser marcada como concluída com sucesso, registrando o identificador da versão publicada e a duração. Em caso de falha, SHALL registrar o estado de falha com código e mensagem.

#### Scenario: Sucesso visível na tela

- **WHEN** a publicação conclui
- **THEN** a execução SHALL registrar sucesso e apontar para a versão publicada, visível no monitoramento

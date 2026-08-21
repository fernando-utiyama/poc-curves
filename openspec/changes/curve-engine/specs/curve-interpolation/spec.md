## ADDED Requirements

### Requirement: Interpolação síncrona sobre versão publicada

O motor SHALL expor uma operação síncrona que retorna a taxa e o fator de desconto para um prazo arbitrário, a partir dos vértices de uma versão de curva publicada. A operação MUST NOT reconstruir a curva.

#### Scenario: Prazo entre vértices

- **WHEN** é solicitado um prazo que fica entre dois vértices da curva publicada
- **THEN** a resposta SHALL trazer o valor interpolado com o interpolador declarado na definição, e SHALL identificar a versão de curva usada

#### Scenario: Prazo exatamente sobre um vértice

- **WHEN** o prazo solicitado coincide com um vértice
- **THEN** a resposta SHALL trazer o valor do vértice, sem passar por interpolação

#### Scenario: Reconstrução proibida

- **WHEN** a interpolação é solicitada
- **THEN** o motor MUST NOT executar bootstrap nem modelo de construção para atendê-la

### Requirement: Paridade entre curvas construídas e importadas

A interpolação SHALL funcionar identicamente para curvas com modo de origem `BOOTSTRAPPED` e `IMPORTED`, porque em ambos os casos opera sobre vértices já publicados.

#### Scenario: Interpolar curva importada

- **WHEN** a interpolação é solicitada sobre uma curva importada da B3
- **THEN** ela SHALL ser atendida pelo mesmo caminho e com o mesmo formato de resposta usado para curvas construídas

### Requirement: Seleção do interpolador e da extrapolação pela definição

O interpolador e a política de extrapolação SHALL vir da versão de definição vigente para a data da curva consultada. O chamador MAY solicitar um interpolador alternativo explicitamente, e nesse caso a resposta SHALL indicar que o interpolador usado difere do padrão da curva.

#### Scenario: Padrão da definição

- **WHEN** nenhum interpolador é informado na consulta
- **THEN** o motor SHALL usar o declarado na definição, e a resposta SHALL identificá-lo

#### Scenario: Interpolador alternativo solicitado

- **WHEN** o chamador informa um interpolador diferente do padrão
- **THEN** a resposta SHALL usar o solicitado e SHALL sinalizar explicitamente que não é o padrão da curva

#### Scenario: Interpolador inexistente

- **WHEN** o interpolador solicitado não existe
- **THEN** a operação SHALL falhar nomeando o identificador recebido e listando os disponíveis

### Requirement: Comportamento fora do intervalo de vértices

Prazo fora do intervalo coberto pelos vértices SHALL seguir a política de extrapolação declarada na definição. Sob política estrita, a operação SHALL falhar informando o prazo solicitado e o intervalo disponível. O motor MUST NOT extrapolar silenciosamente.

#### Scenario: Política estrita

- **WHEN** o prazo solicitado ultrapassa o último vértice e a política é estrita
- **THEN** a resposta SHALL ser um erro nomeando o prazo pedido e o intervalo disponível

#### Scenario: Política permissiva

- **WHEN** o prazo ultrapassa o último vértice e a política permite extrapolação
- **THEN** o valor SHALL seguir a política declarada, e a resposta SHALL sinalizar que o ponto é extrapolado

#### Scenario: Prazo anterior ao primeiro vértice

- **WHEN** o prazo solicitado é menor que o do primeiro vértice
- **THEN** o tratamento SHALL seguir a mesma política declarada, com a mesma sinalização

### Requirement: Seleção de versão na consulta

A consulta SHALL aceitar a versão de curva de três formas: sem indicação, retornando a versão publicada corrente; por identificador de versão explícito; ou por `asOf`, retornando a versão que estava publicada naquele instante.

#### Scenario: Versão corrente

- **WHEN** a consulta não informa versão
- **THEN** SHALL ser usada a versão no estado `PUBLICADA` para a curva, data e momento

#### Scenario: Versão histórica por instante

- **WHEN** a consulta informa `asOf` anterior à última republicação
- **THEN** SHALL ser usada a versão vigente naquele instante, ainda que hoje esteja `SUBSTITUIDA`

#### Scenario: Curva sem versão publicada para a data

- **WHEN** não existe versão publicada para a curva na data solicitada
- **THEN** a resposta SHALL informar explicitamente a ausência, e MUST NOT devolver a curva de outra data

### Requirement: Cache invalidado por versão

O resultado da interpolação SHALL ser cacheado com chave contendo curva, data de referência, identificador da versão, prazo e interpolador. Publicar uma versão nova SHALL tornar as entradas anteriores inalcançáveis por construção, sem depender de expiração por tempo nem de invalidação manual.

#### Scenario: Segunda consulta idêntica

- **WHEN** a mesma interpolação é solicitada duas vezes sem mudança de versão
- **THEN** a segunda SHALL ser atendida pelo cache, com resultado idêntico

#### Scenario: Após republicação

- **WHEN** uma versão nova da curva é publicada e a mesma interpolação é solicitada
- **THEN** a resposta SHALL refletir a versão nova, sem exigir limpeza de cache

#### Scenario: Cache indisponível

- **WHEN** o cache está fora do ar
- **THEN** a interpolação SHALL continuar sendo atendida a partir do banco, com degradação de desempenho e não de correção

### Requirement: Consulta em lote de prazos

A operação SHALL aceitar múltiplos prazos em uma única chamada, retornando um resultado por prazo, preservando a ordem solicitada.

#### Scenario: Vários prazos de uma vez

- **WHEN** a tela solicita uma lista de prazos para montar um gráfico
- **THEN** a resposta SHALL conter um resultado por prazo, na ordem pedida, com a mesma versão de curva para todos

#### Scenario: Um prazo inválido no lote

- **WHEN** um dos prazos do lote é inválido ou fora do intervalo sob política estrita
- **THEN** a resposta SHALL identificar o prazo problemático individualmente, sem invalidar os demais resultados

### Requirement: Precisão da resposta

Taxa e fator de desconto SHALL ser calculados como decimal de precisão arbitrária e serializados como texto numérico. A resposta MUST NOT serializar esses valores como número de ponto flutuante.

#### Scenario: Serialização sem perda

- **WHEN** um valor com muitas casas decimais é retornado
- **THEN** o cliente SHALL receber todos os dígitos significativos, sem arredondamento imposto pela serialização

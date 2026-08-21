## ADDED Requirements

### Requirement: Bateria de validação como gate de publicação

Toda curva construída SHALL ser submetida à bateria de validação de consistência enquanto a versão está em `EM_VALIDACAO`, antes de qualquer promoção. A validação MUST NOT ser opcional, e MUST NOT ser executada após a publicação.

#### Scenario: Validação sempre executada

- **WHEN** uma versão é gravada em `EM_VALIDACAO`
- **THEN** a bateria SHALL ser executada antes de qualquer decisão de promoção

#### Scenario: Validação não pode ser pulada

- **WHEN** uma tentativa de promover versão sem resultado de validação ocorre
- **THEN** a promoção SHALL ser recusada

#### Scenario: Falha na execução da própria bateria

- **WHEN** a bateria não consegue ser executada por erro interno
- **THEN** a versão MUST NOT ser promovida, e a execução SHALL falhar nomeando a causa — ausência de validação nunca equivale a aprovação

### Requirement: Classificação dos testes em bloqueante e aviso

Cada teste SHALL ser classificado como `BLOQUEANTE` ou `AVISO`, por definição de curva. Teste bloqueante reprovado SHALL impedir a publicação. Teste de aviso reprovado SHALL permitir a publicação e SHALL registrar o aviso.

#### Scenario: Bloqueante reprovado

- **WHEN** um teste classificado como bloqueante falha
- **THEN** a versão SHALL ser marcada como `REPROVADA` e nenhuma publicação SHALL ocorrer

#### Scenario: Aviso reprovado

- **WHEN** apenas testes de aviso falham
- **THEN** a versão SHALL ser publicada, e o aviso SHALL ser registrado com o teste, a medida observada e o limite aplicado

#### Scenario: Classificação por curva

- **WHEN** o mesmo teste é configurado como bloqueante em uma curva e como aviso em outra
- **THEN** cada curva SHALL seguir a sua própria classificação

### Requirement: Reprecificação dos instrumentos de calibração

A bateria SHALL reprecificar, com a curva construída, os instrumentos que a calibraram, e SHALL comparar o resultado com o preço ou a taxa observados. O erro máximo de reprecificação SHALL ser declarado por definição de curva.

#### Scenario: Curva reprecifica corretamente

- **WHEN** todos os instrumentos de calibração são reprecificados dentro do erro máximo declarado
- **THEN** o teste SHALL ser aprovado, e o erro máximo observado SHALL ser registrado

#### Scenario: Erro de reprecificação acima do limite

- **WHEN** um instrumento é reprecificado com erro acima do limite
- **THEN** o teste SHALL reprovar nomeando o instrumento, o valor observado, o valor reprecificado e o erro

#### Scenario: Erro registrado mesmo em aprovação

- **WHEN** o teste é aprovado
- **THEN** o erro máximo e o instrumento onde ele ocorreu SHALL ser registrados, permitindo acompanhar a deterioração ao longo dos dias

### Requirement: Ausência de arbitragem

A bateria SHALL verificar que os fatores de desconto são estritamente decrescentes com o prazo e que as taxas forward implícitas entre vértices adjacentes respeitam os limites declarados para a curva.

#### Scenario: Fatores de desconto decrescentes

- **WHEN** os fatores de desconto da curva são verificados em ordem de prazo
- **THEN** cada fator SHALL ser menor que o do prazo anterior

#### Scenario: Fator de desconto não decrescente

- **WHEN** o fator de desconto de um prazo é maior ou igual ao do prazo anterior
- **THEN** o teste SHALL reprovar nomeando os dois prazos e os dois fatores, porque isso implica forward fora dos limites

#### Scenario: Forward fora do limite

- **WHEN** a taxa forward implícita entre dois vértices adjacentes viola os limites declarados
- **THEN** o teste SHALL reprovar identificando o intervalo e a taxa implícita

### Requirement: Faixa plausível de taxas

A bateria SHALL verificar que todas as taxas da curva estão dentro da faixa mínima e máxima declaradas para aquela curva.

#### Scenario: Taxa dentro da faixa

- **WHEN** todas as taxas estão dentro da faixa declarada
- **THEN** o teste SHALL ser aprovado

#### Scenario: Taxa fora da faixa

- **WHEN** uma taxa está fora da faixa declarada
- **THEN** o teste SHALL reprovar nomeando o prazo, a taxa e o limite violado

### Requirement: Consistência estrutural da curva

A bateria SHALL verificar que a curva tem vértices, que os prazos são estritamente crescentes e sem duplicata, que nenhum valor é nulo ou não numérico, e que a cobertura de prazos atende ao mínimo declarado para a curva.

#### Scenario: Curva estruturalmente válida

- **WHEN** a curva tem prazos crescentes, sem duplicata e com todos os valores preenchidos
- **THEN** o teste estrutural SHALL ser aprovado

#### Scenario: Cobertura insuficiente

- **WHEN** o prazo mais longo da curva é menor que a cobertura mínima declarada
- **THEN** o teste SHALL reprovar informando a cobertura obtida e a exigida

#### Scenario: Valor ausente ou inválido

- **WHEN** algum vértice tem taxa ou fator de desconto nulo ou não numérico
- **THEN** o teste SHALL reprovar nomeando o prazo afetado

### Requirement: Suavidade da estrutura a termo

A bateria SHALL verificar que a variação da taxa forward entre vértices adjacentes permanece dentro do limite declarado, sinalizando descontinuidades que denunciam bootstrap mal condicionado.

#### Scenario: Curva suave

- **WHEN** a variação de forward entre vértices adjacentes está dentro do limite
- **THEN** o teste SHALL ser aprovado

#### Scenario: Descontinuidade

- **WHEN** a variação entre dois vértices adjacentes excede o limite declarado
- **THEN** o teste SHALL reprovar identificando o intervalo e a variação observada

### Requirement: Variação contra a curva do dia anterior

A bateria SHALL comparar cada vértice com o correspondente da última curva publicada da mesma definição e momento em data anterior, e SHALL sinalizar deslocamentos acima do limite declarado.

Este teste SHALL ser classificável como aviso, porque deslocamento acima do limite pode ser movimento legítimo de mercado.

#### Scenario: Variação dentro do esperado

- **WHEN** todos os vértices variam menos que o limite declarado
- **THEN** o teste SHALL ser aprovado

#### Scenario: Deslocamento acima do limite

- **WHEN** um ou mais vértices variam acima do limite
- **THEN** o teste SHALL registrar o resultado com os prazos e as variações observadas, e — se classificado como aviso — a curva SHALL ser publicada assim mesmo

#### Scenario: Sem curva anterior

- **WHEN** não existe curva publicada em data anterior para a mesma definição e momento
- **THEN** o teste SHALL ser registrado como não aplicável, e MUST NOT ser contado como aprovação

### Requirement: Comparação contra a curva importada da mesma data

Quando existir curva importada publicada para a mesma data e momento, a bateria SHALL comparar a curva construída contra ela vértice a vértice e SHALL registrar as diferenças.

#### Scenario: Curva oficial disponível

- **WHEN** existe curva importada publicada para a mesma data
- **THEN** a comparação SHALL ser executada e o resultado registrado, com a maior diferença e o prazo onde ocorreu

#### Scenario: Curva oficial ausente

- **WHEN** não existe curva importada para a data
- **THEN** o teste SHALL ser registrado como não aplicável

#### Scenario: Prazos não coincidentes

- **WHEN** as duas curvas não têm exatamente os mesmos prazos
- **THEN** apenas os prazos comuns SHALL ser comparados, e os demais SHALL ser reportados como sem contraparte, sem interpolação para completar

### Requirement: Persistência do resultado da validação

O resultado de cada teste SHALL ser persistido em `validacao_curva`, associado à versão de curva, com identificador do teste, classificação, resultado, medida observada, limite aplicado e instante. A persistência SHALL ocorrer tanto em aprovação quanto em reprovação.

#### Scenario: Resultado completo persistido

- **WHEN** a bateria termina
- **THEN** todos os testes executados SHALL ter resultado persistido, inclusive os aprovados e os não aplicáveis

#### Scenario: Reprovação auditável

- **WHEN** uma versão é reprovada
- **THEN** o resultado dos testes SHALL permanecer consultável junto com a versão reprovada, com valores observados e limites

### Requirement: Limites declarados por definição de curva

Os limites de cada teste — erro máximo de reprecificação, faixa de taxas, limites de forward, variação máxima entre vértices adjacentes, variação máxima contra o dia anterior e cobertura mínima — SHALL ser declarados na versão de definição da curva, e MUST NOT ser constantes de código.

#### Scenario: Limite específico por curva

- **WHEN** duas curvas têm tolerâncias diferentes
- **THEN** cada uma SHALL ser validada com os seus próprios limites

#### Scenario: Limite ausente

- **WHEN** um teste habilitado não tem limite declarado na definição
- **THEN** a validação SHALL falhar nomeando o teste e o limite ausente, em vez de assumir um valor padrão

### Requirement: Orçamento de tempo da validação

A bateria SHALL ter orçamento de tempo próprio e SHALL registrar sua duração efetiva. Estouro do orçamento SHALL ser sinalizado sem interromper a validação em curso.

#### Scenario: Duração registrada

- **WHEN** a bateria termina
- **THEN** sua duração SHALL ser registrada como etapa da execução, para acompanhamento contra o orçamento

#### Scenario: Validação demorando demais

- **WHEN** a bateria excede o orçamento declarado
- **THEN** o estouro SHALL ser sinalizado na execução, e a validação SHALL concluir, porque publicar sem validar não é alternativa aceitável

### Requirement: Independência do módulo de validação

A validação SHALL residir em módulo próprio, sem dependência do código de orquestração do motor, e SHALL ser executável sobre uma curva e um conjunto de insumos fornecidos, sem banco e sem mensageria.

#### Scenario: Teste sem infraestrutura

- **WHEN** a bateria é testada
- **THEN** ela SHALL rodar sobre curva e insumos em memória, sem banco e sem broker

#### Scenario: Extraível para serviço próprio

- **WHEN** a validação for movida para um serviço separado
- **THEN** a mudança SHALL se restringir ao transporte, sem alteração da lógica dos testes

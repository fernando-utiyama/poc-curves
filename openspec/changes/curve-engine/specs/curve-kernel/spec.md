## ADDED Requirements

### Requirement: Biblioteca pura e independente

O `curve-kernel` SHALL ser um módulo de biblioteca sem dependência de framework de aplicação, de banco de dados, de mensageria ou de rede. O módulo MUST NOT declarar dependência de qualquer repositório de curvas externo a este.

#### Scenario: Uso sem infraestrutura

- **WHEN** os testes do kernel são executados
- **THEN** eles SHALL rodar sem banco, sem broker, sem servidor e sem acesso à rede

#### Scenario: Dependência externa proibida

- **WHEN** o kernel declara dependência de framework de aplicação ou de artefato de outro repositório de curvas
- **THEN** a verificação de dependências SHALL falhar o build

### Requirement: Estruturas de curva

O kernel SHALL prover as estruturas de curva de juros e de curva de pontos, com vértices ordenados por prazo, e a amostragem de taxa e de fator de desconto em um prazo dado.

#### Scenario: Amostragem em vértice existente

- **WHEN** a curva é amostrada exatamente em um prazo que é vértice
- **THEN** o valor retornado SHALL ser o valor do vértice, sem passar por interpolação

#### Scenario: Vértices desordenados na entrada

- **WHEN** uma curva é construída com vértices fora de ordem de prazo
- **THEN** a construção SHALL falhar ou ordenar de forma determinística, e MUST NOT produzir curva com ordem indefinida

#### Scenario: Prazos duplicados

- **WHEN** dois vértices com o mesmo prazo e valores distintos são fornecidos
- **THEN** a construção SHALL falhar nomeando o prazo em conflito

### Requirement: Interpoladores

O kernel SHALL prover os interpoladores flat forward, linear, log-linear, log-cúbico, spline cúbico natural, monotônico convexo e flat forward linear, todos selecionáveis por identificador.

#### Scenario: Seleção por identificador

- **WHEN** um interpolador é solicitado pelo seu identificador
- **THEN** o kernel SHALL retornar a implementação correspondente, e SHALL falhar nomeando o identificador quando ele não existir

#### Scenario: Reprodutibilidade

- **WHEN** o mesmo interpolador é aplicado duas vezes aos mesmos vértices e ao mesmo prazo
- **THEN** o resultado SHALL ser idêntico

### Requirement: Políticas de extrapolação

O kernel SHALL prover as políticas de extrapolação estrita, taxa constante, forward constante e forward linear. A política estrita SHALL rejeitar prazo fora do intervalo dos vértices, nomeando o prazo pedido e o intervalo disponível.

#### Scenario: Política estrita fora do intervalo

- **WHEN** um prazo além do último vértice é solicitado sob política estrita
- **THEN** a operação SHALL falhar informando o prazo pedido e o intervalo disponível

#### Scenario: Política permissiva fora do intervalo

- **WHEN** um prazo além do último vértice é solicitado sob política de taxa constante
- **THEN** o valor retornado SHALL seguir a política declarada, de forma determinística

### Requirement: Convenções de mercado B3 e ANBIMA

O kernel SHALL prover o calendário de dias úteis B3/ANBIMA com os feriados reais, incluindo os móveis, e as contagens de dias úteis base 252, atual/360 e atual/365.

#### Scenario: Feriado móvel

- **WHEN** a contagem de dias úteis atravessa um feriado móvel
- **THEN** o resultado SHALL excluir esse dia, conforme o calendário oficial

#### Scenario: Data fora do intervalo do calendário

- **WHEN** uma data fora do intervalo coberto pelo calendário é usada
- **THEN** a operação SHALL falhar explicitamente, e MUST NOT assumir que a data é dia útil

### Requirement: Precisão decimal e arredondamento

Todo valor com política de arredondamento de mercado SHALL ser representado por decimal de precisão arbitrária, com escala e modo de arredondamento explícitos, incluindo truncamento. Esses valores MUST NOT transitar por tipo de ponto flutuante, nem como resultado, nem como intermediário.

#### Scenario: Truncamento como modo de primeira classe

- **WHEN** uma política de arredondamento por truncamento é aplicada
- **THEN** o resultado SHALL ser truncado na escala declarada, sem arredondar para o vizinho mais próximo

#### Scenario: Potência fracionária

- **WHEN** uma potência com expoente fracionário é calculada sobre valor decimal
- **THEN** o cálculo SHALL usar a rotina decimal do kernel, e MUST NOT converter o valor para ponto flutuante

#### Scenario: Ponto flutuante restrito

- **WHEN** ponto flutuante é usado fora de numérica iterativa sem arredondamento de mercado
- **THEN** a verificação estática do build SHALL falhar

### Requirement: Bootstrap e helpers de taxa

O kernel SHALL prover o algoritmo de bootstrap e os helpers de taxa por tipo de instrumento, produzindo a curva a partir de um conjunto de instrumentos com seus prazos e cotações.

#### Scenario: Bootstrap determinístico

- **WHEN** o bootstrap é executado duas vezes sobre o mesmo conjunto de instrumentos
- **THEN** os vértices resultantes SHALL ser idênticos, valor a valor

#### Scenario: Conjunto de instrumentos insuficiente

- **WHEN** o conjunto de instrumentos não permite resolver a curva
- **THEN** o bootstrap SHALL falhar nomeando o que faltou, e MUST NOT produzir curva parcial

#### Scenario: Não convergência

- **WHEN** a resolução numérica de um vértice não converge dentro do limite de iterações
- **THEN** a operação SHALL falhar nomeando o instrumento e o vértice, e MUST NOT retornar a última aproximação como se fosse resultado

### Requirement: Reconciliação contra oráculo oficial

O kernel SHALL prover a reconciliação vértice a vértice entre uma curva calculada e uma curva de referência oficial, arredondando o valor do oráculo pela mesma política de arredondamento de produção e comparando de forma exata.

#### Scenario: Curva confere com o oráculo

- **WHEN** a curva calculada é reconciliada contra a taxa de referência oficial da mesma data
- **THEN** o relatório SHALL indicar conferência total quando todos os vértices baterem exatamente após o arredondamento pela política de produção

#### Scenario: Divergência localizada

- **WHEN** um ou mais vértices divergem
- **THEN** o relatório SHALL identificar cada prazo divergente com o valor calculado e o valor de referência

#### Scenario: Tolerância proibida

- **WHEN** a comparação usa tolerância numérica para mascarar diferença de convenção
- **THEN** o teste SHALL ser considerado inválido, porque o oráculo deve ser arredondado pela mesma política e comparado exato

### Requirement: Testes com fixture real

Os testes do kernel SHALL usar dados reais fixados de fonte oficial. Um teste MUST NOT comparar um método contra o seu próprio delegado interno como forma de verificação.

#### Scenario: Verificação circular

- **WHEN** um teste verifica um método comparando-o com a implementação que ele mesmo chama
- **THEN** o teste SHALL ser considerado inválido, por não verificar comportamento algum

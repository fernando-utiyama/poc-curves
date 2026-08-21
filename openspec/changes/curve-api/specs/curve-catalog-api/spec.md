## ADDED Requirements

### Requirement: Cadastro de definição de curva

A API SHALL permitir cadastrar uma definição de curva com código único, nome, moeda, modo de origem (`BOOTSTRAPPED` ou `IMPORTED`) e estado. O código SHALL ser imutável após a criação.

#### Scenario: Cadastro válido

- **WHEN** uma definição é cadastrada com código inédito e modo de origem válido
- **THEN** ela SHALL ser criada junto com sua primeira versão de definição, e a resposta SHALL identificar ambas

#### Scenario: Código duplicado

- **WHEN** uma definição é cadastrada com código já existente
- **THEN** a operação SHALL ser recusada nomeando o código em conflito

#### Scenario: Tentativa de alterar o código

- **WHEN** uma edição tenta alterar o código de uma definição existente
- **THEN** a operação SHALL ser recusada, porque o código é imutável

### Requirement: Configuração da versão de definição

A versão de definição SHALL conter contagem de dias, calendário, interpolador, política de extrapolação, política de arredondamento, modelo de construção apontado, vínculos de fonte (conjuntos de dados exigidos), dependências de outras curvas e o intervalo de vigência.

#### Scenario: Configuração completa

- **WHEN** uma versão de definição é criada com todos os campos
- **THEN** ela SHALL ser persistida e SHALL passar a valer a partir do início de vigência declarado

#### Scenario: Campo obrigatório ausente

- **WHEN** a versão é criada sem interpolador ou sem política de extrapolação
- **THEN** a operação SHALL ser recusada nomeando o campo ausente

### Requirement: Edição cria versão nova

Toda alteração de configuração SHALL criar uma nova versão de definição, com número incremental, e MUST NOT alterar a versão anterior. As curvas publicadas sob a versão anterior SHALL continuar apontando para ela.

#### Scenario: Troca de interpolador

- **WHEN** o interpolador de uma curva é alterado
- **THEN** uma nova versão de definição SHALL ser criada, e a resposta SHALL identificar a versão criada e a versão de origem

#### Scenario: Histórico preservado

- **WHEN** o histórico de uma definição é consultado após várias edições
- **THEN** todas as versões SHALL estar disponíveis com sua configuração e seu intervalo de vigência

#### Scenario: Curva publicada continua explicada

- **WHEN** uma curva foi publicada sob uma versão de definição e depois a definição é editada
- **THEN** a procedência daquela curva SHALL continuar apontando para a versão sob a qual ela foi construída

### Requirement: Coerência entre modo de origem e vínculos de fonte

A API SHALL validar que o modo de origem é compatível com os vínculos de fonte declarados: `BOOTSTRAPPED` exige conjuntos de dado individual; `IMPORTED` exige exatamente um vínculo de conjunto de curva pronta.

#### Scenario: Curva construída sem insumo individual

- **WHEN** uma definição `BOOTSTRAPPED` é salva sem nenhum vínculo de conjunto de dado individual
- **THEN** a operação SHALL ser recusada nomeando a incoerência

#### Scenario: Curva importada vinculada a dado individual

- **WHEN** uma definição `IMPORTED` é salva vinculada a um conjunto de dado individual
- **THEN** a operação SHALL ser recusada nomeando o modo e o vínculo incompatível

### Requirement: Validação do modelo apontado

A API SHALL validar que o modelo de construção referenciado existe e está habilitado. Definição `IMPORTED` MUST NOT referenciar modelo, porque não há cálculo.

#### Scenario: Modelo inexistente

- **WHEN** a definição referencia um modelo que não está no catálogo
- **THEN** a operação SHALL ser recusada nomeando o modelo solicitado

#### Scenario: Modelo desabilitado

- **WHEN** a definição referencia um modelo desabilitado
- **THEN** a operação SHALL ser recusada informando o estado do modelo

#### Scenario: Curva construída sem modelo explícito

- **WHEN** uma definição `BOOTSTRAPPED` é criada sem indicar modelo
- **THEN** ela SHALL apontar para o modelo embutido padrão do seu tipo de curva

#### Scenario: Curva importada com modelo

- **WHEN** uma definição `IMPORTED` é salva referenciando um modelo
- **THEN** a operação SHALL ser recusada, porque curva importada não é calculada

### Requirement: Validação de interpolador e políticas

A API SHALL validar que o interpolador, a política de extrapolação e a política de arredondamento informados existem entre os suportados.

#### Scenario: Interpolador inexistente

- **WHEN** um interpolador desconhecido é informado
- **THEN** a operação SHALL ser recusada nomeando o valor recebido e listando os suportados

### Requirement: Validação de dependências entre curvas

A API SHALL validar que as curvas declaradas como dependência existem e que o conjunto de dependências não forma ciclo.

#### Scenario: Dependência inexistente

- **WHEN** a definição declara dependência de uma curva que não existe
- **THEN** a operação SHALL ser recusada nomeando a curva ausente

#### Scenario: Ciclo de dependência

- **WHEN** a configuração salva criaria um ciclo entre definições
- **THEN** a operação SHALL ser recusada descrevendo o ciclo detectado

### Requirement: Prazo de publicação e limites no cadastro

A definição de curva SHALL incluir, no cadastro, o horário limite de publicação, o orçamento de tempo por etapa, a duração da janela de bloqueio de carga histórica e os limites de cada teste de validação de consistência — erro máximo de reprecificação, faixa de taxas, limites de forward, variação máxima entre vértices adjacentes, variação máxima contra o dia anterior e cobertura mínima — além da classificação de cada teste entre bloqueante e aviso.

#### Scenario: Cadastro completo

- **WHEN** uma definição é cadastrada
- **THEN** o horário limite de publicação SHALL ser obrigatório, e os limites dos testes habilitados SHALL ser informados

#### Scenario: Limite ausente para teste habilitado

- **WHEN** um teste de validação é habilitado sem o seu limite
- **THEN** o cadastro SHALL ser recusado nomeando o teste e o limite ausente, em vez de assumir um valor padrão

#### Scenario: Classificação por teste

- **WHEN** a definição declara a classificação de cada teste
- **THEN** ela SHALL ser persistida na versão de definição e aplicada nas construções seguintes

### Requirement: Modelo de arquivo para carga manual

A API SHALL gerar, para uma definição de curva, o modelo de arquivo de carga manual em CSV e em planilha, com o cabeçalho correto e os prazos esperados daquela curva já preenchidos.

O modelo SHALL ser gerado a partir da definição, e MUST NOT ser arquivo estático genérico.

#### Scenario: Modelo específico da curva

- **WHEN** o modelo é solicitado para uma definição
- **THEN** o arquivo SHALL trazer as colunas conforme as convenções daquela curva e os prazos esperados dela

#### Scenario: Dois formatos

- **WHEN** o modelo é solicitado em CSV e em planilha
- **THEN** os dois SHALL ter o mesmo leiaute, aceitos indistintamente na carga

#### Scenario: Modelo acompanha a definição

- **WHEN** a definição é alterada em convenções ou prazos esperados
- **THEN** o modelo gerado a seguir SHALL refletir a alteração

### Requirement: Ciclo de vida da definição

Uma definição SHALL transitar entre rascunho, ativa e aposentada. Definição aposentada MUST NOT ser alvo de novas construções, e SHALL permanecer consultável junto com suas curvas já publicadas.

#### Scenario: Aposentadoria

- **WHEN** uma definição é aposentada
- **THEN** ela SHALL parar de ser construída, e suas curvas publicadas SHALL continuar consultáveis

#### Scenario: Definição em rascunho

- **WHEN** uma definição está em rascunho
- **THEN** ela MUST NOT ser construída nem agendada

### Requirement: Consulta do catálogo

A API SHALL expor a consulta das definições com filtro por código, nome, moeda, modo de origem e estado, retornando para cada uma a versão de definição vigente e o modelo apontado.

#### Scenario: Listagem para a tela de catálogo

- **WHEN** o catálogo é consultado
- **THEN** cada definição SHALL trazer código, nome, modo de origem, estado, versão vigente e modelo apontado

#### Scenario: Filtro por modo de origem

- **WHEN** o catálogo é filtrado por modo de origem
- **THEN** apenas as definições daquele modo SHALL ser retornadas

### Requirement: Autorização de cadastro

Criar, editar e mudar o estado de uma definição SHALL exigir perfil de administrador de curva. Consultar o catálogo SHALL ser permitido a qualquer usuário autenticado.

#### Scenario: Leitor tenta cadastrar

- **WHEN** um usuário com perfil de leitor tenta criar uma definição
- **THEN** a operação SHALL ser recusada por falta de autorização

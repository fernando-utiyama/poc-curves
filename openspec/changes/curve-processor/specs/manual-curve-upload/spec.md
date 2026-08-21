## ADDED Requirements

### Requirement: Curva carregada como contingência

A plataforma SHALL aceitar a carga manual de uma curva a partir de arquivo, como caminho de contingência para quando a curva não puder ser produzida pelo fluxo normal antes do horário limite. A versão resultante SHALL ter `origem_versao` igual a `CARREGADA`.

Uma definição de curva de qualquer modo de origem SHALL poder receber versão carregada. O modo de origem da definição descreve o caminho **normal**; a carga manual é exceção declarada, não substituição do modo.

#### Scenario: Contingência em curva construída

- **WHEN** uma curva de modo `BOOTSTRAPPED` não pôde ser construída e o operador carrega a curva por arquivo
- **THEN** a versão SHALL ser publicada com `origem_versao` igual a `CARREGADA`, e a definição SHALL permanecer com modo `BOOTSTRAPPED`

#### Scenario: Origem sempre distinguível

- **WHEN** uma versão de curva é consultada por qualquer caminho
- **THEN** a resposta SHALL informar se ela foi calculada, importada da fonte oficial ou carregada manualmente

#### Scenario: Carga não altera a definição

- **WHEN** uma versão carregada é publicada
- **THEN** a definição de curva e a sua versão vigente MUST NOT ser alteradas

### Requirement: Justificativa obrigatória na carga

A carga manual SHALL exigir justificativa textual não vazia, e SHALL registrar quem carregou, quando, o nome do arquivo e o hash do conteúdo. Carga sem justificativa MUST NOT ser aceita.

#### Scenario: Justificativa ausente

- **WHEN** uma carga é submetida sem justificativa
- **THEN** ela SHALL ser recusada, e nenhuma versão SHALL ser criada

#### Scenario: Rastro completo

- **WHEN** a procedência de uma versão carregada é consultada
- **THEN** SHALL identificar o arquivo, o hash do conteúdo, o autor da carga, o instante e a justificativa

### Requirement: Formatos aceitos e leiaute declarado

O processor SHALL aceitar arquivo em CSV e em planilha, com o leiaute declarado pelo contrato: uma linha por vértice, com prazo e taxa, e as demais colunas conforme a definição da curva. A primeira linha SHALL ser cabeçalho.

#### Scenario: CSV válido

- **WHEN** um CSV no leiaute declarado é carregado
- **THEN** os vértices SHALL ser lidos e a carga SHALL prosseguir para a validação

#### Scenario: Planilha válida

- **WHEN** uma planilha no mesmo leiaute é carregada
- **THEN** o resultado SHALL ser idêntico ao do CSV equivalente

#### Scenario: Cabeçalho divergente

- **WHEN** o cabeçalho não corresponde ao leiaute declarado
- **THEN** a carga SHALL ser recusada nomeando as colunas esperadas e as recebidas, e nenhuma versão SHALL ser criada

#### Scenario: Arquivo vazio

- **WHEN** o arquivo não contém nenhuma linha de vértice
- **THEN** a carga SHALL ser recusada informando isso explicitamente

### Requirement: Erros de leitura reportados por linha

Erro no conteúdo do arquivo SHALL ser reportado identificando a linha e a coluna afetadas, e SHALL listar **todos** os erros encontrados, não apenas o primeiro. Arquivo com qualquer erro MUST NOT ser parcialmente aplicado.

#### Scenario: Várias linhas com problema

- **WHEN** o arquivo tem erros em linhas diferentes
- **THEN** a resposta SHALL listar todos eles com linha, coluna e motivo

#### Scenario: Aplicação parcial proibida

- **WHEN** o arquivo tem ao menos um erro
- **THEN** nenhuma versão SHALL ser criada, ainda que a maioria das linhas esteja correta

#### Scenario: Valor numérico inválido

- **WHEN** uma taxa não pode ser convertida com a precisão exigida
- **THEN** o erro SHALL nomear a linha, o valor recebido e o formato esperado

#### Scenario: Prazo duplicado

- **WHEN** o arquivo repete o mesmo prazo em duas linhas
- **THEN** a carga SHALL ser recusada nomeando o prazo e as linhas em conflito

### Requirement: Precisão preservada na leitura

Os valores lidos do arquivo SHALL ser convertidos diretamente para decimal de precisão arbitrária, respeitando o separador decimal declarado. A leitura MUST NOT passar por tipo de ponto flutuante, nem arredondar o valor informado.

#### Scenario: Valor com muitas casas decimais

- **WHEN** o arquivo traz uma taxa com doze casas decimais
- **THEN** o vértice persistido SHALL conter exatamente esse valor, dígito a dígito

### Requirement: Curva carregada passa pelo mesmo gate de validação

A versão carregada SHALL ser gravada em `EM_VALIDACAO` e submetida à mesma bateria de validação de consistência das demais, antes de qualquer promoção. Testes que dependam de insumos inexistentes SHALL ser registrados como não aplicáveis.

#### Scenario: Curva carregada com defeito

- **WHEN** o arquivo carregado produz uma curva que viola um teste bloqueante
- **THEN** a versão SHALL ser marcada como `REPROVADA`, MUST NOT ser publicada, e os testes reprovados SHALL ser informados ao operador

#### Scenario: Testes sem insumo de calibração

- **WHEN** a bateria roda sobre uma curva carregada, que não tem instrumentos de calibração
- **THEN** o teste de reprecificação SHALL ser registrado como não aplicável, e os demais SHALL ser executados normalmente

#### Scenario: Validação não é dispensada por urgência

- **WHEN** a carga é feita em contingência, próxima do horário limite
- **THEN** a bateria SHALL ser executada integralmente, porque curva digitada é mais sujeita a erro, não menos

### Requirement: Versionamento e substituição na carga

A versão carregada SHALL seguir as mesmas regras de versionamento das demais: número incremental, substituição da versão anterior apenas na promoção, e preservação dos vértices da anterior.

#### Scenario: Carga sobre curva já publicada

- **WHEN** uma curva já publicada no dia recebe uma carga manual aprovada
- **THEN** a versão carregada SHALL ser publicada como nova versão, e a anterior SHALL passar a substituída com seus vértices intactos

#### Scenario: Carga reprovada não substitui

- **WHEN** a versão carregada é reprovada na validação
- **THEN** a versão anteriormente publicada SHALL permanecer publicada e inalterada

#### Scenario: Recarga do mesmo arquivo

- **WHEN** o mesmo arquivo é carregado duas vezes
- **THEN** a segunda carga SHALL ser reconhecida pelo hash do conteúdo e MUST NOT criar versão duplicada sem alteração

### Requirement: Autorização e limite da carga

A carga manual SHALL exigir perfil de operador ou de administrador de curva, e o arquivo SHALL respeitar o limite de tamanho configurado.

#### Scenario: Leitor tenta carregar

- **WHEN** um usuário com perfil de leitor tenta carregar uma curva
- **THEN** a operação SHALL ser recusada por falta de autorização

#### Scenario: Arquivo acima do limite

- **WHEN** o arquivo excede o limite de tamanho
- **THEN** a carga SHALL ser recusada informando o limite

### Requirement: Execução e rastreio da carga

Toda carga SHALL criar uma execução com `correlacao_id` próprio, faixa prioritária e tipo de disparo identificando a carga manual, visível no monitoramento junto das demais execuções.

#### Scenario: Carga rastreável

- **WHEN** uma carga é submetida
- **THEN** SHALL existir execução correspondente, e o resultado — publicada, reprovada ou recusada — SHALL ser visível no monitoramento

#### Scenario: Carga aparece no painel do dia

- **WHEN** uma curva do dia foi publicada por carga manual
- **THEN** o painel do dia SHALL indicá-la como carregada, e não como calculada

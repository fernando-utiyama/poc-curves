## ADDED Requirements

### Requirement: Aplicação Angular autônoma na POC

O front da POC SHALL ser uma aplicação Angular autônoma, servida estaticamente, sem dependência de host externo para executar. A POC MUST NOT depender do shell Liquid para funcionar.

#### Scenario: Subida no ambiente local

- **WHEN** o ambiente local é iniciado
- **THEN** o app SHALL ser servido e utilizável no navegador, sem nenhum host de micro-frontend

#### Scenario: Desenvolvimento

- **WHEN** o desenvolvedor executa o servidor de desenvolvimento do Angular
- **THEN** todas as telas SHALL funcionar contra o BFF local

### Requirement: Preparação para embarque futuro no shell

O app SHALL ser estruturado de forma a poder ser exposto depois como micro-frontend, sem reescrita: rota base configurável, ausência de suposição sobre controle da rota raiz do navegador, tema por tokens substituíveis e contexto de usuário obtido de uma fonte única e trocável.

#### Scenario: Rota base configurável

- **WHEN** a rota base é alterada por configuração
- **THEN** todas as rotas internas SHALL passar a ser relativas a ela, sem alteração de código

#### Scenario: Nenhuma suposição sobre a rota raiz

- **WHEN** o app é montado sob um prefixo de rota
- **THEN** a navegação interna SHALL continuar correta, sem sequestrar a rota raiz

#### Scenario: Tema por tokens

- **WHEN** os tokens de tema são substituídos
- **THEN** a aparência SHALL acompanhar, sem cores ou tipografia fixadas diretamente nos componentes

#### Scenario: Contexto de usuário centralizado

- **WHEN** a origem do contexto de usuário é trocada
- **THEN** apenas a implementação da fonte SHALL mudar, e nenhuma tela SHALL precisar ser alterada

### Requirement: Autenticação OIDC própria na POC

O app SHALL executar o fluxo OIDC por conta própria contra o provedor local, obtendo o token usado nas chamadas ao BFF.

#### Scenario: Login

- **WHEN** um usuário não autenticado acessa o app
- **THEN** o fluxo de autenticação SHALL ser iniciado, e após a conclusão o usuário SHALL retornar à rota pretendida

#### Scenario: Token aplicado às chamadas

- **WHEN** o usuário está autenticado
- **THEN** todas as chamadas ao BFF SHALL apresentar o token obtido

### Requirement: Tratamento de sessão expirada

Quando o BFF responder não autorizado por sessão expirada, o app SHALL reautenticar e retomar a navegação, e SHALL preservar o conteúdo de formulário em preenchimento.

#### Scenario: Sessão expira durante o uso

- **WHEN** uma requisição retorna não autorizado por expiração
- **THEN** o app SHALL reautenticar e informar claramente o que aconteceu

#### Scenario: Trabalho não perdido silenciosamente

- **WHEN** a sessão expira com um formulário preenchido
- **THEN** o conteúdo preenchido SHALL ser preservado até a conclusão da reautenticação

### Requirement: Fronteira de comunicação

O app SHALL se comunicar exclusivamente com o `curve-bff`. O app MUST NOT chamar `curve-api`, `curve-engine`, `curve-orchestrator`, banco de dados ou fontes externas.

#### Scenario: Chamada a serviço de domínio

- **WHEN** o código do app referencia um endpoint de serviço de domínio
- **THEN** a verificação do build SHALL falhar

### Requirement: Cliente gerado a partir do contrato

Os tipos e o cliente HTTP do app SHALL ser gerados do contrato OpenAPI do BFF, versionado no repositório. Divergência entre app e contrato SHALL falhar em tempo de compilação.

#### Scenario: Campo removido do contrato

- **WHEN** um campo usado pelo app é removido do contrato do BFF
- **THEN** a compilação do app SHALL falhar, apontando o uso

### Requirement: Empacotamento para o ambiente local

O app SHALL ser empacotado como artefato estático e servido por container no ambiente Podman, com a URL do BFF e a configuração de autenticação injetadas em tempo de execução, não fixadas no build.

#### Scenario: Mesma imagem em ambientes diferentes

- **WHEN** a mesma imagem é executada com configuração diferente
- **THEN** o app SHALL apontar para o BFF e o provedor de identidade corretos, sem rebuild

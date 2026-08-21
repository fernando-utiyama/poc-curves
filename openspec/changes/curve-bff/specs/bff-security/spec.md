## ADDED Requirements

### Requirement: Fronteira única exposta

O `curve-bff` SHALL ser o único serviço da plataforma alcançável pelo navegador. `curve-api`, `curve-engine` e `curve-orchestrator` MUST NOT ser expostos externamente.

#### Scenario: Chamada direta a serviço interno

- **WHEN** o navegador tenta chamar diretamente uma API de domínio
- **THEN** a requisição SHALL falhar por inalcançabilidade, conforme a configuração de rede do ambiente

#### Scenario: Fronteira verificada

- **WHEN** a configuração do ambiente é verificada
- **THEN** apenas a porta do BFF SHALL estar publicada para fora

### Requirement: Autenticação OIDC obrigatória

Toda requisição SHALL apresentar token OIDC válido. Requisição sem token, com token expirado ou com assinatura inválida SHALL ser rejeitada como não autorizada, sem chamar nenhuma dependência.

#### Scenario: Token válido

- **WHEN** uma requisição chega com token válido
- **THEN** a identidade do usuário SHALL ser resolvida e a requisição SHALL prosseguir

#### Scenario: Token ausente

- **WHEN** uma requisição chega sem token
- **THEN** SHALL ser rejeitada como não autorizada, e nenhuma dependência SHALL ser chamada

#### Scenario: Token expirado

- **WHEN** o token expira durante a sessão
- **THEN** a resposta SHALL ser uma condição de não autorizado padronizada, que permita ao front reautenticar

### Requirement: Perfis e autorização por operação

O BFF SHALL resolver os perfis do usuário entre `CURVE_VIEWER`, `CURVE_OPERATOR` e `CURVE_ADMIN`, e SHALL autorizar por operação, não por endpoint agregador.

- `CURVE_VIEWER`: consultar catálogo, curva, vértices, interpolação, comparação, histórico, procedência e execuções.
- `CURVE_OPERATOR`: o acima, mais disparar ingestão manual e backfill.
- `CURVE_ADMIN`: o acima, mais cadastrar e editar curva, gerir agendamentos, importar modelo Groovy e trocar o modelo de uma curva.

#### Scenario: Leitor consulta

- **WHEN** um usuário com perfil de leitor consulta uma curva
- **THEN** a operação SHALL ser permitida

#### Scenario: Leitor tenta disparar

- **WHEN** um usuário com perfil de leitor tenta disparar ingestão
- **THEN** a operação SHALL ser recusada por falta de autorização, e o orquestrador MUST NOT ser chamado

#### Scenario: Operador tenta trocar modelo

- **WHEN** um usuário com perfil de operador tenta trocar o modelo de uma curva
- **THEN** a operação SHALL ser recusada, porque exige perfil de administrador

#### Scenario: Tela visível com ação restrita

- **WHEN** um leitor abre uma tela que contém uma ação restrita
- **THEN** a consulta SHALL ser atendida, e a resposta SHALL indicar que a ação não está autorizada para aquele usuário

### Requirement: Credencial de serviço nas chamadas internas

O BFF SHALL chamar as APIs de domínio com credencial de serviço própria, propagando a identidade do usuário como contexto para auditoria. O token do usuário final MUST NOT ser repassado diretamente às APIs internas.

#### Scenario: Identidade preservada para auditoria

- **WHEN** o BFF dispara uma ingestão em nome de um operador
- **THEN** a execução criada SHALL registrar a identidade do operador, e não a do serviço

### Requirement: Propagação de correlation id

O BFF SHALL gerar ou receber um `correlacao_id` por requisição e SHALL propagá-lo a todas as chamadas internas e a todo log.

#### Scenario: Rastreio de uma ação de tela

- **WHEN** uma ação de tela dispara chamadas a mais de uma dependência
- **THEN** todas as chamadas SHALL carregar o mesmo `correlacao_id`

### Requirement: Isolamento de acesso a dados

O `curve-bff` MUST NOT acessar banco de dados nem Kafka, sob nenhuma circunstância.

#### Scenario: Dependência proibida declarada

- **WHEN** o serviço declara dependência de driver de banco ou de cliente Kafka
- **THEN** o teste de fronteira SHALL falhar o build

### Requirement: Proteções de superfície pública

Sendo a única superfície exposta, o BFF SHALL aplicar restrição de origem para o front, limite de tamanho de payload e limitação de taxa por usuário.

#### Scenario: Origem não permitida

- **WHEN** uma requisição chega de origem não permitida
- **THEN** ela SHALL ser rejeitada

#### Scenario: Payload acima do limite

- **WHEN** uma requisição excede o limite de tamanho configurado
- **THEN** ela SHALL ser rejeitada informando o limite

#### Scenario: Excesso de requisições

- **WHEN** um usuário excede a taxa configurada
- **THEN** as requisições excedentes SHALL ser recusadas com indicação de quando tentar novamente

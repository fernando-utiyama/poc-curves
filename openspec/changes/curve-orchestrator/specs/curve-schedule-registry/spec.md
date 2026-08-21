## ADDED Requirements

### Requirement: Cadastro de agendamento

O orquestrador SHALL permitir cadastrar agendamentos identificando a fonte, o conjunto de dados ou a curva alvo, a expressão de horário, o fuso horário, a janela de tentativa, o intervalo entre tentativas e o estado de ativação. Cadastrar, editar, ativar ou desativar agendamento MUST NOT exigir redeploy.

#### Scenario: Agendamento criado

- **WHEN** um administrador cadastra um agendamento diário para um conjunto de dados
- **THEN** ele SHALL passar a disparar no horário declarado, sem reinício do serviço

#### Scenario: Desativação

- **WHEN** um agendamento é desativado
- **THEN** ele SHALL parar de disparar, e SHALL permanecer cadastrado para reativação posterior

#### Scenario: Expressão de horário inválida

- **WHEN** a expressão de horário informada é inválida
- **THEN** o cadastro SHALL ser recusado nomeando o erro, e nenhum agendamento SHALL ser criado

### Requirement: Durabilidade dos agendamentos

Os agendamentos SHALL ser persistidos e recarregados na inicialização do serviço. Reiniciar o serviço MUST NOT perder agendamento nem provocar disparo duplicado do mesmo horário.

#### Scenario: Reinício do serviço

- **WHEN** o serviço é reiniciado
- **THEN** todos os agendamentos ativos SHALL voltar a operar com a mesma configuração

#### Scenario: Reinício dentro do horário de disparo

- **WHEN** o serviço reinicia no exato horário de um agendamento que já disparou
- **THEN** o disparo MUST NOT ser repetido para a mesma data e alvo

### Requirement: Respeito ao calendário de pregão

O agendamento SHALL consultar o calendário de pregão antes de disparar. Data que não é dia de pregão SHALL registrar a execução como ausência de dado do tipo não-pregão, sem acionar o feeder.

#### Scenario: Feriado em dia de semana

- **WHEN** o horário do agendamento chega em um feriado de pregão
- **THEN** a execução SHALL terminar como ausência de dado classificada como não-pregão, e o feeder MUST NOT ser acionado

#### Scenario: Dia de pregão comum

- **WHEN** o horário chega em um dia de pregão
- **THEN** o disparo SHALL prosseguir normalmente

### Requirement: Janela de tentativa

O agendamento SHALL declarar uma janela de tentativa e um intervalo entre tentativas. Quando a fonte responder que o dado ainda não foi divulgado, o orquestrador SHALL tentar novamente dentro da janela, no intervalo declarado.

#### Scenario: Dado divulgado na segunda tentativa

- **WHEN** a primeira tentativa retorna dado ainda não divulgado e a segunda encontra o dado
- **THEN** a execução SHALL concluir com sucesso, e o número de tentativas SHALL constar no registro

#### Scenario: Janela esgotada sem divulgação

- **WHEN** a janela de tentativa termina e o dado continua não divulgado
- **THEN** a execução SHALL terminar em ausência de dado, visível na tela, e MUST NOT ser registrada como falha

#### Scenario: Falha real dentro da janela

- **WHEN** a fonte falha por indisponibilidade em vez de ausência de dado
- **THEN** o tratamento SHALL seguir a política de retentativa de falha, e não a janela de divulgação

### Requirement: Alvo do agendamento

Um agendamento SHALL poder ter como alvo um conjunto de dados de dado individual ou o conjunto de dados de curva pronta. O orquestrador SHALL permitir cadastrar ambos para a mesma data, de forma independente.

#### Scenario: Agendamentos independentes para os dois tipos

- **WHEN** existem um agendamento para os arquivos de dado individual e outro para a curva pronta
- **THEN** os dois SHALL disparar de forma independente, cada um com sua janela e seu horário

#### Scenario: Falha de um não bloqueia o outro

- **WHEN** o agendamento de dado individual falha
- **THEN** o agendamento de curva pronta SHALL continuar operando normalmente

### Requirement: Consulta de agendamentos

O orquestrador SHALL expor a consulta dos agendamentos cadastrados, com sua configuração, estado de ativação, horário do último disparo e resultado da última execução.

#### Scenario: Tela de agendamentos

- **WHEN** a lista de agendamentos é consultada
- **THEN** cada linha SHALL mostrar alvo, horário, estado e o desfecho da última execução

### Requirement: Autorização de gestão de agendamento

Criar, editar, ativar e desativar agendamento SHALL exigir perfil de administrador de curva. Consultar agendamentos SHALL ser permitido a qualquer usuário autenticado.

#### Scenario: Usuário sem permissão

- **WHEN** um usuário com perfil de leitor tenta desativar um agendamento
- **THEN** a operação SHALL ser recusada por falta de autorização

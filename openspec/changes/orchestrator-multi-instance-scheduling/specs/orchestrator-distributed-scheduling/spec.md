## Purpose

Garantir que o agendamento de aquisições do curve-orchestrator continue correto quando o serviço roda com múltiplas réplicas simultâneas: cada agendamento dispara exatamente uma vez por horário devido, e o catálogo de agendamentos (criação, edição, ativação, desativação) converge em todas as réplicas sem depender de reinício.

## ADDED Requirements

### Requirement: Disparo único entre réplicas

Quando múltiplas réplicas do curve-orchestrator têm o mesmo agendamento registrado e seu horário chega, exatamente uma réplica SHALL processar o disparo (criar a execução e acionar o feeder); as demais SHALL detectar que outra réplica já assumiu o disparo e retornar sem efeito colateral, sem registrar erro.

#### Scenario: Duas réplicas disparam o mesmo cron ao mesmo tempo

- **WHEN** o horário de um agendamento chega e duas réplicas tentam processá-lo no mesmo instante
- **THEN** exatamente uma execução SHALL ser criada e exatamente uma chamada ao feeder SHALL ocorrer

#### Scenario: Réplica perdedora não gera erro visível

- **WHEN** uma réplica tenta processar um disparo já assumido por outra
- **THEN** ela SHALL retornar sem lançar exceção não tratada e sem registrar o agendamento como falho

### Requirement: Convergência do catálogo de agendamentos entre réplicas

Toda réplica SHALL reconciliar seu registro local de agendamentos contra o catálogo persistido em um intervalo curto e limitado, sem depender de reinício do processo. Criação, edição, ativação e desativação de um agendamento SHALL refletir em todas as réplicas dentro desse intervalo.

#### Scenario: Agendamento criado em uma réplica aparece nas demais

- **WHEN** um agendamento é criado via a réplica A
- **THEN** as réplicas B e C SHALL passar a disparar esse agendamento dentro do intervalo de reconciliação, sem reiniciar

#### Scenario: Edição de horário propaga para todas as réplicas

- **WHEN** a expressão de horário de um agendamento ativo é alterada
- **THEN** todas as réplicas SHALL passar a usar a nova expressão dentro do intervalo de reconciliação, e nenhuma SHALL continuar dispersando pela expressão antiga além desse intervalo

#### Scenario: Desativação para o disparo em todas as réplicas

- **WHEN** um agendamento é desativado
- **THEN** nenhuma réplica SHALL processar esse agendamento além do intervalo de reconciliação, mesmo que ele já estivesse registrado localmente antes da desativação

### Requirement: Execução presa não bloqueia indefinidamente

Uma execução deixada em estado não-terminal por uma réplica que caiu no meio do processamento SHALL ser detectada e reconciliada por outra réplica ainda viva, sem exigir reinício de nenhum processo, e sem depender de nenhuma tabela ou mecanismo de lock além do que já existe para rastrear execuções.

#### Scenario: Réplica cai no meio do processamento

- **WHEN** a réplica que criou uma execução cai antes de concluí-la, e o tempo decorrido desde o início ultrapassa o limite configurado de detecção de execução presa
- **THEN** outra réplica viva SHALL reconciliar essa execução (marcá-la como falha), liberando o alvo/data/momento para uma nova tentativa

#### Scenario: Execução legítima não é derrubada prematuramente

- **WHEN** uma execução está em andamento havia menos tempo que o limite de detecção de execução presa
- **THEN** a reconciliação periódica MUST NOT marcá-la como falha

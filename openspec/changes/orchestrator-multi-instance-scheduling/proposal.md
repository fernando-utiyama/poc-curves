## Why

Todos os serviços Java da plataforma passam a rodar com pelo menos 2 réplicas cada. `curve-orchestrator` não é seguro nesse modo hoje: `AgendamentoSchedulerRegistry` é um `Spring TaskScheduler` inteiramente em memória da JVM (`adapter/out/scheduling/AgendamentoSchedulerRegistry.java`) — cada réplica registra e dispara os mesmos crons de forma totalmente independente das demais. Isso causa dois problemas reais, confirmados no código:

1. **Disparo concorrente do mesmo agendamento em réplicas diferentes.** Hoje a única proteção é indireta: o índice único filtrado `ux_execucao_curva_ativa_conjunto`/`_definicao` (`db/migration/V14`) rejeita a segunda tentativa de `INSERT` em `execucao_curva` para o mesmo alvo/data/momento — mas isso sobe como exceção não tratada em `DisparoAgendadoExecutor.executar()`, não como um "outra réplica já assumiu" reconhecido e limpo.
2. **Estado de agendamento diverge entre réplicas.** `AgendamentoService.cadastrar/editar/ativar/desativar` grava no banco e chama `schedulerRegistry` só na réplica que atendeu aquela requisição HTTP — as demais só sincronizam o catálogo de agendamentos no *próximo boot* (`AgendamentoBootstrap`, que roda `listarAtivos()` uma única vez, na inicialização). Um agendamento criado ou editado fica com comportamento inconsistente entre réplicas até elas reiniciarem.

**Restrição explícita do usuário**: a solução SHALL ter a menor complexidade de banco de dados possível — nenhuma tabela nova, nenhuma biblioteca de lock distribuído. O design abaixo reaproveita só o que já existe.

## What Changes

- **Zero tabelas novas.** O índice único filtrado já existente (`ux_execucao_curva_ativa_conjunto`/`_definicao`, `db/migration/V14`) já é, na prática, um lock distribuído: a segunda réplica que tentar `INSERT` uma `ExecucaoCurva` para o mesmo alvo/data/momento toma uma violação de constraint do próprio SQL Server. A mudança é só **tratar essa violação como sinal esperado de "outra réplica já assumiu"** em `DisparoAgendadoExecutor.executar()`, em vez de deixá-la subir como exceção não tratada — sem lock explícito, sem `ShedLock`, sem dependência nova.
- Substitui o modelo "registra uma vez no boot + muta só na réplica que recebeu o HTTP" por **reconciliação periódica**: cada réplica relê `agendamento` (tabela já existente) em um intervalo curto e ajusta seu `TaskScheduler` local (registra o que é novo, cancela o que foi desativado, reagenda o que mudou de expressão de horário) — em vez de depender de `AgendamentoService` chamar `schedulerRegistry` diretamente. Nenhuma tabela nova; só muda quando/quantas vezes a mesma consulta já existente (`listarAtivos()`) é chamada.
- **Fecha a lacuna de execução "presa" entre réplicas sem tabela nova**: `ReconciliacaoService.reconciliarExecucoesPresas()` já existe e já faz exatamente o que seria o "lock expira" do ShedLock (recupera execução órfã), mas hoje só roda uma vez, no boot de cada processo — inútil se nenhuma réplica reiniciar. Passa a rodar também periodicamente, com um critério de "presa de verdade" baseado em tempo decorrido desde `iniciado_em` (não só "está em estado não-terminal", que hoje presume execução de processo recém-reiniciado) — para não derrubar execução legítima de uma réplica saudável ainda dentro da janela de tentativa.
- **BREAKING** (nível de implementação, não de API pública): `AgendamentoService.cadastrar/editar/ativar/desativar` deixam de depender de `AgendamentoSchedulerPort` — só escrevem no banco; a convergência do estado de agendamento em cada réplica passa a ser responsabilidade exclusiva do ciclo de reconciliação.

## Capabilities

### New Capabilities
- `orchestrator-distributed-scheduling`: garante que, com múltiplas réplicas do curve-orchestrator rodando, cada agendamento dispara exatamente uma vez por horário (não uma vez por réplica), e que o catálogo de agendamentos (criação, edição, ativação, desativação) converge em todas as réplicas dentro de um intervalo curto, sem depender de reinício.

### Modified Capabilities
(nenhuma — `curve-schedule-registry` (openspec/changes/curve-orchestrator) ainda não foi arquivada em `openspec/specs/`, então não há uma versão canônica para declarar delta formal; a seção "Durabilidade dos agendamentos" daquele capability, hoje escrita pensando em reinício de uma única instância, é estendida em espírito por `orchestrator-distributed-scheduling` para cobrir múltiplas réplicas simultâneas — a reconciliar quando `curve-orchestrator` for arquivado.)

## Impact

- **curve-orchestrator**: nenhuma dependência nova. `DisparoAgendadoExecutor` (captura a violação de constraint do `INSERT` como caminho esperado), `AgendamentoSchedulerRegistry` redesenhado (reconciliação periódica em vez de registro único), `AgendamentoService` (remove a chamada direta a `schedulerRegistry`), `ReconciliacaoService`/`ReconciliacaoInicializacaoRunner` (passam a rodar periodicamente, não só no boot, com critério de tempo decorrido).
- **db/migration**: nenhuma migração nova.
- **deploy/podman**: `compose.yaml` passa a subir 2+ réplicas de cada serviço Java (a definir como, na tarefa de implementação — `deploy: replicas` do Compose ou serviços duplicados nomeados, dado que Podman Compose tem suporte parcial a `deploy.replicas`).

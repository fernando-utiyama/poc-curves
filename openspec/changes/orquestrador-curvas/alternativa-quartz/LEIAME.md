# Alternativa guardada: agendamento pelo Quartz em cluster

Versão de 2026-09-30 da change `orquestrador-curvas` com o agendamento pelo Quartz (JDBC JobStore em cluster no SQL Server), guardada para uso futuro. **Não é a versão ativa**: a change ativa (pasta acima) usa o agendador do Spring com trava por ocorrência no banco, sem tabela nova.

Conteúdo: `proposal.md`, `design.md`, `tasks.md` e `specs/` da versão Quartz, e `scripts/quartz-orquestrador.sql` (DDL oficial do Quartz 2.5.2 para SQL Server, 11 tabelas `QRTZ_*`, com volta; ainda não executado em nenhum banco).

Para adotar: pedir o script ao DBA, trocar os arquivos da change ativa por estes e revalidar com `openspec validate orquestrador-curvas --strict`. O que o Quartz traz a mais que a versão ativa: gatilhos no banco (mudança de cadastro vale na hora em todas as instâncias, sem reconciliação), recuperação de execução pelo próprio Quartz e política de disparo perdido pronta.

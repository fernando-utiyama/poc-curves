## 1. Base

- [ ] 1.1 Esperar a transcrição do orquestrador real e mapear onde entram tarefa agendada, execução manual e execução única; verificar com o dono do orquestrador antes de codificar
- [ ] 1.2 Criar o cliente HTTP do conector e do engine com token por client credentials (Managed Identity ou cofre), `X-Correlation-Id` e os tempos limite da spec; verificar com servidor simulado que o cabeçalho e o token vão em toda chamada
- [ ] 1.3 Criar o relógio de Brasília e a checagem de dia útil pela exportação de calendário do engine, com cache até o fim do dia e o recuo para só fim de semana com o engine fora; verificar feriado, fim de semana, engine fora e a JVM em UTC

## 2. Tarefas

- [ ] 2.1 Criar `B3_TAXA_SWAP_DOWNLOAD`: conferência da `dataBase` da resposta, novas tentativas por intervalo até o limite, parada em 4xx, `CARGA_NAO_RECEBIDA`; verificar os três cenários da spec e cada status do conector
- [ ] 2.2 Criar `CONSTRUCAO_CURVAS_DATA`: chamada ao engine, repetição em 5xx e tempo esgotado, resumo por situação, `CURVAS_PENDENTES` só no último horário do dia; verificar os dois cenários da spec e o 403 sem repetição
- [ ] 2.3 Agendar pelos horários configurados, sem padrão, com `TAREFA_SEM_HORARIO`; verificar o cenário da spec
- [ ] 2.4 Criar a execução manual por data (inclusive o reprocessamento B3), com o papel `Curvas.Operador` e o usuário no log; verificar o cenário da spec

## 3. Operação

- [ ] 3.1 Emitir os eventos de log e as métricas da spec, sem token nem segredo; verificar os campos com um appender de teste
- [ ] 3.2 Garantir execução única entre instâncias pelo mecanismo do orquestrador real; verificar com duas instâncias que cada disparo roda uma vez
- [ ] 3.3 Rodar `openspec validate orquestrador-curvas --strict` e a suíte do orquestrador; verificar que tudo passa

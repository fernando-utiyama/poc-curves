## 1. Esqueleto do serviço

- [ ] 1.1 Criar o módulo Maven `services/curve-orchestrator` com Java 21, Spring Boot 3.4.x, sem Lombok
- [ ] 1.2 Configurar a fonte de dados com credencial restrita a `execucao_curva` e à tabela de agendamentos, com leitura de `definicao_curva`
- [ ] 1.3 Configurar o produtor Kafka de `curve.build.requested.v1`
- [ ] 1.4 Escrever o `Containerfile` e adicionar o serviço ao compose Podman com healthcheck

## 2. Modelo de execução

- [ ] 2.1 Implementar a entidade de execução com estado, `correlacao_id`, tipo de disparo, disparado por, alvo, data e momento
- [ ] 2.2 Implementar a máquina de estados `PENDENTE` → `EXECUTANDO` → `CONSTRUINDO` → `CONCLUIDA` / `SEM_DADO` / `FALHOU`
- [ ] 2.3 Implementar a geração e a propagação do `correlacao_id`
- [ ] 2.4 Implementar o registro de etapas, duração, tentativas e causa de falha
- [ ] 2.5 Implementar a verificação de execução ativa duplicada por alvo, data e momento

## 3. Prazo, orçamento e faixas

- [ ] 3.1 Ler o horário limite de publicação da definição de curva e associá-lo à execução
- [ ] 3.2 Manter e expor o tempo restante até o horário limite durante a execução
- [ ] 3.3 Registrar a duração efetiva de cada etapa concluída e a margem no encerramento
- [ ] 3.4 Implementar o estado `EM_RISCO` derivado do orçamento das etapas restantes
- [ ] 3.5 Implementar o alerta preditivo por ausência de publicação dentro do orçamento, sem depender de falha
- [ ] 3.6 Implementar o estado `ATRASADA`, distinto de falha, mantendo a execução em andamento
- [ ] 3.7 Implementar a seleção de faixa por origem do disparo e registrá-la na execução
- [ ] 3.8 Implementar a janela de bloqueio, pausando a faixa de massa e retomando após
- [ ] 3.9 Implementar o teto de retentativa derivado do tempo restante até o horário limite

## 4. Disparo manual

- [ ] 4.1 Implementar a API de disparo manual recebendo data e a lista de conjuntos de dados a consumir
- [ ] 4.2 Suportar conjuntos de dado individual (arquivo de preços, BVBG.086, BVBG.028) e o de curva pronta
- [ ] 4.3 Suportar o disparo de vários conjuntos em uma única ação, com acompanhamento por conjunto
- [ ] 4.4 Validar a data contra o calendário de pregão antes de acionar o feeder, informando quando não é dia de pregão
- [ ] 4.5 Retornar o `correlacao_id` da execução criada, ou o da execução em andamento quando já houver uma
- [ ] 4.6 Implementar o acionamento do feeder e a recepção do reporte de resultado

## 5. Carga manual de curva

- [ ] 5.1 Implementar a API de submissão de carga com arquivo, curva, data, momento e justificativa
- [ ] 5.2 Recusar submissão sem justificativa antes de qualquer publicação
- [ ] 5.3 Criar a execução com tipo de disparo de carga manual e faixa prioritária
- [ ] 5.4 Publicar o conteúdo com `source` igual a `MANUAL`, com autor e justificativa
- [ ] 5.5 Permitir a carga dentro da janela de bloqueio, por ser fluxo do dia corrente
- [ ] 5.6 Retornar o `correlacao_id` e refletir o desfecho no monitoramento

## 6. Agendamentos

- [ ] 6.1 Criar a migração da tabela de agendamentos, com alvo, expressão de horário, fuso, janela de tentativa, intervalo e ativação
- [ ] 6.2 Implementar o cadastro, a edição, a ativação e a desativação sem redeploy
- [ ] 6.3 Implementar a validação da expressão de horário no cadastro
- [ ] 6.4 Implementar o carregamento dos agendamentos na inicialização, sem disparo duplicado do mesmo horário
- [ ] 6.5 Implementar a consulta ao calendário de pregão antes do disparo
- [ ] 6.6 Implementar a janela de tentativa com intervalo entre tentativas para dado ainda não divulgado
- [ ] 6.7 Implementar o encerramento em ausência de dado ao esgotar a janela
- [ ] 6.8 Implementar a consulta de agendamentos com última execução e resultado

## 7. Backfill

- [ ] 7.1 Implementar a API de backfill com data inicial e final
- [ ] 7.2 Implementar a execução-mãe com filhas por dia de pregão, pulando dias não úteis
- [ ] 7.3 Implementar o limite de concorrência configurável
- [ ] 7.4 Implementar a interrupção, que para de criar filhas sem abortar as em andamento
- [ ] 7.5 Implementar a consulta de progresso com total, concluídas, sem dado, falhas e pendentes

## 8. Encadeamento até a construção

- [ ] 8.1 Implementar a resolução das definições de curva que consomem um conjunto de dados
- [ ] 8.2 Emitir `curve.build.requested.v1` apenas para definições de modo `BOOTSTRAPPED`, com o mesmo `correlacao_id`
- [ ] 8.3 Não emitir pedido de construção após ingestão de curva pronta nem após ausência de dado
- [ ] 8.4 Atualizar o estado da execução para `CONSTRUINDO` ao emitir o pedido
- [ ] 8.5 Consumir `curve.published.v1` para fechar a execução como concluída

## 9. Resiliência

- [ ] 9.1 Implementar a retentativa com backoff exponencial para falha transitória, até o limite
- [ ] 9.2 Implementar a classificação de falha permanente, sem retentativa, com causa nomeada
- [ ] 9.3 Implementar a reconciliação de execuções presas na inicialização
- [ ] 9.4 Expor métricas de execuções por estado, duração e tentativas
- [ ] 9.5 Implementar log estruturado em JSON com `correlacao_id`, alvo e data de referência

## 10. Pendências de dead-letter

- [ ] 10.1 Criar a migração de `pendencia_dlq` com unicidade de `id_evento` e índice de agrupamento
- [ ] 10.2 Implementar o consumidor de todos os tópicos de dead-letter do catálogo
- [ ] 10.3 Materializar a pendência a partir dos cabeçalhos, sem copiar o payload, guardando as coordenadas na dead-letter
- [ ] 10.4 Garantir idempotência da materialização por `id_evento`, com o offset confirmado só após a gravação
- [ ] 10.5 Implementar o agrupamento por `(motivo, fonte, conjunto_dados, data_referencia)` com contagem e idade
- [ ] 10.6 Implementar a máquina de estados `ABERTA` → `EM_REPROCESSAMENTO` → `RESOLVIDA` / `DESCARTADA` / `OBSOLETA`
- [ ] 10.7 Implementar o fechamento automático da pendência quando o `id_evento` é processado com sucesso
- [ ] 10.8 Implementar o retorno a `ABERTA` com novo motivo e tentativa incrementada quando o reprocessamento falha
- [ ] 10.9 Implementar o reprocessamento individual: reler da dead-letter e republicar no tópico original preservando `id_evento` e `correlacao_id`
- [ ] 10.10 Implementar o reprocessamento de grupo com limite de concorrência e progresso consultável
- [ ] 10.11 Implementar a guarda de obsolescência comparando com o lote mais recente da mesma chave
- [ ] 10.12 Implementar o tratamento de mensagem já expirada na dead-letter
- [ ] 10.13 Implementar o descarte com justificativa obrigatória, autor e instante
- [ ] 10.14 Marcar a execução correspondente como falha com o motivo da dead-letter, e ligar as duas pelo `correlacao_id`
- [ ] 10.15 Implementar a contagem de grupos abertos e a idade da pendência mais antiga, para o alerta
- [ ] 10.16 Implementar a consulta detalhada com filtros, paginação e ordenação pela falha mais antiga
- [ ] 10.17 Garantir que nenhum caminho reprocesse pendência automaticamente

## 11. Consulta e segurança

- [ ] 11.1 Implementar a consulta de execuções com filtro por curva, conjunto de dados, data, estado, tipo de disparo, período e `correlacao_id`
- [ ] 11.2 Implementar paginação e ordenação por início mais recente
- [ ] 11.3 Implementar a autorização: disparo e backfill exigem operador ou administrador; consulta exige apenas autenticação
- [ ] 11.4 Implementar a autorização de pendências: reprocessar e descartar exigem operador ou administrador; consultar exige apenas autenticação
- [ ] 11.5 Implementar a autorização de administrador para gestão de agendamentos

## 12. Testes

- [ ] 12.1 Testar que disparo agendado, manual e backfill produzem execuções no mesmo formato
- [ ] 12.2 Testar a distinção entre ausência de dado e falha, incluindo feriado e dado ainda não divulgado
- [ ] 12.3 Testar a janela de tentativa: sucesso na segunda tentativa e encerramento em ausência de dado ao esgotar
- [ ] 12.4 Testar a prevenção de execução concorrente duplicada, no disparo manual e na coincidência com agendamento
- [ ] 12.5 Testar retentativa de falha transitória e ausência de retentativa em falha permanente
- [ ] 12.6 Testar o encadeamento: pedido de construção só para curvas `BOOTSTRAPPED`
- [ ] 12.7 Testar backfill: filhas por dia de pregão, dias não úteis pulados, limite de concorrência e interrupção
- [ ] 12.8 Testar a reconciliação de execuções presas após reinício
- [ ] 12.9 Testar a durabilidade dos agendamentos através de reinício do serviço
- [ ] 12.10 Testar a autorização de disparo, backfill e gestão de agendamento por perfil
- [ ] 12.11 Testar a materialização idempotente de pendência a partir da dead-letter
- [ ] 12.12 Testar que o banco fora não perde a pendência: offset não confirmado e materialização posterior
- [ ] 12.13 Testar o agrupamento com milhares de mensagens do mesmo motivo, dataset e data
- [ ] 12.14 Testar o fechamento automático da pendência após reprocessamento bem-sucedido
- [ ] 12.15 Testar o fechamento automático quando o reprocessamento vem do offset do tópico de origem
- [ ] 12.16 Testar o grupo resolvido parcialmente, com a contagem diminuindo e o alerta permanecendo
- [ ] 12.17 Testar o retorno a `ABERTA` quando o reprocessamento falha de novo
- [ ] 12.18 Testar a guarda de obsolescência com lote mais recente existente
- [ ] 12.19 Testar a recusa de descarte sem justificativa
- [ ] 12.20 Testar que nenhuma pendência é reprocessada automaticamente
- [ ] 12.21 Testar a contagem do alerta chegando a zero quando não há pendência aberta

## 13. Integração

- [ ] 13.1 Rodar o orquestrador contra o feeder, o Kafka e o banco locais, disparando uma data real de ponta a ponta
- [ ] 13.2 Disparar manualmente os dois tipos de insumo para a mesma data e confirmar as duas curvas publicadas
- [ ] 13.3 Rodar um backfill de uma semana e conferir o progresso e os desfechos
- [ ] 13.4 Provocar uma falha permanente de propósito, conferir o alerta, corrigir a causa, reprocessar e ver o alerta desaparecer
- [ ] 13.5 Documentar em `services/curve-orchestrator/README.md` os agendamentos, o disparo manual, o backfill e a operação de pendências

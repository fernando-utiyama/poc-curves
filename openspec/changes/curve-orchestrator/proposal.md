## Why

O `curve-orchestrator` é quem decide *quando* alguma coisa acontece. Hoje ele dispara os feeders por agendamento cadastrado ou por acionamento em tela, mas sem contrato: não há resposta para o que ocorre quando um feeder falha no meio da noite, quando a B3 ainda não divulgou o dado no horário agendado, quando alguém dispara duas vezes a mesma data, ou quando é preciso carregar um mês inteiro de histórico.

Sem isso, o operador não consegue responder à pergunta que mais importa na mesa: **"a curva de hoje saiu? e se não saiu, onde parou?"**

Esta mudança especifica o orquestrador como o dono do ciclo de vida da execução: agendamentos cadastráveis, disparo manual pela tela para os dois tipos de insumo, backfill de janela de datas, controle de estado com `correlacao_id`, política de retentativa e visibilidade completa do que aconteceu.

## What Changes

- **Cadastro de agendamentos**: por curva ou por conjunto de dados, com expressão de horário, fuso, janela de tentativa e ativação/desativação — sem redeploy.
- **Disparo manual pela tela**, cobrindo explicitamente os dois caminhos de ingestão: **dado individual** (arquivo de preços, BVBG.086, BVBG.028) e **curva pronta** (endpoint de curva da B3). O operador escolhe a data e o que consumir.
- **Backfill de janela de datas** como operação de primeira classe: carregar um intervalo, respeitando o calendário de pregão, com limite de concorrência e possibilidade de interromper.
- **Execução (`execucao_curva`) como entidade central**: estado, `correlacao_id`, tipo de disparo, quem disparou, etapas percorridas, duração, erro e resultado.
- **Distinção entre falha e ausência de dado**: `SEM_DADO` não é erro e não alarma; a execução fica visível como tal e pode ser reagendada dentro da janela.
- **Retentativa com backoff** para falha transitória, com limite; falha permanente encerra a execução com causa nomeada.
- **Encadeamento até a construção**: concluída a ingestão, o orquestrador emite o pedido de construção para as curvas afetadas — e apenas para as de modo `BOOTSTRAPPED`, já que a importada é publicada na própria ingestão.
- **Prevenção de execução concorrente duplicada** para a mesma curva, data e momento.
- **Consulta de execuções** para a tela de monitoramento: filtro por curva, data, estado e `correlacao_id`, com a etapa onde parou.
- **Prazo de publicação conhecido pelo orquestrador**: cada curva tem horário limite, cada execução carrega o tempo restante até ele, e cada etapa tem orçamento. É o que permite responder "vai dar tempo?" antes de virar "não deu".
- **Estado `EM_RISCO` e alerta preditivo**: quando o tempo restante fica menor que o orçamento das etapas que faltam, a execução é sinalizada **antes** de qualquer falha. "Falhou" descoberto depois do corte não serve para nada.
- **Três faixas de ingestão**: agendamento publica na rotina, disparo manual na prioritária, backfill na de massa. O redisparo de algo travado nunca entra atrás da mensagem travada.
- **Janela de bloqueio antes do corte**: backfill e reprocessamento de pendência antiga são pausados na janela crítica. O dia ganha do histórico, por construção e não por disciplina.
- **Retentativa com orçamento de tempo**: o teto é o tempo restante até o corte, não um contador fixo. Retentar dez minutos às 14h é razoável; às 18h50 é irresponsável.
- **Pendências de dead-letter materializadas**: um consumidor lê todos os tópicos de dead-letter e transforma cada mensagem em uma pendência no banco, agrupada por `(motivo, fonte, conjunto de dados, data)`. É isso que alimenta o alerta na tela — o front nunca consulta o Kafka.
- **Alerta que se resolve sozinho**: reprocessar republica a mensagem no tópico original preservando o `id_evento`; quando o processamento dá certo, a pendência é fechada automaticamente e o alerta desaparece, sem o operador precisar marcar nada como feito.
- **Guarda contra reprocessamento obsoleto**: se já existe lote mais recente para a mesma chave, o reprocessamento é recusado e a pendência vira obsoleta, em vez de sobrescrever dado corrigido com dado velho.

## Capabilities

### New Capabilities

- `curve-schedule-registry`: cadastro, edição, ativação e desativação de agendamentos por curva ou conjunto de dados, com expressão de horário, fuso, janela de tentativa e respeito ao calendário de pregão.
- `dlq-pendency-management`: materialização das mensagens de dead-letter como pendências, agrupamento para o alerta, ciclo de vida, resolução automática por reprocessamento bem-sucedido, reprocessamento individual e em grupo, guarda contra replay obsoleto, descarte com justificativa e vínculo com a execução.
- `curve-run-orchestration`: disparo agendado, manual e por backfill nas três faixas; ciclo de vida e estados da execução incluindo `EM_RISCO` e `ATRASADA`; prazo de publicação, orçamento por etapa e alerta preditivo; janela de bloqueio da carga histórica; `correlacao_id`; distinção entre falha e ausência de dado; retentativa com orçamento de tempo; encadeamento até o pedido de construção; prevenção de duplicidade; e consulta de execuções para monitoramento.

### Modified Capabilities

<!-- Nenhuma. -->

## Impact

- **Novo serviço**: `services/curve-orchestrator/` em Java 21 / Spring Boot 3.4.x, sem Lombok.
- **Depende de** `curves-solution-architecture` (modelo de dados, contratos de evento) e conversa com `function-marketdata` para disparar e receber o reporte de resultado.
- **Escreve** em `execucao_curva`, em `pendencia_dlq` e na tabela de agendamentos. Lê `definicao_curva` para saber quais curvas dependem de qual conjunto de dados.
- **Produz** `curve.build.requested.v1` e republica mensagens nos tópicos de origem durante o reprocessamento de pendência.
- **Consome** todos os tópicos de dead-letter do catálogo, para materializar as pendências.
- **Expõe** API HTTP interna consumida pelo `curve-bff` para disparo manual, backfill, consulta de execuções e gestão de pendências de dead-letter; não é exposta ao navegador.
- **Requer agendamento durável**: o agendamento precisa sobreviver a reinício do serviço, então não pode viver apenas em memória.
- **Fora de escopo**: construir ou publicar curva, ingerir dado, e decidir metodologia — tudo isso é de outros componentes.

## ADDED Requirements

### Requirement: Execução como registro central

Todo disparo — agendado, manual ou por backfill — SHALL criar um registro em `execucao_curva` com o mesmo formato, contendo `correlacao_id`, tipo de disparo, quem disparou, alvo, data de referência, momento de curva, estado, carimbos de tempo e, em caso de falha, código e mensagem de erro.

#### Scenario: Disparo agendado registrado

- **WHEN** um agendamento dispara
- **THEN** SHALL existir uma execução com tipo de disparo agendado e `correlacao_id` novo

#### Scenario: Disparo manual registrado

- **WHEN** um operador dispara pela tela
- **THEN** SHALL existir uma execução com tipo de disparo manual e a identidade de quem disparou

#### Scenario: Formato uniforme

- **WHEN** execuções de origens diferentes são listadas
- **THEN** todas SHALL ter o mesmo conjunto de campos, permitindo uma lista única de monitoramento

### Requirement: Disparo manual pela tela para os dois tipos de insumo

O orquestrador SHALL expor uma operação de disparo manual que recebe a data de referência e o que consumir, aceitando tanto os conjuntos de dados de **dado individual** — arquivo de preços, BVBG.086, BVBG.028 — quanto o conjunto de dados de **curva pronta**. O operador SHALL poder disparar um, vários ou todos em uma única ação.

#### Scenario: Disparo manual de dado individual

- **WHEN** o operador seleciona uma data e pede o consumo dos arquivos BVBG
- **THEN** o orquestrador SHALL acionar o feeder para esses conjuntos de dados e SHALL retornar o `correlacao_id` da execução criada

#### Scenario: Disparo manual da curva pronta

- **WHEN** o operador seleciona uma data e pede o consumo do endpoint de curva pronta
- **THEN** o orquestrador SHALL acionar o feeder para esse conjunto de dados, e a curva importada SHALL ser publicada ao fim do fluxo

#### Scenario: Disparo dos dois de uma vez

- **WHEN** o operador pede ambos para a mesma data
- **THEN** as duas ingestões SHALL ser disparadas, e o acompanhamento SHALL mostrar o progresso de cada uma

#### Scenario: Data que não é dia de pregão

- **WHEN** o operador dispara manualmente para uma data que não é dia de pregão
- **THEN** o orquestrador SHALL informar isso explicitamente antes de acionar o feeder, e a execução SHALL terminar como ausência de dado

### Requirement: Prazo de publicação e orçamento por etapa

A execução SHALL registrar o horário limite de publicação aplicável, derivado da definição de curva, e SHALL manter o tempo restante até esse limite. Cada etapa concluída SHALL ter sua duração efetiva registrada.

#### Scenario: Tempo restante disponível

- **WHEN** uma execução está em andamento
- **THEN** o tempo restante até o horário limite SHALL ser consultável

#### Scenario: Duração por etapa registrada

- **WHEN** uma etapa da execução conclui
- **THEN** a sua duração efetiva SHALL ser persistida, permitindo calibrar o orçamento com série medida

#### Scenario: Margem registrada no encerramento

- **WHEN** uma execução conclui com publicação
- **THEN** a margem em relação ao horário limite SHALL ser persistida

### Requirement: Estado de risco e alerta preditivo

Quando o tempo restante até o horário limite ficar menor que o orçamento das etapas ainda não concluídas, a execução SHALL ser marcada como `EM_RISCO` e um alerta SHALL ser emitido — antes de qualquer falha ocorrer.

#### Scenario: Execução entra em risco

- **WHEN** o tempo restante fica menor que o orçamento do que falta
- **THEN** a execução SHALL passar a `EM_RISCO`, e o alerta SHALL identificar a curva, a etapa atual e o tempo restante

#### Scenario: Risco não depende de falha

- **WHEN** nenhuma falha ocorreu, mas o ritmo indica que o prazo não será cumprido
- **THEN** o alerta SHALL ser emitido mesmo assim

#### Scenario: Saída do risco

- **WHEN** uma execução em risco conclui a etapa e volta a caber no orçamento
- **THEN** ela SHALL deixar o estado `EM_RISCO`

#### Scenario: Prazo ultrapassado

- **WHEN** o horário limite é atingido sem publicação
- **THEN** a execução SHALL ser marcada como `ATRASADA`, distinta de falha, e SHALL continuar se ainda houver possibilidade de concluir

### Requirement: Faixa de ingestão por origem do disparo

O orquestrador SHALL publicar na faixa de **rotina** os disparos agendados, na faixa **prioritária** todos os disparos manuais, e na faixa de **massa** as cargas históricas. A faixa SHALL ser registrada na execução.

#### Scenario: Disparo manual sempre prioritário

- **WHEN** uma ingestão é disparada manualmente
- **THEN** ela SHALL usar a faixa prioritária, independentemente de haver ou não execução travada na rotina

#### Scenario: Redisparo de ingestão travada

- **WHEN** o operador redispara uma ingestão cuja execução anterior está travada
- **THEN** a nova execução SHALL usar a faixa prioritária e SHALL ser processada sem depender do desbloqueio da anterior

#### Scenario: Faixa registrada

- **WHEN** uma execução é consultada
- **THEN** a faixa usada SHALL constar no registro

### Requirement: Janela de bloqueio da carga histórica

O orquestrador SHALL suspender a faixa de massa durante a janela de bloqueio anterior ao horário limite. Backfill e reprocessamento de pendência antiga MUST NOT consumir capacidade dentro dessa janela.

#### Scenario: Backfill pausado ao entrar na janela

- **WHEN** a janela de bloqueio começa com backfill em andamento
- **THEN** ele SHALL ser pausado e retomado após o fim da janela, preservando o progresso

#### Scenario: Solicitação dentro da janela

- **WHEN** um backfill é solicitado dentro da janela de bloqueio
- **THEN** a solicitação SHALL ser recusada ou agendada para após a janela, informando o motivo

#### Scenario: Rotina e prioritária não são afetadas

- **WHEN** a faixa de massa está pausada
- **THEN** rotina e prioritária SHALL continuar operando normalmente

### Requirement: Carga manual de curva como execução

O orquestrador SHALL aceitar a submissão de uma curva por arquivo, criar a execução correspondente com tipo de disparo de carga manual e faixa prioritária, e publicar o conteúdo para processamento com `source` igual a `MANUAL`, carregando autor e justificativa.

#### Scenario: Carga submetida

- **WHEN** um operador submete um arquivo de curva com justificativa
- **THEN** SHALL ser criada uma execução na faixa prioritária, e a resposta SHALL trazer o `correlacao_id` para acompanhamento

#### Scenario: Carga sem justificativa

- **WHEN** a submissão não traz justificativa
- **THEN** ela SHALL ser recusada antes de qualquer publicação, e nenhuma execução SHALL ser criada

#### Scenario: Carga sempre prioritária

- **WHEN** uma carga é submetida
- **THEN** ela SHALL usar a faixa prioritária, porque é intervenção com alguém esperando na tela

#### Scenario: Carga durante a janela de bloqueio

- **WHEN** uma carga é submetida dentro da janela de bloqueio
- **THEN** ela SHALL ser aceita, porque é fluxo do dia corrente e não carga histórica

### Requirement: Estados da execução

A execução SHALL transitar entre `PENDENTE`, `EXECUTANDO`, `CONSTRUINDO`, `EM_RISCO`, `ATRASADA`, `CONCLUIDA`, `SEM_DADO` e `FALHOU`. `SEM_DADO` SHALL ser um estado terminal legítimo, distinto de falha, e MUST NOT ser reportado como erro. `EM_RISCO` e `ATRASADA` SHALL ser estados de execução ainda em andamento, e MUST NOT ser reportados como falha.

#### Scenario: Ausência de dado não é falha

- **WHEN** a fonte responde corretamente que não há dado para a data
- **THEN** a execução SHALL terminar em `SEM_DADO` com o motivo, e MUST NOT aparecer como falha no monitoramento

#### Scenario: Falha de fonte

- **WHEN** a fonte está indisponível
- **THEN** a execução SHALL terminar em `FALHOU` com código e mensagem, nomeando fonte e recurso

#### Scenario: Progresso visível

- **WHEN** a execução avança de etapa
- **THEN** o estado SHALL ser atualizado, permitindo ver em qual etapa ela está

### Requirement: Propagação e geração de correlation id

O orquestrador SHALL gerar o `correlacao_id` no início de cada execução e SHALL transmiti-lo ao feeder e a todo evento derivado. Componentes a jusante MUST NOT gerar identificador próprio.

#### Scenario: Rastreio ponta a ponta

- **WHEN** uma execução percorre feeder, processor e motor
- **THEN** todos os registros e eventos SHALL carregar o mesmo `correlacao_id`, e a consulta por ele SHALL trazer a execução completa

### Requirement: Prevenção de execução concorrente duplicada

Antes de criar uma execução, o orquestrador SHALL verificar se já existe execução ativa para o mesmo alvo, data de referência e momento. Existindo, o novo disparo SHALL ser associado à execução em andamento em vez de criar uma concorrente.

#### Scenario: Operador dispara duas vezes

- **WHEN** o operador dispara a mesma data duas vezes em sequência
- **THEN** a segunda ação SHALL informar que já existe execução em andamento e mostrar o seu progresso, sem criar execução nova

#### Scenario: Agendamento coincide com disparo manual

- **WHEN** o agendamento dispara enquanto uma execução manual da mesma data está ativa
- **THEN** apenas uma execução SHALL permanecer, sem trabalho duplicado a jusante

### Requirement: Retentativa e classificação de falha

Falha transitória SHALL ser retentada com backoff exponencial até o limite configurado, e o teto de retentativa SHALL ser adicionalmente limitado por uma fração do tempo restante até o horário limite de publicação. Falha permanente — configuração inválida, conjunto de dados inexistente, erro de validação — MUST NOT ser retentada e SHALL encerrar a execução com a causa nomeada.

#### Scenario: Falha transitória recuperada

- **WHEN** a primeira tentativa falha por indisponibilidade e a seguinte tem sucesso
- **THEN** a execução SHALL concluir com sucesso, registrando o número de tentativas

#### Scenario: Falha permanente

- **WHEN** o disparo referencia um conjunto de dados inexistente
- **THEN** a execução SHALL falhar na primeira tentativa, nomeando o conjunto de dados solicitado

#### Scenario: Retentativa perto do corte

- **WHEN** uma falha transitória ocorre com pouco tempo restante até o horário limite
- **THEN** o teto de retentativa SHALL ser reduzido pelo tempo restante, e a execução SHALL falhar cedo em vez de consumir o que sobrou

#### Scenario: Retentativa longe do corte

- **WHEN** a mesma falha ocorre com folga até o horário limite
- **THEN** a retentativa SHALL usar o teto padrão da política

### Requirement: Encadeamento até o pedido de construção

Concluída a ingestão de um conjunto de dados, o orquestrador SHALL identificar as definições de curva que o consomem e SHALL emitir `curve.build.requested.v1` apenas para as de modo de origem `BOOTSTRAPPED`. Definições `IMPORTED` MUST NOT receber pedido de construção.

#### Scenario: Insumo pronto dispara construção

- **WHEN** a ingestão de dado individual conclui para uma data
- **THEN** o pedido de construção SHALL ser emitido para cada curva construída que consome aquele conjunto de dados, com o mesmo `correlacao_id`

#### Scenario: Curva importada não é reconstruída

- **WHEN** a ingestão de curva pronta conclui
- **THEN** nenhum pedido de construção SHALL ser emitido, porque a curva já foi publicada durante a ingestão

#### Scenario: Ingestão sem dado não dispara construção

- **WHEN** a ingestão termina em ausência de dado
- **THEN** nenhum pedido de construção SHALL ser emitido

### Requirement: Backfill de janela de datas

O orquestrador SHALL aceitar um pedido de backfill com data inicial e final, criando uma execução-mãe e uma execução filha por dia de pregão do intervalo, com limite de concorrência configurável. O backfill SHALL poder ser interrompido.

#### Scenario: Backfill de um intervalo

- **WHEN** um backfill é solicitado para um intervalo de datas
- **THEN** SHALL ser criada uma execução por dia de pregão do intervalo, respeitando o limite de concorrência

#### Scenario: Dias não úteis pulados

- **WHEN** o intervalo contém fins de semana e feriados
- **THEN** nenhuma execução filha SHALL ser criada para essas datas

#### Scenario: Interrupção

- **WHEN** um backfill em andamento é interrompido
- **THEN** nenhuma execução filha nova SHALL ser criada, as em andamento SHALL concluir, e o progresso SHALL ficar registrado

#### Scenario: Progresso do backfill

- **WHEN** o backfill é consultado
- **THEN** SHALL informar total de datas, concluídas, sem dado, falhas e pendentes

### Requirement: Reconciliação de execuções presas

Na inicialização, o orquestrador SHALL identificar execuções em estado não terminal há mais tempo que o limite configurado e SHALL encerrá-las como falha com causa explícita de interrupção.

#### Scenario: Serviço caiu no meio de uma execução

- **WHEN** o serviço reinicia e encontra execuções paradas em estado intermediário além do limite
- **THEN** elas SHALL ser encerradas com causa de interrupção, e MUST NOT permanecer indefinidamente como em andamento

### Requirement: Consulta de execuções para monitoramento

O orquestrador SHALL expor consulta de execuções com filtro por curva, conjunto de dados, data de referência, estado, tipo de disparo, período e `correlacao_id`, com paginação e ordenação por início mais recente.

#### Scenario: Onde a curva de hoje parou

- **WHEN** o operador filtra por curva e data de hoje
- **THEN** a resposta SHALL mostrar a execução, seu estado, a etapa em que está ou parou e a causa da falha quando houver

#### Scenario: Busca por correlation id

- **WHEN** o operador informa um `correlacao_id`
- **THEN** a resposta SHALL trazer a execução correspondente e suas etapas

### Requirement: Autorização de disparo

Disparo manual e backfill SHALL exigir perfil de operador ou de administrador de curva. Consultar execuções SHALL ser permitido a qualquer usuário autenticado.

#### Scenario: Leitor tenta disparar

- **WHEN** um usuário com perfil de leitor tenta disparar uma ingestão
- **THEN** a operação SHALL ser recusada por falta de autorização, e nenhuma execução SHALL ser criada

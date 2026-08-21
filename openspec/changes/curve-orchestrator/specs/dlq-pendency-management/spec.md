## ADDED Requirements

### Requirement: Materialização de pendência a partir da dead-letter

O orquestrador SHALL consumir todos os tópicos de dead-letter do catálogo e SHALL materializar cada mensagem como uma pendência em `pendencia_dlq`, com motivo, detalhe, `id_evento`, `correlacao_id`, tópico, partição e offset de origem, tópico de dead-letter e suas coordenadas, fonte, conjunto de dados, data de referência, momento da falha e versão da aplicação que falhou.

A materialização SHALL ser idempotente por `id_evento`: consumir a mesma mensagem de dead-letter mais de uma vez MUST NOT criar pendência duplicada.

#### Scenario: Mensagem cai na dead-letter

- **WHEN** um consumidor envia uma mensagem para a dead-letter
- **THEN** SHALL ser criada uma pendência no estado `ABERTA` com todos os campos de contexto preenchidos

#### Scenario: Banco indisponível na materialização

- **WHEN** o banco está fora no momento em que a mensagem de dead-letter é consumida
- **THEN** o offset MUST NOT ser confirmado, e a pendência SHALL ser materializada quando o banco voltar, sem perda

#### Scenario: Consumo duplicado da dead-letter

- **WHEN** a mesma mensagem de dead-letter é consumida duas vezes
- **THEN** SHALL existir exatamente uma pendência para aquele `id_evento`

#### Scenario: Payload não é copiado para o banco

- **WHEN** uma pendência é materializada
- **THEN** ela SHALL guardar as coordenadas da mensagem na dead-letter, e MUST NOT copiar o payload original para o banco

### Requirement: Severidade da pendência pelo impacto no fechamento

A severidade de uma pendência SHALL ser derivada primeiro do seu impacto no fechamento do dia corrente — se ela bloqueia a publicação de uma curva cujo horário limite ainda não passou — e só depois da sua idade.

#### Scenario: Pendência que bloqueia a curva de hoje

- **WHEN** uma pendência impede a publicação de uma curva cujo horário limite é hoje e ainda não passou
- **THEN** ela SHALL receber a severidade máxima, ainda que tenha surgido há poucos minutos

#### Scenario: Pendência antiga sem impacto no dia

- **WHEN** uma pendência de data antiga não afeta nenhuma curva do dia corrente
- **THEN** ela SHALL receber severidade menor que a de uma pendência que bloqueia o fechamento de hoje

### Requirement: Pendência de evento já processado

Ao materializar uma pendência, o orquestrador SHALL verificar se aquele `id_evento` já foi processado com sucesso por outra tentativa. Em caso positivo, a pendência SHALL ser registrada diretamente como `RESOLVIDA`, com o motivo de já ter sido processada, e MUST NOT contar para o alerta.

#### Scenario: Redisparo prioritário resolveu antes

- **WHEN** o operador redispara na faixa prioritária e o processamento conclui antes de a mensagem travada chegar à dead-letter
- **THEN** a pendência resultante SHALL nascer `RESOLVIDA`, preservando o rastro sem gerar alerta falso

#### Scenario: Evento ainda não processado

- **WHEN** o `id_evento` não consta como processado com sucesso
- **THEN** a pendência SHALL ser criada como `ABERTA` normalmente

### Requirement: Reprocessamento fora da janela crítica

O reprocessamento de pendência cujo impacto não é o fechamento do dia corrente SHALL respeitar a janela de bloqueio e MUST NOT consumir capacidade dentro dela.

#### Scenario: Reprocessamento de pendência antiga na janela

- **WHEN** o reprocessamento de uma pendência sem impacto no dia é solicitado dentro da janela de bloqueio
- **THEN** ele SHALL ser recusado ou agendado para após a janela, informando o motivo

#### Scenario: Pendência que bloqueia hoje

- **WHEN** a pendência bloqueia a publicação de uma curva do dia corrente
- **THEN** o reprocessamento SHALL ser permitido dentro da janela, porque é o próprio fluxo do dia

### Requirement: Agrupamento de pendências

O orquestrador SHALL agrupar as pendências abertas por `(motivo, fonte, conjunto_dados, data_referencia)`, expondo por grupo a quantidade de mensagens, o instante da primeira e da última falha, e um detalhe representativo. O alerta SHALL ser contado por grupo, e não por mensagem.

#### Scenario: Falha sistêmica de parser

- **WHEN** um erro de parser faz milhares de mensagens do mesmo conjunto de dados e data caírem na dead-letter
- **THEN** SHALL ser exposto um único grupo com a contagem das mensagens, e não um item por mensagem

#### Scenario: Motivos distintos não se misturam

- **WHEN** existem pendências com motivos diferentes para o mesmo conjunto de dados e data
- **THEN** cada motivo SHALL formar um grupo próprio, porque a correção é diferente para cada um

### Requirement: Ciclo de vida da pendência

Uma pendência SHALL transitar entre `ABERTA`, `EM_REPROCESSAMENTO`, `RESOLVIDA`, `DESCARTADA` e `OBSOLETA`. Apenas `ABERTA` e `EM_REPROCESSAMENTO` SHALL contar para o alerta.

#### Scenario: Estados que alertam

- **WHEN** a contagem do alerta é calculada
- **THEN** SHALL considerar apenas pendências em `ABERTA` e `EM_REPROCESSAMENTO`

#### Scenario: Histórico preservado

- **WHEN** uma pendência é resolvida ou descartada
- **THEN** ela SHALL permanecer consultável com o desfecho, o instante e o responsável

### Requirement: Resolução automática por reprocessamento bem-sucedido

Quando um evento é processado com sucesso, o consumidor SHALL fechar a pendência aberta correspondente ao mesmo `id_evento`, marcando-a como `RESOLVIDA` com o instante e o `correlacao_id` do processamento. A resolução MUST NOT depender de ação manual do operador.

#### Scenario: Alerta some após reprocessamento

- **WHEN** uma mensagem reprocessada a partir da pendência é processada com sucesso
- **THEN** a pendência SHALL passar a `RESOLVIDA`, e o alerta correspondente SHALL deixar de ser contado

#### Scenario: Correção pela origem também resolve

- **WHEN** o operador corrige a causa e reprocessa a partir do tópico de origem, em vez de a partir da pendência
- **THEN** as pendências dos eventos reprocessados com sucesso SHALL ser resolvidas automaticamente, porque a correspondência é pelo `id_evento`

#### Scenario: Grupo resolvido parcialmente

- **WHEN** parte das mensagens de um grupo é reprocessada com sucesso
- **THEN** a contagem do grupo SHALL diminuir, e o grupo SHALL deixar de alertar apenas quando não restar nenhuma pendência aberta nele

#### Scenario: Reprocessamento falha de novo

- **WHEN** o reprocessamento falha novamente
- **THEN** a pendência SHALL voltar ao estado `ABERTA` com o novo motivo e a contagem de tentativas incrementada, e o alerta SHALL permanecer

### Requirement: Reprocessamento a partir da pendência

O orquestrador SHALL permitir reprocessar uma pendência individual ou um grupo inteiro. O reprocessamento SHALL reler a mensagem da dead-letter pelas coordenadas registradas e republicá-la **no tópico original**, preservando `id_evento`, `correlacao_id` e o payload intactos. O orquestrador MUST NOT injetar a mensagem diretamente no consumidor, nem alterar o conteúdo.

#### Scenario: Reprocessamento individual

- **WHEN** o operador manda reprocessar uma pendência
- **THEN** a mensagem SHALL ser republicada no tópico original com os identificadores originais, e a pendência SHALL passar a `EM_REPROCESSAMENTO`

#### Scenario: Reprocessamento de grupo

- **WHEN** o operador manda reprocessar um grupo inteiro
- **THEN** todas as pendências abertas do grupo SHALL ser republicadas, com limite de concorrência, e o progresso SHALL ser consultável

#### Scenario: Validação preservada

- **WHEN** uma mensagem é reprocessada
- **THEN** ela SHALL passar por toda a validação do fluxo normal, e não por um caminho especial

#### Scenario: Mensagem expirada na dead-letter

- **WHEN** a mensagem já não está disponível na dead-letter por expiração de retenção
- **THEN** o reprocessamento SHALL falhar informando isso explicitamente, e a pendência SHALL permanecer aberta com o motivo registrado

### Requirement: Guarda contra reprocessamento obsoleto

Antes de republicar, o orquestrador SHALL verificar se já existe lote de ingestão mais recente para a mesma `(fonte, conjunto_dados, data_referencia)` do que a falha registrada na pendência. Havendo, o reprocessamento SHALL ser recusado e a pendência SHALL ser marcada como `OBSOLETA`, porque republicar sobrescreveria dado mais novo com dado velho.

#### Scenario: Fonte já republicou dado corrigido

- **WHEN** uma pendência antiga é reprocessada e já existe lote mais recente para a mesma chave
- **THEN** o reprocessamento SHALL ser recusado, a pendência SHALL ficar `OBSOLETA` com a explicação, e nada SHALL ser republicado

#### Scenario: Nenhum lote mais novo

- **WHEN** não existe lote mais recente para a chave
- **THEN** o reprocessamento SHALL prosseguir normalmente

### Requirement: Descarte explícito

O orquestrador SHALL permitir descartar uma pendência ou um grupo, exigindo justificativa textual. O descarte SHALL registrar quem descartou, quando e por quê. Pendência descartada MUST NOT voltar a alertar.

#### Scenario: Descarte com justificativa

- **WHEN** o operador descarta uma pendência informando a justificativa
- **THEN** ela SHALL passar a `DESCARTADA` com autor, instante e justificativa, e SHALL sair do alerta

#### Scenario: Descarte sem justificativa

- **WHEN** o descarte é solicitado sem justificativa
- **THEN** a operação SHALL ser recusada

### Requirement: Ausência de reprocessamento automático

O orquestrador MUST NOT reprocessar pendências automaticamente. Reprocessamento SHALL ser sempre acionado de forma explícita por um usuário autorizado.

#### Scenario: Pendência não é reenviada sozinha

- **WHEN** uma pendência permanece aberta por qualquer período
- **THEN** o orquestrador MUST NOT republicá-la por conta própria, para não criar laço de falha e reenvio

### Requirement: Vínculo entre pendência e execução

A pendência SHALL referenciar, pelo `correlacao_id`, a execução que a originou, e a execução correspondente SHALL ser marcada como falha com o motivo da dead-letter. O operador SHALL conseguir navegar de uma para a outra.

#### Scenario: Execução reflete a falha

- **WHEN** uma mensagem de uma execução cai na dead-letter
- **THEN** a execução SHALL registrar falha com o mesmo motivo, visível no monitor de execuções

#### Scenario: Navegação entre os dois

- **WHEN** o operador está vendo uma pendência
- **THEN** SHALL conseguir abrir a execução correspondente, e vice-versa

### Requirement: Consulta de pendências e contagem para alerta

O orquestrador SHALL expor a contagem de grupos com pendência aberta, para o alerta, e a consulta detalhada com filtro por motivo, fonte, conjunto de dados, data de referência, estado, período e `correlacao_id`, com paginação e ordenação por falha mais antiga.

#### Scenario: Contagem para o alerta

- **WHEN** a contagem é consultada
- **THEN** SHALL retornar o número de grupos com pendência aberta e a idade da pendência aberta mais antiga

#### Scenario: Pendência mais antiga em destaque

- **WHEN** a lista de pendências é consultada sem ordenação explícita
- **THEN** SHALL vir ordenada da falha mais antiga para a mais recente

#### Scenario: Nenhuma pendência aberta

- **WHEN** não há pendência aberta
- **THEN** a contagem SHALL ser zero, e o alerta SHALL deixar de ser exibido

### Requirement: Autorização sobre pendências

Consultar pendências SHALL ser permitido a qualquer usuário autenticado. Reprocessar e descartar SHALL exigir perfil de operador ou de administrador de curva.

#### Scenario: Leitor vê mas não age

- **WHEN** um usuário com perfil de leitor consulta as pendências
- **THEN** a consulta SHALL ser atendida, e as ações de reprocessar e descartar SHALL ser recusadas por falta de autorização

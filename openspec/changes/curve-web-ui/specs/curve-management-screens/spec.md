## ADDED Requirements

### Requirement: Painel do dia

A tela SHALL apresentar, para uma data de referência, uma linha por curva ativa, com o estado de publicação, o horário limite, o tempo restante ou a margem obtida, e a etapa atual quando em andamento. SHALL ser a tela inicial do app.

#### Scenario: A curva de hoje saiu?

- **WHEN** o painel é aberto para hoje
- **THEN** o estado de cada curva SHALL ser legível de relance, sem exigir navegação

#### Scenario: Curva em risco antes de falhar

- **WHEN** uma execução está em risco de não cumprir o prazo
- **THEN** a linha SHALL ser destacada como em risco, com o tempo restante, ainda que nenhuma falha tenha ocorrido

#### Scenario: Curva atrasada

- **WHEN** o horário limite passou sem publicação
- **THEN** a linha SHALL ser marcada como atrasada, de forma distinta de falha

#### Scenario: Curva reprovada na validação

- **WHEN** a versão do dia foi reprovada no gate de consistência
- **THEN** a linha SHALL indicar a reprovação e permitir ver quais testes falharam, e SHALL deixar claro qual versão permanece vigente

#### Scenario: Curva publicada com aviso

- **WHEN** a curva foi publicada com teste de aviso reprovado
- **THEN** a linha SHALL indicar o aviso sem sugerir falha

#### Scenario: Curva não iniciada

- **WHEN** o agendamento ainda não disparou
- **THEN** a linha SHALL mostrar o horário previsto, distinta de curva com problema

#### Scenario: Redisparo a partir do painel

- **WHEN** uma curva está atrasada, travada ou falhou
- **THEN** a linha SHALL oferecer o redisparo da ingestão, sujeito ao perfil do usuário

### Requirement: Tela de catálogo de curvas

A tela SHALL listar as definições de curva com código, nome, moeda, modo de origem, estado, versão de definição vigente e modelo apontado, com filtro e paginação.

#### Scenario: Distinguir as duas origens

- **WHEN** o catálogo é aberto com curvas construídas e importadas
- **THEN** o modo de origem de cada uma SHALL ser visível na listagem

#### Scenario: Navegar para a curva

- **WHEN** o usuário seleciona uma curva do catálogo
- **THEN** a tela de curva SHALL abrir para aquela definição

### Requirement: Tela de cadastro e edição de curva

A tela SHALL permitir configurar código, nome, moeda, modo de origem, contagem de dias, calendário, interpolador, política de extrapolação, política de arredondamento, vínculos de fonte, dependências e o modelo de construção. A tela SHALL deixar explícito que salvar cria uma nova versão da definição.

#### Scenario: Aviso de versionamento

- **WHEN** o usuário edita uma definição existente
- **THEN** a tela SHALL informar que a alteração criará uma nova versão da definição, preservando a anterior

#### Scenario: Erro de validação do backend

- **WHEN** o backend recusa o salvamento por incoerência
- **THEN** a mensagem SHALL ser exibida junto ao campo correspondente quando identificável, e no formulário quando não

#### Scenario: Escolha do modelo de construção

- **WHEN** a definição é de modo construído
- **THEN** a tela SHALL permitir escolher entre o modelo embutido padrão e os modelos Groovy importados disponíveis

#### Scenario: Prazo e limites de validação no cadastro

- **WHEN** a tela de cadastro é aberta
- **THEN** SHALL permitir informar o horário limite de publicação, o orçamento por etapa, a janela de bloqueio, os limites de cada teste de validação e a classificação de cada teste entre bloqueante e aviso

#### Scenario: Curva importada não escolhe modelo

- **WHEN** a definição é de modo importado
- **THEN** o campo de modelo SHALL estar indisponível, com a explicação de que não há cálculo

### Requirement: Viewer de curva

A tela SHALL exibir a curva publicada em gráfico e em tabela de vértices, com seletor de data de referência, momento de curva e versão — incluindo seleção por instante. A tabela SHALL suportar centenas de vértices sem degradar a navegação.

#### Scenario: Curva com muitos vértices

- **WHEN** uma curva com centenas de vértices é aberta
- **THEN** a tabela SHALL permanecer navegável e responsiva à rolagem

#### Scenario: Seleção de versão histórica

- **WHEN** o usuário escolhe uma versão anterior ou um instante passado
- **THEN** a tela SHALL exibir aquela versão e indicar claramente que não é a corrente

#### Scenario: Sem curva publicada na data

- **WHEN** não há versão publicada para a data escolhida
- **THEN** a tela SHALL informar a ausência explicitamente, e MUST NOT exibir a curva de outra data

### Requirement: Resultado da validação junto da curva

A tela de curva SHALL exibir o resultado da validação de consistência da versão apresentada: os testes executados, o resultado de cada um, a medida observada e o limite aplicado, com os avisos destacados.

#### Scenario: Curva aprovada

- **WHEN** a versão exibida passou em todos os testes
- **THEN** a tela SHALL indicar a aprovação e permitir consultar os resultados

#### Scenario: Curva publicada com aviso

- **WHEN** a versão foi publicada com teste de aviso reprovado
- **THEN** o aviso SHALL ser exibido junto da curva, com o teste e a medida observada

#### Scenario: Versão reprovada

- **WHEN** o usuário consulta uma versão reprovada
- **THEN** a tela SHALL exibir os testes que falharam e deixar claro que essa versão não foi publicada

### Requirement: Procedência exibida junto da curva

A tela de curva SHALL exibir, junto dos dados, a versão apresentada, o instante de publicação, a execução que a gerou e o modelo que a produziu — ou, para curva importada, o arquivo de origem.

#### Scenario: Origem do número acessível

- **WHEN** o usuário está olhando os vértices
- **THEN** a procedência SHALL estar visível na mesma tela, sem exigir navegação para outra

#### Scenario: Curva importada

- **WHEN** a curva exibida é importada
- **THEN** a procedência SHALL identificar o arquivo de origem e o lote de ingestão, e indicar que não houve cálculo

### Requirement: Consulta interpolada

A tela SHALL permitir consultar um prazo arbitrário ou uma lista de prazos, exibindo a taxa e o fator de desconto e identificando a versão de curva usada.

#### Scenario: Lista de prazos

- **WHEN** o usuário informa vários prazos
- **THEN** os resultados SHALL ser exibidos na ordem informada, todos da mesma versão

#### Scenario: Ponto extrapolado

- **WHEN** um prazo cai fora do intervalo dos vértices sob política permissiva
- **THEN** o resultado SHALL ser exibido com sinalização explícita de extrapolação

#### Scenario: Política estrita

- **WHEN** um prazo cai fora do intervalo sob política estrita
- **THEN** a tela SHALL exibir o erro nomeando o prazo pedido e o intervalo disponível, sem invalidar os demais prazos consultados

### Requirement: Tela de disparo manual de ingestão

A tela SHALL permitir escolher a data de referência e marcar o que consumir — os conjuntos de **dado individual** (arquivo de preços, BVBG.086, BVBG.028) e/ou o conjunto de **curva pronta** da B3 —, disparar e acompanhar o resultado.

#### Scenario: Disparo dos arquivos BVBG

- **WHEN** o operador marca os arquivos de dado individual e dispara
- **THEN** a ingestão SHALL ser iniciada e a tela SHALL exibir o `correlacao_id` de forma copiável

#### Scenario: Disparo da curva pronta

- **WHEN** o operador marca o conjunto de curva pronta e dispara
- **THEN** a ingestão SHALL ser iniciada, e ao final a curva importada SHALL aparecer publicada no viewer

#### Scenario: Disparo dos dois de uma vez

- **WHEN** o operador marca os dois tipos para a mesma data
- **THEN** ambos SHALL ser disparados e o progresso de cada um SHALL ser exibido separadamente

#### Scenario: Data que não é dia de pregão

- **WHEN** o operador escolhe uma data que não é dia de pregão
- **THEN** a tela SHALL avisar antes do disparo

#### Scenario: Execução já em andamento

- **WHEN** já existe execução ativa para a data escolhida
- **THEN** a tela SHALL mostrar a execução existente e seu progresso, em vez de criar outra

### Requirement: Backfill pela tela

A tela SHALL permitir solicitar um backfill informando data inicial e final, exibindo o progresso por data e permitindo interromper.

#### Scenario: Progresso do backfill

- **WHEN** um backfill está em andamento
- **THEN** a tela SHALL exibir total, concluídas, sem dado, falhas e pendentes

#### Scenario: Interromper backfill

- **WHEN** o operador interrompe o backfill
- **THEN** a tela SHALL refletir a interrupção e indicar que as execuções em andamento serão concluídas

### Requirement: Monitor de execuções

A tela SHALL listar as execuções com estado, alvo, data de referência, tipo de disparo, quem disparou, duração, `correlacao_id` e causa da falha, com filtros por curva, data, estado e `correlacao_id`.

#### Scenario: Onde a curva de hoje parou

- **WHEN** o operador filtra pela curva e pela data de hoje
- **THEN** a tela SHALL mostrar a execução e a etapa em que ela está ou parou

#### Scenario: Ausência de dado não é erro

- **WHEN** uma execução terminou por ausência de dado
- **THEN** ela SHALL ser exibida com tratamento visual próprio, distinto de falha, com o motivo

#### Scenario: Redisparo entra na faixa prioritária

- **WHEN** o operador redispara uma ingestão cuja execução está travada
- **THEN** a tela SHALL indicar que a nova tentativa é prioritária e SHALL exibir o novo `correlacao_id`, mostrando as duas tentativas vinculadas ao mesmo alvo e data

#### Scenario: Redisparo a partir do monitor

- **WHEN** o operador escolhe redisparar uma execução que falhou
- **THEN** a tela SHALL disparar novamente para o mesmo alvo e data

### Requirement: Alerta global de pendência de dead-letter

O app SHALL exibir, em **todas as telas**, um indicador de pendências de dead-letter abertas, mostrando a quantidade de grupos e destacando a idade da pendência mais antiga. O indicador SHALL ser atualizado periodicamente sem ação do usuário, e SHALL levar à tela de pendências quando acionado.

#### Scenario: Algo cai na dead-letter

- **WHEN** uma mensagem cai na dead-letter enquanto o usuário está em qualquer tela
- **THEN** o indicador SHALL passar a ser exibido na atualização seguinte, com a contagem de grupos abertos

#### Scenario: Alerta desaparece após reprocessamento

- **WHEN** todas as pendências são resolvidas por reprocessamento bem-sucedido
- **THEN** o indicador SHALL deixar de ser exibido automaticamente, sem o usuário precisar dar baixa em nada

#### Scenario: Resolução parcial

- **WHEN** parte das pendências de um grupo é resolvida
- **THEN** a contagem exibida SHALL diminuir, e o indicador SHALL permanecer enquanto restar grupo aberto

#### Scenario: Sem pendência

- **WHEN** não há pendência aberta
- **THEN** nenhum indicador SHALL ser exibido, e a ausência MUST NOT ser representada por um contador zerado permanente

#### Scenario: Envelhecimento destacado

- **WHEN** existe pendência aberta há mais tempo que o limite configurado
- **THEN** o indicador SHALL destacar visualmente essa condição, porque idade importa mais que quantidade

#### Scenario: Visível para qualquer perfil

- **WHEN** um usuário com perfil de leitor usa o app
- **THEN** o indicador SHALL ser exibido normalmente, ainda que as ações de correção não estejam disponíveis para ele

#### Scenario: Alerta indisponível

- **WHEN** a consulta do alerta falha
- **THEN** o app MUST NOT exibir o indicador como zerado, e SHALL sinalizar que o estado do alerta é desconhecido

### Requirement: Tela de pendências de dead-letter

A tela SHALL listar os grupos de pendência com motivo, fonte, conjunto de dados, data de referência, quantidade de mensagens, instante da primeira e da última falha e um detalhe representativo, ordenados da falha mais antiga para a mais recente, e SHALL permitir expandir um grupo para ver as mensagens individuais.

#### Scenario: Falha sistêmica agrupada

- **WHEN** milhares de mensagens do mesmo motivo, conjunto de dados e data estão pendentes
- **THEN** a tela SHALL exibir um único grupo com a contagem, e não uma linha por mensagem

#### Scenario: Detalhe do grupo

- **WHEN** o usuário expande um grupo
- **THEN** SHALL ver as pendências individuais com `id_evento`, `correlacao_id` e o detalhe da falha

#### Scenario: Navegar para a execução

- **WHEN** o usuário aciona o `correlacao_id` de uma pendência
- **THEN** a execução correspondente SHALL ser aberta no monitor de execuções

### Requirement: Ações de correção na tela de pendências

A tela SHALL permitir reprocessar uma pendência, reprocessar um grupo inteiro e descartar com justificativa obrigatória, para usuários com perfil de operador ou administrador. Após o reprocessamento, a tela SHALL refletir o novo estado sem exigir recarga manual.

#### Scenario: Reprocessar grupo

- **WHEN** o operador manda reprocessar um grupo
- **THEN** as pendências SHALL passar a `EM_REPROCESSAMENTO` na tela, e o resultado SHALL ser refletido quando concluir

#### Scenario: Reprocessamento falha de novo

- **WHEN** o reprocessamento falha novamente
- **THEN** a pendência SHALL voltar a aparecer como aberta, com o novo motivo e a contagem de tentativas

#### Scenario: Recusa por obsolescência

- **WHEN** o reprocessamento é recusado porque já existe lote mais recente
- **THEN** a tela SHALL explicar que reprocessar sobrescreveria dado mais novo, e exibir a pendência como obsoleta

#### Scenario: Descarte exige justificativa

- **WHEN** o usuário tenta descartar sem preencher a justificativa
- **THEN** a ação SHALL ser bloqueada na tela, com a exigência explicitada

#### Scenario: Leitor vê mas não age

- **WHEN** um usuário com perfil de leitor abre a tela de pendências
- **THEN** a listagem SHALL ser exibida, e as ações de reprocessar e descartar SHALL aparecer desabilitadas com o motivo

### Requirement: Tela de carga manual de curva

A tela SHALL permitir selecionar a curva, a data e o momento, baixar o modelo de arquivo, anexar o arquivo preenchido, informar a justificativa e submeter. O botão de submissão SHALL permanecer bloqueado enquanto a justificativa estiver vazia.

#### Scenario: Modelo disponível na própria tela

- **WHEN** o usuário seleciona a curva
- **THEN** a tela SHALL oferecer o download do modelo em CSV e em planilha, com o cabeçalho e os prazos daquela curva

#### Scenario: Carga bem-sucedida

- **WHEN** o arquivo é aceito e a curva passa no gate de validação
- **THEN** a tela SHALL informar a publicação e a nova versão, marcada como carregada

#### Scenario: Erros de leitura por linha

- **WHEN** o arquivo tem erros de conteúdo
- **THEN** a tela SHALL listar todos os erros com linha, coluna e motivo, e SHALL deixar claro que nada foi publicado

#### Scenario: Curva carregada reprovada

- **WHEN** a curva carregada é reprovada em teste bloqueante
- **THEN** a tela SHALL exibir os testes reprovados e informar que a versão anterior permanece vigente

#### Scenario: Justificativa obrigatória

- **WHEN** a justificativa está vazia
- **THEN** a submissão SHALL permanecer bloqueada, com a exigência explicitada

#### Scenario: Leitor sem permissão

- **WHEN** um usuário com perfil de leitor abre a tela
- **THEN** a tela SHALL ser exibida com a submissão desabilitada e o motivo visível

### Requirement: Curva carregada permanece identificável

Toda apresentação de uma versão carregada manualmente SHALL indicá-la como tal — no painel do dia, no viewer, no histórico de versões e na procedência —, com o autor e a justificativa acessíveis.

#### Scenario: Painel do dia

- **WHEN** a curva do dia foi publicada por carga manual
- **THEN** a linha SHALL indicar a origem carregada, distinta de calculada e de importada

#### Scenario: Viewer

- **WHEN** uma versão carregada é exibida no viewer
- **THEN** a origem, o autor da carga e a justificativa SHALL estar visíveis junto dos dados

### Requirement: Tela de modelos e comparação

A tela SHALL listar os modelos de construção com tipo e estado, permitir importar modelo Groovy, permitir apontar uma curva para outro modelo, e comparar duas curvas ou dois modelos prazo a prazo.

#### Scenario: Importar modelo Groovy

- **WHEN** um administrador importa um script Groovy
- **THEN** o resultado da validação SHALL ser exibido, e o modelo SHALL passar a aparecer na lista quando válido

#### Scenario: Importação recusada

- **WHEN** o script não compila ou não produz vértices
- **THEN** a tela SHALL exibir o motivo da recusa

#### Scenario: Comparar construída contra importada

- **WHEN** a comparação entre as duas origens é solicitada para uma data
- **THEN** a tela SHALL exibir a tabela de diferenças por prazo, destacando as maiores

#### Scenario: Prazos presentes em apenas uma curva

- **WHEN** as duas curvas não têm exatamente os mesmos prazos
- **THEN** esses prazos SHALL ser sinalizados como presentes em apenas uma, sem valor inventado para a outra

### Requirement: Precisão na exibição

Valores de mercado SHALL ser tratados como texto no modelo do app e convertidos apenas para formatação de exibição. O app MUST NOT fazer aritmética com valores de mercado, incluindo cálculo de diferenças, que vêm prontos do backend.

#### Scenario: Valor com muitas casas decimais

- **WHEN** um vértice com doze casas decimais é exibido
- **THEN** todos os dígitos SHALL ser preservados na exibição, sem perda por conversão numérica

#### Scenario: Aritmética proibida no navegador

- **WHEN** o código do app faz conversão numérica de um campo de valor de mercado para cálculo
- **THEN** a verificação de lint do build SHALL falhar

### Requirement: Estados de carregamento, erro e degradação

Toda tela SHALL tratar explicitamente carregamento, erro e degradação parcial. Seção marcada como indisponível pelo backend MUST NOT ser renderizada como vazia.

#### Scenario: Seção indisponível

- **WHEN** o backend marca uma seção da tela como indisponível
- **THEN** a tela SHALL exibir o restante normalmente e sinalizar a seção afetada com o motivo e a opção de tentar de novo

#### Scenario: Distinção entre vazio e indisponível

- **WHEN** uma seção não tem dados por não haver o que mostrar
- **THEN** a apresentação SHALL ser distinta da de uma seção que falhou ao carregar

### Requirement: Ações condicionadas ao perfil

Ações que o perfil do usuário não permite SHALL ser exibidas desabilitadas com o motivo, e MUST NOT ser simplesmente ocultadas nem falhar apenas no clique.

#### Scenario: Leitor na tela de disparo

- **WHEN** um usuário com perfil de leitor abre a tela de disparo manual
- **THEN** a tela SHALL ser exibida com a ação de disparo desabilitada e o motivo visível

#### Scenario: Operador na tela de cadastro

- **WHEN** um usuário com perfil de operador abre o cadastro de curva
- **THEN** os campos SHALL ser visíveis e a ação de salvar SHALL estar desabilitada com a explicação

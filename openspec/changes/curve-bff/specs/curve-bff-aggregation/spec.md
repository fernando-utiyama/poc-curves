## ADDED Requirements

### Requirement: Contratos orientados a tela

O BFF SHALL expor um recurso por tela ou por ação de tela, entregando em uma única resposta tudo o que a tela precisa. O front MUST NOT precisar encadear chamadas para montar uma tela.

#### Scenario: Tela de curva em uma chamada

- **WHEN** a tela de curva é aberta para uma curva e uma data
- **THEN** a resposta SHALL conter a versão publicada, os vértices, a procedência, o modelo utilizado e o estado da última execução

#### Scenario: Tela de catálogo

- **WHEN** a tela de catálogo é aberta
- **THEN** a resposta SHALL listar as definições com código, nome, modo de origem, estado, versão vigente e modelo apontado

#### Scenario: Tela de execuções

- **WHEN** a tela de monitoramento é aberta com filtros
- **THEN** a resposta SHALL trazer as execuções com estado, etapa, duração, `correlacao_id` e causa da falha quando houver

### Requirement: Agregação em paralelo com timeout individual

As chamadas às dependências SHALL ser feitas em paralelo, cada uma com timeout próprio menor que o timeout total da requisição.

#### Scenario: Dependência lenta

- **WHEN** uma dependência excede o seu timeout individual
- **THEN** a resposta SHALL ser devolvida com as demais seções preenchidas, dentro do timeout total

### Requirement: Degradação parcial explícita

Quando uma dependência falha ou não responde, o BFF SHALL retornar as seções obtidas com sucesso e SHALL marcar a seção indisponível explicitamente, com o motivo. A resposta MUST NOT falhar por inteiro por causa de uma seção.

#### Scenario: Motor indisponível na tela de curva

- **WHEN** o serviço de interpolação está fora do ar e a tela de curva é aberta
- **THEN** os vértices, a procedência e a execução SHALL ser retornados normalmente, e a seção de interpolação SHALL vir marcada como indisponível com o motivo

#### Scenario: Seção indisponível não é seção vazia

- **WHEN** uma seção não pôde ser obtida
- **THEN** ela SHALL ser distinguível de uma seção legitimamente vazia

#### Scenario: Falha total das dependências

- **WHEN** nenhuma dependência responde
- **THEN** a resposta SHALL indicar a indisponibilidade de forma explícita, e MUST NOT simular dado

### Requirement: Disparo manual de ingestão pelos dois tipos de insumo

O BFF SHALL expor uma ação de disparo manual que recebe a data de referência e a lista do que consumir, aceitando os conjuntos de **dado individual** — arquivo de preços, BVBG.086, BVBG.028 — e o conjunto de **curva pronta** da B3, isoladamente ou em conjunto. Todo disparo manual SHALL usar a faixa prioritária. A resposta SHALL trazer o `correlacao_id` para acompanhamento.

#### Scenario: Disparo dos arquivos BVBG

- **WHEN** o operador dispara a ingestão dos arquivos BVBG para uma data
- **THEN** o orquestrador SHALL ser acionado para esses conjuntos, e a resposta SHALL trazer o `correlacao_id` da execução

#### Scenario: Disparo do endpoint de curva pronta

- **WHEN** o operador dispara a ingestão da curva pronta para uma data
- **THEN** o orquestrador SHALL ser acionado para esse conjunto, e a resposta SHALL trazer o `correlacao_id`

#### Scenario: Disparo dos dois em uma ação

- **WHEN** o operador seleciona ambos os tipos de insumo para a mesma data
- **THEN** ambos SHALL ser disparados, e a resposta SHALL permitir acompanhar o progresso de cada um

#### Scenario: Redisparo de ingestão travada

- **WHEN** o operador redispara uma ingestão cuja execução anterior está travada
- **THEN** a nova execução SHALL ser criada na faixa prioritária, e a resposta SHALL trazer o seu `correlacao_id`, sem depender do desbloqueio da anterior

#### Scenario: Já existe execução em andamento

- **WHEN** o disparo é feito para uma data que já tem execução ativa
- **THEN** a resposta SHALL informar isso e trazer o `correlacao_id` e o progresso da execução existente, sem criar outra

#### Scenario: Data que não é dia de pregão

- **WHEN** o disparo é feito para uma data que não é dia de pregão
- **THEN** a resposta SHALL informar isso explicitamente antes de qualquer ingestão

### Requirement: Consulta de interpolação para a tela

O BFF SHALL expor a consulta interpolada aceitando um ou vários prazos em uma chamada, devolvendo os resultados na ordem solicitada e identificando a versão de curva usada.

#### Scenario: Vários prazos para um gráfico

- **WHEN** a tela solicita uma lista de prazos
- **THEN** a resposta SHALL trazer um resultado por prazo, na ordem pedida, todos da mesma versão de curva

#### Scenario: Prazo fora do intervalo sob política estrita

- **WHEN** um prazo está fora do intervalo e a política da curva é estrita
- **THEN** a resposta SHALL identificar o prazo problemático individualmente, sem invalidar os demais

### Requirement: Comparação para a tela

O BFF SHALL expor a comparação entre duas curvas da mesma data — tipicamente a construída contra a importada — e entre dois modelos para a mesma curva e data, em contrato pronto para exibição.

#### Scenario: Construída contra importada

- **WHEN** a comparação entre as duas origens é solicitada
- **THEN** a resposta SHALL trazer, por prazo, os dois valores e a diferença, com os prazos presentes em apenas uma sinalizados

#### Scenario: Modelo contra modelo

- **WHEN** a comparação entre dois modelos é solicitada para a mesma curva e data
- **THEN** a resposta SHALL trazer a diferença prazo a prazo, e nenhuma curva SHALL ser publicada como efeito colateral

### Requirement: Gestão de curva e de modelos pela tela

O BFF SHALL expor as ações de cadastro e edição de definição de curva, de gestão de agendamentos, de importação de modelo Groovy e de troca do modelo de uma curva, todas restritas ao perfil de administrador.

#### Scenario: Troca de modelo pela tela

- **WHEN** um administrador troca o modelo de uma curva pela tela
- **THEN** a nova versão da definição SHALL ser criada, e a resposta SHALL identificá-la

#### Scenario: Importação de Groovy pela tela

- **WHEN** um administrador importa um modelo Groovy
- **THEN** o resultado da validação SHALL ser devolvido, e o modelo SHALL ficar disponível para seleção quando válido

### Requirement: Carga manual de curva pela tela

O BFF SHALL expor a ação de carga manual de curva, recebendo o arquivo, a curva, a data, o momento e a justificativa, encaminhando ao orquestrador e retornando o `correlacao_id`. A ação SHALL exigir perfil de operador ou administrador.

O BFF MUST NOT interpretar o conteúdo do arquivo — apenas validar tipo e tamanho antes de encaminhar.

#### Scenario: Carga aceita

- **WHEN** um operador submete um arquivo de curva com justificativa
- **THEN** a ação SHALL ser encaminhada e a resposta SHALL trazer o `correlacao_id`

#### Scenario: Erros de leitura devolvidos ao usuário

- **WHEN** o processamento do arquivo encontra erros de conteúdo
- **THEN** a resposta SHALL trazer a lista completa de erros com linha, coluna e motivo, em formato exibível

#### Scenario: Curva carregada reprovada na validação

- **WHEN** a curva carregada é reprovada no gate de consistência
- **THEN** a resposta SHALL informar os testes reprovados e deixar claro que nada foi publicado

#### Scenario: Leitor tenta carregar

- **WHEN** um usuário com perfil de leitor tenta submeter uma carga
- **THEN** a operação SHALL ser recusada, e o orquestrador MUST NOT ser chamado

#### Scenario: BFF não interpreta o arquivo

- **WHEN** o arquivo é recebido
- **THEN** o BFF SHALL validar apenas tipo e tamanho, e MUST NOT parsear o conteúdo

### Requirement: Modelo de arquivo para download

O BFF SHALL expor o download do modelo de carga de uma curva, em CSV e em planilha, obtido do `curve-api`.

#### Scenario: Download do modelo

- **WHEN** o usuário solicita o modelo de uma curva
- **THEN** o arquivo SHALL ser entregue com o cabeçalho e os prazos daquela curva

### Requirement: Painel do dia

O BFF SHALL expor um recurso que retorna, para uma data de referência, o estado de publicação de cada curva ativa: se está publicada, em andamento, em risco, atrasada, reprovada ou não iniciada; o horário limite; o tempo restante ou a margem obtida; e a etapa atual quando em andamento.

#### Scenario: Manhã de operação

- **WHEN** o painel do dia é aberto para hoje
- **THEN** cada curva ativa SHALL aparecer com o seu estado, o horário limite e o tempo restante ou a margem

#### Scenario: Curva em risco destacada

- **WHEN** uma execução está marcada como em risco
- **THEN** a curva SHALL vir sinalizada como tal, com a etapa atual e o tempo restante

#### Scenario: Curva reprovada na validação

- **WHEN** a versão do dia foi reprovada no gate de consistência
- **THEN** a curva SHALL aparecer como reprovada, com os testes que falharam, e a versão anterior SHALL constar como a vigente

#### Scenario: Curva publicada com aviso

- **WHEN** a curva foi publicada com teste de aviso reprovado
- **THEN** ela SHALL aparecer como publicada e SHALL trazer os avisos registrados

#### Scenario: Curva ainda não iniciada

- **WHEN** o agendamento de uma curva ainda não disparou
- **THEN** ela SHALL aparecer como não iniciada, com o horário previsto, e MUST NOT ser confundida com falha

### Requirement: Alerta de risco de atraso

O BFF SHALL incluir no recurso leve de alerta a quantidade de curvas em risco e atrasadas para a data corrente, e a menor margem restante entre elas.

#### Scenario: Curva em risco

- **WHEN** existe execução em risco para hoje
- **THEN** o alerta SHALL refletir isso, ainda que nenhuma falha tenha ocorrido e nenhuma pendência exista

#### Scenario: Alerta combina as duas naturezas

- **WHEN** existem simultaneamente pendências de dead-letter e curvas em risco
- **THEN** o alerta SHALL distinguir as duas naturezas, porque a ação para cada uma é diferente

### Requirement: Alerta de pendências de dead-letter

O BFF SHALL expor um recurso leve de alerta, adequado a consulta frequente por todas as telas, retornando a quantidade de grupos com pendência aberta, o total de mensagens pendentes, a idade da pendência aberta mais antiga e a severidade derivada dessa idade.

#### Scenario: Existe pendência aberta

- **WHEN** há pendências abertas
- **THEN** a resposta SHALL trazer a contagem de grupos, o total de mensagens e a idade da mais antiga

#### Scenario: Nenhuma pendência aberta

- **WHEN** não há pendência aberta
- **THEN** a contagem SHALL ser zero, e o front SHALL deixar de exibir o alerta

#### Scenario: Recurso leve

- **WHEN** o recurso de alerta é consultado periodicamente por muitas telas
- **THEN** ele SHALL retornar apenas os agregados, sem carregar a lista de pendências

#### Scenario: Alerta visível a qualquer autenticado

- **WHEN** um usuário com perfil de leitor consulta o alerta
- **THEN** a consulta SHALL ser atendida, porque enxergar o problema não depende de poder corrigi-lo

### Requirement: Tela de pendências de dead-letter

O BFF SHALL expor a listagem de grupos de pendência com motivo, fonte, conjunto de dados, data de referência, quantidade, instante da primeira e da última falha, detalhe representativo e estado; e o detalhamento das mensagens de um grupo, com filtros e paginação.

#### Scenario: Listagem por grupo

- **WHEN** a tela de pendências é aberta
- **THEN** a resposta SHALL trazer os grupos ordenados da falha mais antiga para a mais recente

#### Scenario: Detalhe de um grupo

- **WHEN** um grupo é expandido
- **THEN** a resposta SHALL trazer as pendências individuais com `id_evento`, `correlacao_id` e detalhe da falha

#### Scenario: Navegação para a execução

- **WHEN** uma pendência é inspecionada
- **THEN** a resposta SHALL conter o `correlacao_id` que permite abrir a execução correspondente

### Requirement: Ações sobre pendências

O BFF SHALL expor as ações de reprocessar uma pendência, reprocessar um grupo inteiro e descartar com justificativa, todas restritas ao perfil de operador ou de administrador. A resposta SHALL indicar o resultado da ação e o novo estado.

#### Scenario: Reprocessar grupo

- **WHEN** um operador manda reprocessar um grupo
- **THEN** a ação SHALL ser encaminhada ao orquestrador, e a resposta SHALL informar quantas pendências entraram em reprocessamento

#### Scenario: Alerta reflete a resolução

- **WHEN** o reprocessamento conclui com sucesso e as pendências são resolvidas
- **THEN** a consulta seguinte ao recurso de alerta SHALL refletir a contagem reduzida, sem exigir ação adicional do usuário

#### Scenario: Reprocessamento recusado por obsolescência

- **WHEN** o orquestrador recusa o reprocessamento porque já existe lote mais recente
- **THEN** a resposta SHALL informar o motivo de forma exibível, e o estado obsoleto SHALL ser refletido

#### Scenario: Descarte sem justificativa

- **WHEN** o descarte é solicitado sem justificativa
- **THEN** a operação SHALL ser recusada

#### Scenario: Leitor tenta reprocessar

- **WHEN** um usuário com perfil de leitor tenta reprocessar ou descartar
- **THEN** a operação SHALL ser recusada por falta de autorização, e o orquestrador MUST NOT ser chamado

### Requirement: Preservação de precisão até o navegador

Valores de mercado SHALL trafegar como texto numérico da API de domínio até a resposta do BFF. O BFF MUST NOT converter, formatar, arredondar ou fazer aritmética com esses valores.

#### Scenario: Valor de doze casas decimais

- **WHEN** um vértice com muitas casas decimais é retornado
- **THEN** o front SHALL receber exatamente os mesmos dígitos que a API de domínio produziu

### Requirement: Paginação, filtro e ordenação uniformes

O BFF SHALL usar o mesmo formato de paginação, filtro e ordenação em todas as listagens, com limite máximo de página definido.

#### Scenario: Listagem longa

- **WHEN** uma listagem excede o limite de página
- **THEN** a resposta SHALL trazer a página solicitada e a informação necessária para navegar

#### Scenario: Limite excedido no pedido

- **WHEN** o cliente pede uma página maior que o limite máximo
- **THEN** o BFF SHALL aplicar o limite máximo e indicar isso na resposta

### Requirement: Tradução de erro sem validação própria

Erro de negócio vindo das APIs de domínio SHALL ser traduzido para o contrato de tela preservando o código original e uma mensagem apresentável. O BFF MUST NOT implementar validação de domínio própria.

#### Scenario: Erro de cadastro

- **WHEN** o cadastro é recusado por incoerência detectada pela API de domínio
- **THEN** a resposta do BFF SHALL preservar o código do erro e trazer mensagem exibível ao usuário

#### Scenario: Validação duplicada proibida

- **WHEN** o BFF implementa regra de validação de domínio própria
- **THEN** a revisão SHALL rejeitar a implementação, porque criaria duas verdades sobre o que é válido

### Requirement: Contrato OpenAPI do BFF

O contrato do BFF SHALL residir em `contracts/openapi/curve-bff.yaml`, versionado, e SHALL ser verificado por teste contra a implementação. O front SHALL ser desenvolvido contra esse contrato.

#### Scenario: Implementação diverge do contrato

- **WHEN** a implementação passa a devolver estrutura diferente do contrato
- **THEN** o teste de conformidade SHALL falhar no build

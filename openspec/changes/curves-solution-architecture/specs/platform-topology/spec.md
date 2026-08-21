## ADDED Requirements

### Requirement: Fronteiras de componente

A plataforma SHALL ser composta pelos componentes `curve-orchestrator`, feeders de market data, `curve-processor`, `curve-engine`, `curve-api`, `curve-bff` e `curve-web-ui`, e cada componente SHALL respeitar as fronteiras abaixo. Nenhum componente pode acessar recurso fora da sua fronteira.

- Feeders MUST escrever apenas em Kafka; feeders MUST NOT acessar o banco de dados.
- `curve-processor` MUST consumir Kafka e escrever nas tabelas de market data, de lote de ingestão e — exclusivamente para versões de curva que **chegaram prontas**, isto é, com `origem_versao` `IMPORTADA` ou `CARREGADA` — nas tabelas de curva publicada.
- `curve-engine` MUST ser o único componente autorizado a escrever versões de curva **calculadas por ele**, com `origem_versao` igual a `CALCULADA`.
- `curve-api` MUST acessar o banco somente para leitura, exceto pelo cadastro de definição de curva.
- `curve-bff` MUST NOT acessar o banco de dados nem Kafka; só chama `curve-api`, `curve-engine` e `curve-orchestrator` por HTTP.
- `curve-web-ui` MUST chamar exclusivamente o `curve-bff`.

#### Scenario: Feeder tenta gravar no banco

- **WHEN** um feeder é implementado com acesso direto ao banco de dados
- **THEN** a revisão de arquitetura SHALL rejeitar a implementação, e o teste de fronteira de dependência SHALL falhar por dependência proibida

#### Scenario: Front tenta chamar API de domínio

- **WHEN** o `curve-web-ui` emite requisição para `curve-api` ou `curve-engine` sem passar pelo `curve-bff`
- **THEN** a requisição SHALL ser rejeitada, porque somente o `curve-bff` é exposto ao navegador na configuração de rede do ambiente

### Requirement: Fluxo ponta a ponta

A plataforma SHALL executar o fluxo `curve-orchestrator` → feeder → `marketdata.raw` → `curve-processor` → `ponto_dado_mercado` → `curve.build.requested` → `curve-engine` → `vertice_curva` → `curve.published` → `curve-api` → `curve-bff` → tela, sem etapa manual entre o disparo e a curva disponível para consulta.

#### Scenario: Ingestão agendada resulta em curva consultável

- **WHEN** o `curve-orchestrator` dispara o agendamento diário da fonte B3 para uma data de pregão com dado publicado
- **THEN** ao final do fluxo a curva SHALL estar no estado `PUBLISHED` com seus vértices persistidos, e SHALL ser retornada pela consulta do `curve-bff` sem intervenção manual

#### Scenario: Disparo manual pela tela

- **WHEN** um usuário com perfil operador dispara a ingestão de uma data pela tela
- **THEN** o fluxo SHALL ser o mesmo do agendado, e o run resultante SHALL registrar `triggeredBy` = `MANUAL` com a identidade do usuário

### Requirement: Origem da versão de curva

Toda `versao_curva` SHALL declarar a sua origem entre `CALCULADA`, `IMPORTADA` e `CARREGADA`. A autoridade de publicação SHALL decorrer da origem da versão, e não do modo de origem da definição:

- versões `CALCULADA` SHALL ser publicadas exclusivamente pelo `curve-engine`;
- versões `IMPORTADA` e `CARREGADA` SHALL ser publicadas exclusivamente pelo `curve-processor`.

O modo de origem da definição descreve o caminho **normal** da curva; a origem da versão descreve como **aquela** versão foi produzida. Uma definição de qualquer modo SHALL poder receber versão `CARREGADA`, como contingência declarada.

#### Scenario: Origem sempre visível

- **WHEN** uma versão de curva é retornada por qualquer consulta
- **THEN** a sua origem SHALL constar na resposta

#### Scenario: Contingência em curva construída

- **WHEN** uma curva de modo `BOOTSTRAPPED` recebe uma versão carregada manualmente
- **THEN** a versão SHALL ter origem `CARREGADA`, e o modo da definição MUST NOT ser alterado

#### Scenario: Motor não publica o que não calculou

- **WHEN** o motor tenta publicar uma versão de origem `IMPORTADA` ou `CARREGADA`
- **THEN** a operação SHALL ser recusada

### Requirement: Dois modos de origem de curva

Toda definição de curva SHALL declarar um modo de origem entre `BOOTSTRAPPED` e `IMPORTED`, e o modo SHALL determinar quem publica os vértices:

- `BOOTSTRAPPED`: os insumos chegam como **dado individual por instrumento**, são persistidos em `ponto_dado_mercado`, e o `curve-engine` monta a curva por bootstrap e publica os vértices.
- `IMPORTED`: a **curva já pronta** chega da fonte vértice a vértice, e o `curve-processor` normaliza e publica os vértices sem qualquer bootstrap.

Uma curva construída e uma curva importada que representem o mesmo mercado SHALL ser definições distintas no catálogo, com códigos distintos. A plataforma MUST NOT mesclar as duas em uma única definição.

#### Scenario: Curva construída a partir de dado individual

- **WHEN** a definição declara modo `BOOTSTRAPPED` e os contratos individuais da data estão persistidos
- **THEN** o `curve-engine` SHALL montar a curva por bootstrap e publicar os vértices, e o `curve-processor` MUST NOT publicar vértices para essa definição

#### Scenario: Curva recebida pronta da fonte

- **WHEN** a definição declara modo `IMPORTED` e a fonte entrega a curva já pronta para a data
- **THEN** o `curve-processor` SHALL publicar os vértices recebidos sem bootstrap, e o `curve-engine` MUST NOT reconstruí-los

#### Scenario: Mesmo mercado, duas definições

- **WHEN** existem no catálogo a curva construída e a curva importada do mesmo mercado e da mesma data
- **THEN** ambas SHALL ser consultáveis de forma independente, cada uma com sua própria versão e proveniência

#### Scenario: Modo de origem incoerente com o insumo

- **WHEN** chega curva pronta para uma definição declarada como `BOOTSTRAPPED`, ou dado individual para uma definição declarada como `IMPORTED`
- **THEN** o processamento SHALL falhar nomeando a definição, o modo declarado e o tipo de insumo recebido, e nada SHALL ser publicado

### Requirement: Paridade de tratamento entre curvas construídas e importadas

Uma vez publicada, a curva SHALL receber o mesmo tratamento independentemente do modo de origem: mesmas APIs de consulta, mesma interpolação síncrona, mesmo versionamento, mesma exigência de proveniência e mesmos estados de ciclo de vida.

#### Scenario: Interpolação sobre curva importada

- **WHEN** um usuário solicita interpolação em prazo arbitrário sobre uma curva de modo `IMPORTED`
- **THEN** a interpolação SHALL ser atendida pelo mesmo caminho síncrono usado para curvas construídas, com o interpolador declarado na definição

#### Scenario: Proveniência da curva importada

- **WHEN** a proveniência de uma curva importada é consultada
- **THEN** a resposta SHALL identificar o arquivo de origem, seu hash, o run e o lote de ingestão que a produziram

### Requirement: Comparação entre curva construída e curva importada

A plataforma SHALL permitir comparar, para a mesma data de referência, os vértices de uma curva construída contra os de uma curva importada, reportando as diferenças vértice a vértice.

#### Scenario: Comparação vértice a vértice

- **WHEN** a comparação é solicitada para duas curvas da mesma data, uma construída e uma importada
- **THEN** a resposta SHALL listar, por prazo, o valor de cada curva e a diferença, sinalizando os prazos presentes em apenas uma delas

#### Scenario: Datas sem contraparte

- **WHEN** uma das duas curvas não tem versão publicada para a data solicitada
- **THEN** a comparação SHALL informar explicitamente qual curva está ausente, e MUST NOT substituí-la pela versão de outra data

### Requirement: Prazo de publicação e janela crítica

Cada definição de curva SHALL declarar um horário limite de publicação. A plataforma SHALL conhecer, para cada execução em andamento, o tempo restante até esse limite, e SHALL registrar o tempo efetivo de cada etapa — ingestão, construção, validação e publicação — para calibração do orçamento.

#### Scenario: Curva publicada dentro do prazo

- **WHEN** a curva é publicada antes do horário limite
- **THEN** a margem em relação ao limite SHALL ser registrada

#### Scenario: Execução em risco

- **WHEN** o tempo restante até o limite fica menor que o orçamento típico das etapas que faltam
- **THEN** a execução SHALL ser sinalizada como em risco, antes de qualquer falha ocorrer

#### Scenario: Prazo ultrapassado

- **WHEN** o horário limite é atingido sem publicação
- **THEN** a execução SHALL ser sinalizada como atrasada, distinta de falha, e SHALL permanecer em andamento se ainda houver chance de concluir

#### Scenario: Orçamento medido, não suposto

- **WHEN** uma etapa conclui
- **THEN** o seu tempo efetivo SHALL ser registrado, permitindo calibrar o orçamento com série observada

### Requirement: Prioridade do dia sobre o histórico

Operações de carga histórica e de reprocessamento de pendência antiga MUST NOT competir com o fluxo do dia dentro da janela crítica. A plataforma SHALL prover uma janela de bloqueio, anterior ao horário limite, durante a qual essas operações são suspensas.

#### Scenario: Backfill em andamento ao entrar na janela crítica

- **WHEN** a janela de bloqueio começa e existe carga histórica em andamento
- **THEN** ela SHALL ser pausada, e SHALL ser retomada após o fim da janela

#### Scenario: Tentativa de iniciar carga histórica na janela

- **WHEN** uma carga histórica é solicitada dentro da janela de bloqueio
- **THEN** a solicitação SHALL ser recusada ou agendada para depois da janela, informando o motivo

### Requirement: Detecção de fila travada

A plataforma SHALL monitorar, por partição, o avanço do offset confirmado de cada grupo de consumo. Offset sem avanço por mais que o limite configurado, havendo mensagens pendentes, SHALL gerar alerta identificando o grupo, o tópico e a partição.

#### Scenario: Partição sem avanço

- **WHEN** o offset de uma partição não avança pelo tempo limite e existe lag
- **THEN** um alerta SHALL ser gerado identificando exatamente a partição afetada

#### Scenario: Monitoramento por partição, não agregado

- **WHEN** uma única partição está travada e as demais avançam normalmente
- **THEN** o alerta SHALL ser gerado mesmo assim, sem ser diluído pela média do grupo

### Requirement: Propagação de correlation id

Todo componente SHALL receber, propagar e registrar em log um `correlationId` único por run, do disparo até a publicação da curva. Um componente que gera trabalho derivado MUST reutilizar o `correlationId` recebido, e nunca criar um novo.

#### Scenario: Rastrear um run ponta a ponta

- **WHEN** um run é disparado e concluído com sucesso
- **THEN** os logs de `curve-orchestrator`, feeder, `curve-processor` e `curve-engine` SHALL conter o mesmo `correlationId`, e a consulta de execução SHALL listar as etapas correlacionadas por ele

#### Scenario: Evento sem correlation id

- **WHEN** um evento chega a um consumidor sem `correlationId` preenchido
- **THEN** o consumidor SHALL rejeitar o evento para a dead-letter, sem processá-lo

### Requirement: Ciclo de vida e estados da curva

Uma curva SHALL transitar pelos estados `PENDENTE`, `CONSTRUINDO`, `EM_VALIDACAO`, `PUBLICADA`, `REPROVADA`, `SUBSTITUIDA` e `FALHOU`, e SHALL ser qualificada por um `momento_curva` entre `ABERTURA`, `INTRADIA` e `FECHAMENTO`. Uma nova publicação bem-sucedida para o mesmo `(curva, data_referencia, momento_curva)` SHALL marcar a versão anterior como `SUBSTITUIDA`, e MUST NOT alterar seus vértices. Versão em `EM_VALIDACAO` ou `REPROVADA` MUST NOT ser retornada por nenhuma consulta de curva vigente.

#### Scenario: Reprocessamento da mesma data

- **WHEN** a mesma data de referência é reprocessada e a construção conclui com sucesso
- **THEN** uma nova `versao_curva` SHALL ser criada em `PUBLICADA`, a versão anterior SHALL passar para `SUBSTITUIDA`, e os vértices da versão anterior SHALL permanecer legíveis e inalterados

#### Scenario: Consulta histórica por instante

- **WHEN** uma consulta informa `asOf` igual a um instante anterior à última republicação
- **THEN** a resposta SHALL conter a versão que estava `PUBLICADA` naquele instante, e não a versão mais recente

### Requirement: Gate de validação de consistência da curva

Toda curva SHALL passar por validação de consistência antes de ser publicada. A versão SHALL ser gravada em `EM_VALIDACAO`, submetida aos testes, e apenas então promovida a `PUBLICADA` ou marcada como `REPROVADA`. Versão `REPROVADA` MUST NOT ser promovida nem retornada como curva vigente.

Os testes SHALL ser classificados em **bloqueantes** e **de aviso**. Teste bloqueante reprovado impede a publicação; teste de aviso reprovado permite a publicação e SHALL registrar o aviso de forma visível.

#### Scenario: Curva aprovada

- **WHEN** todos os testes bloqueantes passam
- **THEN** a versão SHALL ser promovida a `PUBLICADA`, e o resultado da validação SHALL ficar registrado

#### Scenario: Curva reprovada

- **WHEN** um teste bloqueante falha
- **THEN** a versão SHALL ficar `REPROVADA`, MUST NOT ser publicada, a execução SHALL ir para falha nomeando os testes reprovados, e a versão anterior SHALL permanecer publicada e inalterada

#### Scenario: Curva com aviso

- **WHEN** todos os bloqueantes passam mas um teste de aviso falha
- **THEN** a versão SHALL ser publicada, o aviso SHALL ser registrado com o teste e a medida observada, e SHALL ficar visível na consulta e na tela

#### Scenario: Consulta nunca vê curva não validada

- **WHEN** uma consulta de curva vigente ocorre enquanto uma versão está `EM_VALIDACAO`
- **THEN** a resposta SHALL trazer a versão anterior publicada, e MUST NOT trazer a versão em validação

#### Scenario: Resultado auditável mesmo em reprovação

- **WHEN** uma versão é reprovada
- **THEN** o resultado de cada teste SHALL permanecer persistido e consultável, incluindo os valores observados e os limites aplicados

### Requirement: Gate de qualidade de dado na publicação

A construção de curva SHALL falhar explicitamente quando qualquer insumo exigido pela definição da curva estiver ausente, nomeando o índice e a data faltantes. A plataforma MUST NOT interpolar, repetir o valor anterior, nem aplicar valor default para suprir insumo ausente, e MUST NOT publicar curva parcial.

#### Scenario: Fixing ausente

- **WHEN** a definição da curva exige o fixing de um índice em uma data e esse ponto não existe em `ponto_dado_mercado`
- **THEN** a construção SHALL falhar nomeando índice e data, o run SHALL ir para `FAILED`, e nenhuma `versao_curva` SHALL ser criada

#### Scenario: Conjunto de instrumentos incompleto

- **WHEN** o conjunto de contratos futuros recebido é menor que o mínimo declarado na definição da curva
- **THEN** a construção SHALL falhar identificando o conjunto esperado e o recebido, e nada SHALL ser publicado

### Requirement: Falha de fonte externa é explícita

Falha de comunicação, indisponibilidade ou resposta inválida de fonte externa SHALL ser reportada como falha nomeando a fonte e o recurso. A plataforma MUST NOT substituir a resposta ausente por dado sintético, cache silencioso ou valor de outra data.

#### Scenario: Fonte indisponível

- **WHEN** o endpoint da fonte externa responde erro ou não responde dentro do timeout
- **THEN** o run SHALL registrar a falha com fonte, recurso e causa, e SHALL NOT emitir evento de dado bruto

### Requirement: Reprocessamento e idempotência do fluxo

Reexecutar o fluxo para a mesma `(fonte, dataset, data de referência)` SHALL ser seguro: não SHALL duplicar pontos de market data, e SHALL produzir vértices idênticos quando os insumos e a versão da definição de curva forem idênticos.

#### Scenario: Replay de tópico

- **WHEN** o mesmo evento de market data é consumido mais de uma vez
- **THEN** o estado final do `ponto_dado_mercado` SHALL ser idêntico ao de um único consumo

#### Scenario: Reconstrução determinística

- **WHEN** a mesma curva é reconstruída com os mesmos insumos e a mesma versão de definição
- **THEN** os vértices resultantes SHALL ser exatamente iguais aos da versão anterior, valor a valor

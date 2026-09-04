## ADDED Requirements

### Requirement: Contrato de execução do feeder

Todo feeder SHALL expor uma operação de aquisição que recebe `source`, `dataset`, `referenceDate` e `correlationId`, e retorna exatamente um resultado entre `PUBLISHED`, `NO_DATA` e `FAILED`. O feeder MUST NOT retornar sucesso sem ter publicado, nem falha quando a fonte respondeu corretamente que não há dado.

#### Scenario: Aquisição bem-sucedida

- **WHEN** a fonte responde com o dado da data solicitada
- **THEN** o feeder SHALL publicar o evento e retornar `PUBLISHED` com o `eventId` publicado

#### Scenario: Data sem publicação na fonte

- **WHEN** a fonte responde corretamente que não há publicação para a data (feriado, dia não útil ou dado ainda não divulgado)
- **THEN** o feeder SHALL retornar `NO_DATA` com o motivo, SHALL NOT publicar evento, e SHALL NOT ser tratado como falha

#### Scenario: Fonte indisponível

- **WHEN** a fonte não responde dentro do timeout, ou responde erro
- **THEN** o feeder SHALL retornar `FAILED` nomeando a fonte, o recurso e a causa, e SHALL NOT publicar evento

### Requirement: Publicação no envelope comum

O feeder SHALL publicar exclusivamente nos tópicos de ingestão do catálogo, usando o envelope comum definido em `contracts/events`, com `source`, `dataset`, `referenceDate` e `correlationId` preenchidos e cada bloco acompanhado dos metadados de aquisição — URL de origem, encoding declarado, tamanho e hash do conteúdo.

#### Scenario: Evento conforme o schema

- **WHEN** o feeder publica um evento
- **THEN** o evento SHALL passar na validação do schema do envelope e do payload, verificada no próprio produtor antes do envio

#### Scenario: Metadados de aquisição presentes

- **WHEN** um evento de dado bruto é inspecionado
- **THEN** ele SHALL conter a URL de origem, o encoding, o tamanho e o hash do conteúdo adquirido

### Requirement: Quebra do conteúdo em blocos

O feeder SHALL dividir o conteúdo adquirido em blocos de registros e publicar um evento por bloco, cada um declarando `loteId`, `sequencia` e `totalBlocos`. O feeder MUST NOT publicar o conteúdo adquirido como uma única mensagem.

O corte SHALL ser estrutural — sobre a estrutura do container do arquivo — e o feeder MUST NOT interpretar o significado financeiro dos campos, converter valores numéricos, derivar chave de instrumento nem aplicar política de arredondamento.

#### Scenario: Arquivo dividido

- **WHEN** um arquivo com muitos registros é adquirido
- **THEN** ele SHALL ser publicado em blocos, todos com o mesmo `loteId`, sequências distintas e o mesmo `totalBlocos`

#### Scenario: Arquivo menor que um bloco

- **WHEN** o conteúdo adquirido cabe em um único bloco
- **THEN** SHALL ser publicado um evento com `sequencia` 1 e `totalBlocos` 1

#### Scenario: Interpretação financeira proibida

- **WHEN** o código do feeder converte um valor numérico ou deriva significado financeiro de um campo
- **THEN** a revisão SHALL rejeitar a implementação, porque essa responsabilidade é do consumidor

#### Scenario: Falha no meio do arquivo

- **WHEN** o feeder falha após publicar parte dos blocos
- **THEN** o resultado SHALL ser falha, e os blocos já publicados SHALL permanecer identificáveis pelo `loteId` como lote incompleto

### Requirement: Publicação na faixa recebida

O disparo SHALL informar a faixa de ingestão — rotina, prioritária ou massa — e o feeder SHALL publicar no tópico correspondente. O feeder MUST NOT escolher a faixa por conta própria.

#### Scenario: Disparo manual

- **WHEN** o feeder é acionado com a faixa prioritária
- **THEN** todos os blocos SHALL ser publicados no tópico da faixa prioritária

#### Scenario: Faixa não informada

- **WHEN** o disparo não informa a faixa
- **THEN** o feeder SHALL falhar informando o parâmetro ausente, e MUST NOT assumir um padrão

### Requirement: Identificador de evento determinístico

O `loteId` SHALL ser derivado deterministicamente de `source`, `dataset`, `referenceDate` e do hash do conteúdo adquirido, e o `eventId` de cada bloco SHALL ser derivado de `loteId` e `sequencia`. Reexecutar o feeder com conteúdo idêntico SHALL produzir os mesmos identificadores; conteúdo diferente SHALL produzir identificadores diferentes.

#### Scenario: Redisparo com o mesmo conteúdo

- **WHEN** o feeder é executado duas vezes para a mesma data e a fonte devolve o mesmo conteúdo
- **THEN** os eventos SHALL ter os mesmos `loteId` e `eventId`, e o consumidor SHALL tratá-los como duplicados

#### Scenario: Fonte corrigiu o dado

- **WHEN** o feeder é reexecutado e a fonte devolve conteúdo diferente para a mesma data
- **THEN** o `loteId` e os `eventId` SHALL ser diferentes, e o dado corrigido SHALL ser processado a jusante

### Requirement: Propagação de correlation id

O feeder SHALL usar o `correlationId` recebido no disparo em todos os eventos publicados, em todos os logs e em todo reporte de progresso. O feeder MUST NOT gerar um `correlationId` próprio quando um foi fornecido.

#### Scenario: Correlação preservada

- **WHEN** o orquestrador dispara o feeder com um `correlationId`
- **THEN** o evento publicado e as linhas de log do feeder SHALL conter exatamente esse `correlationId`

### Requirement: Retentativa restrita a falha de transporte

Falhas de rede, timeout e erros de servidor SHALL ser retentadas com backoff exponencial e jitter, até o limite configurado. Erros de cliente e respostas estruturalmente inválidas MUST NOT ser retentados, e SHALL ser reportados imediatamente como falha permanente.

#### Scenario: Falha transitória se recupera

- **WHEN** a primeira tentativa falha por timeout e a segunda tem sucesso
- **THEN** o feeder SHALL retornar `PUBLISHED`, e o número de tentativas SHALL constar no reporte de execução

#### Scenario: Erro de cliente não é retentado

- **WHEN** a fonte responde erro de cliente
- **THEN** o feeder SHALL falhar na primeira tentativa, sem retentar

### Requirement: Reporte de progresso ao orquestrador

O feeder SHALL reportar início, resultado e diagnóstico da execução ao `curve-orchestrator`, incluindo `correlationId`, resultado, número de tentativas, duração e — quando aplicável — o `eventId` publicado ou o motivo da falha.

#### Scenario: Execução visível na tela

- **WHEN** uma execução termina em qualquer um dos três resultados
- **THEN** o reporte SHALL chegar ao orquestrador e o resultado SHALL ficar visível na tela de monitoramento

### Requirement: Dois modos de execução com o mesmo núcleo

A lógica de aquisição SHALL ser independente do runtime de hospedagem, e SHALL ser executável tanto como handler de Azure Function quanto como processo de container. Os adaptadores MUST NOT conter regra de aquisição.

#### Scenario: Teste sem runtime de nuvem

- **WHEN** a suíte de testes do núcleo é executada
- **THEN** ela SHALL rodar sem Azure e sem broker, usando apenas fixtures e dublês

#### Scenario: Mesmo comportamento nos dois modos

- **WHEN** a mesma aquisição roda pelo adaptador de container e pelo adaptador de função
- **THEN** o evento produzido SHALL ser idêntico, incluindo o `eventId`

### Requirement: Isolamento de fronteira

O feeder MUST NOT acessar banco de dados, MUST NOT consumir tópicos Kafka, e MUST NOT interpretar semanticamente o conteúdo adquirido além do necessário para verificar integridade e detectar ausência de publicação.

#### Scenario: Tentativa de acesso a banco

- **WHEN** o feeder declara dependência de driver de banco de dados
- **THEN** o teste de fronteira de dependência SHALL falhar o build

### Requirement: Extensão para novas fontes

Uma nova fonte SHALL ser adicionada implementando o mesmo contrato e publicando no mesmo tópico, sem alteração no contrato de evento nem no roteamento do consumidor.

#### Scenario: Feeder novo entra sem quebrar o consumidor

- **WHEN** um feeder de outra fonte é adicionado publicando um dataset com parser já registrado
- **THEN** o consumidor SHALL processá-lo sem alteração de código

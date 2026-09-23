## Purpose

Ingerir o arquivo `TaxaSwap.txt` da B3 — já disponibilizado no Blob Storage para uma data de referência — no banco oficial do projeto, disparado por um webhook de notificação ou por reprocessamento manual via endpoint.

## ADDED Requirements

### Requirement: Webhook de disponibilização do arquivo
O sistema SHALL expor um endpoint HTTP webhook que recebe a notificação de que o arquivo `TaxaSwap.txt` de uma data de referência está disponível no Blob Storage, e SHALL disparar a ingestão dessa data a partir dessa notificação.

#### Scenario: Notificação válida dispara a ingestão
- **WHEN** o webhook recebe uma notificação informando a data de referência X e o arquivo `b3/{X}/TaxaSwap.txt` existe no Blob Storage
- **THEN** o sistema inicia o processamento do arquivo daquela data e responde com sucesso

#### Scenario: Notificação sem data de referência é rejeitada
- **WHEN** o webhook recebe uma notificação sem data de referência ou com data em formato inválido
- **THEN** o sistema rejeita a requisição com erro 400, sem iniciar nenhum processamento

### Requirement: Download do arquivo já disponível no Blob Storage
O sistema SHALL buscar o conteúdo de `b3/{data}/TaxaSwap.txt` no Blob Storage para a data de referência informada, sem baixar nada diretamente da B3.

#### Scenario: Arquivo existe no Blob
- **WHEN** o sistema busca `b3/{data}/TaxaSwap.txt` para uma data cujo arquivo já foi carregado no Blob
- **THEN** o conteúdo do arquivo é obtido com sucesso e segue para a etapa de parse

#### Scenario: Arquivo não existe no Blob para a data informada
- **WHEN** o sistema busca `b3/{data}/TaxaSwap.txt` e o blob não existe para aquela data
- **THEN** o processamento é interrompido com um erro que identifica a data e o caminho de blob não encontrado, e nenhum dado é gravado no banco

### Requirement: Parse do arquivo conforme layout posicional da B3
O sistema SHALL interpretar o conteúdo do `TaxaSwap.txt` de acordo com o mesmo layout posicional de campos já usado pelo pipeline de download direto da B3, produzindo um registro por linha válida com ticker, código da curva, dias corridos, dias úteis e taxa.

#### Scenario: Linha bem formada é parseada
- **WHEN** uma linha do arquivo segue o layout posicional esperado
- **THEN** o sistema produz um registro com ticker, dias corridos, dias úteis e taxa extraídos corretamente

#### Scenario: Linha malformada é descartada sem interromper o arquivo inteiro
- **WHEN** uma linha do arquivo não pode ser parseada pelo layout posicional
- **THEN** essa linha é descartada e registrada como erro, e o processamento continua para as demais linhas do arquivo

### Requirement: Persistência dos vértices brutos no banco
O sistema SHALL persistir cada registro parseado como vértice bruto de curva no banco de dados, associado ao ticker/código de curva identificado (`cTickerIndcd`) e à data de referência.

#### Scenario: Registro com ticker já cadastrado no catálogo é persistido
- **WHEN** um registro parseado tem um ticker que já existe no catálogo de curvas do banco
- **THEN** o vértice bruto é gravado no banco associado a esse ticker e à data de referência do arquivo

#### Scenario: Registro com ticker desconhecido no catálogo não é persistido silenciosamente
- **WHEN** um registro parseado tem um ticker que não existe no catálogo de curvas do banco
- **THEN** esse registro não é gravado, e o sistema reporta explicitamente quais tickers ficaram sem catálogo ao final do processamento — sem interromper a gravação dos demais registros válidos

### Requirement: Reprocessamento manual via endpoint
O sistema SHALL expor um endpoint HTTP de reprocessamento que, dado um parâmetro de data de referência, executa a mesma ingestão (download do Blob, parse, persistência) de forma independente do webhook, podendo ser chamado a qualquer momento.

#### Scenario: Reprocessamento de uma data já processada anteriormente
- **WHEN** o endpoint de reprocessamento é chamado para uma data de referência cujos vértices brutos já haviam sido gravados anteriormente
- **THEN** o sistema substitui os vértices brutos previamente gravados para aquela data pelos vértices recém-parseados, sem duplicar registros

#### Scenario: Reprocessamento de uma data sem parâmetro
- **WHEN** o endpoint de reprocessamento é chamado sem informar a data de referência
- **THEN** o sistema rejeita a requisição com erro 400, sem executar nenhum processamento

### Requirement: Rastreabilidade do processamento
O sistema SHALL registrar, para cada execução de ingestão (via webhook ou reprocessamento), quantas linhas foram lidas, quantas foram persistidas com sucesso e quantas foram descartadas ou ficaram sem catálogo, de forma que a origem de cada divergência seja identificável.

#### Scenario: Execução com sucesso parcial é reportada de forma explícita
- **WHEN** uma execução de ingestão persiste alguns registros e descarta outros (por linha malformada ou ticker sem catálogo)
- **THEN** a resposta da execução informa a contagem de registros lidos, persistidos e descartados, e não reporta sucesso total quando houve descarte

## ADDED Requirements

### Requirement: Datasets B3 suportados

O feeder B3 SHALL adquirir os datasets `B3_PRICE_REPORT` (arquivo de Preços de Referência), `B3_BVBG086` (preços e ajustes), `B3_BVBG028` (cadastro de instrumentos) — todos de dado individual por instrumento — e `B3_REFERENCE_RATES` (curva pronta oficial), cada um identificado por `dataset` no envelope do evento.

#### Scenario: Aquisição de um dataset suportado

- **WHEN** o feeder é disparado para um dos datasets suportados em uma data de pregão com publicação
- **THEN** ele SHALL publicar um evento com o `dataset` correspondente e o conteúdo adquirido

#### Scenario: Dataset não suportado

- **WHEN** o feeder é disparado para um `dataset` que ele não implementa
- **THEN** ele SHALL falhar imediatamente informando o dataset solicitado e a lista dos suportados, sem chamar a fonte

### Requirement: Classificação do tipo de insumo

Todo evento publicado pelo feeder SHALL declarar `payloadKind` entre `INDIVIDUAL_QUOTES` — dado por instrumento — e `READY_CURVE` — curva já pronta, vértice a vértice. A classificação SHALL ser determinada pelo endpoint consultado, e MUST NOT ser inferida do conteúdo.

#### Scenario: Dado individual por instrumento

- **WHEN** o feeder adquire um dos datasets de preços ou de cadastro de instrumentos
- **THEN** o evento SHALL declarar `payloadKind` igual a `INDIVIDUAL_QUOTES`

#### Scenario: Curva pronta

- **WHEN** o feeder adquire a curva já pronta do endpoint de taxas de referência
- **THEN** o evento SHALL declarar `payloadKind` igual a `READY_CURVE` e SHALL identificar a curva solicitada no metadado

#### Scenario: Classificação ausente

- **WHEN** um evento é publicado sem `payloadKind`
- **THEN** a validação de schema no produtor SHALL falhar e o evento SHALL NOT ser enviado

### Requirement: Aquisição da curva pronta

O feeder SHALL adquirir a curva pronta da B3 montando o pedido conforme o contrato do endpoint, com a data de pregão e o identificador da curva, e SHALL publicar a resposta acompanhada do identificador de curva solicitado e do `payloadKind` `READY_CURVE`.

#### Scenario: Curva oficial de uma data

- **WHEN** o feeder é disparado para a curva pronta de um identificador de curva em uma data com divulgação
- **THEN** o evento publicado SHALL conter a resposta da B3, o identificador da curva e a classificação de curva pronta

#### Scenario: Curva sem divulgação na data

- **WHEN** a B3 responde sem conteúdo para o identificador de curva na data solicitada
- **THEN** o feeder SHALL retornar `NO_DATA` nomeando a curva e a data

### Requirement: Encoding e formato preservados

O feeder SHALL declarar no metadado do evento o encoding do conteúdo adquirido e SHALL publicar o conteúdo sem conversão de encoding, sem reformatação e sem conversão de separador decimal.

#### Scenario: Arquivo em ISO-8859-1

- **WHEN** o conteúdo adquirido está em ISO-8859-1 com decimal por vírgula
- **THEN** o evento SHALL declarar esse encoding, e o conteúdo publicado SHALL preservar os bytes originais

#### Scenario: Conversão indevida

- **WHEN** o feeder converte números do formato brasileiro para o formato americano antes de publicar
- **THEN** o teste SHALL falhar, porque a conversão pertence ao consumidor que detém a política de arredondamento

### Requirement: Verificação de integridade do conteúdo

O feeder SHALL verificar a integridade do conteúdo adquirido antes de publicar — conteúdo não vazio, tamanho compatível com o declarado pela resposta e, para arquivos compactados, abertura bem-sucedida do container. Conteúdo que falha na verificação MUST NOT ser publicado.

#### Scenario: Download truncado

- **WHEN** o conteúdo recebido é menor que o tamanho declarado pela resposta
- **THEN** o feeder SHALL tratar como falha transitória, retentar, e SHALL NOT publicar conteúdo truncado

#### Scenario: Arquivo compactado corrompido

- **WHEN** o arquivo compactado adquirido não pode ser aberto
- **THEN** o feeder SHALL retornar `FAILED` informando o arquivo e a causa, sem publicar

### Requirement: Calendário de pregão

O feeder SHALL usar o calendário de pregão B3, com os feriados reais, para decidir se a data solicitada é dia de pregão. Data que não é dia de pregão SHALL resultar em `NO_DATA` sem chamada à fonte. O feeder MUST NOT inferir dia útil apenas pelo dia da semana.

#### Scenario: Feriado nacional em dia útil

- **WHEN** a data solicitada é um feriado B3 que cai em dia de semana
- **THEN** o feeder SHALL retornar `NO_DATA` classificado como não-pregão, sem chamar a fonte

#### Scenario: Dia de pregão comum

- **WHEN** a data solicitada é dia de pregão
- **THEN** o feeder SHALL prosseguir com a aquisição normalmente

### Requirement: Distinção entre dado não divulgado e fonte indisponível

O feeder SHALL distinguir "dia de pregão cujo dado ainda não foi divulgado" de "fonte indisponível", e SHALL reportar o primeiro como `NO_DATA` com motivo `NOT_YET_PUBLISHED` e o segundo como `FAILED`.

#### Scenario: Consulta antes da divulgação

- **WHEN** o feeder consulta um dataset de um dia de pregão antes do horário de divulgação e a fonte responde sem conteúdo
- **THEN** o resultado SHALL ser `NO_DATA` com motivo `NOT_YET_PUBLISHED`, e o orquestrador SHALL poder reagendar sem tratar como erro

#### Scenario: Fonte fora do ar

- **WHEN** a fonte não responde
- **THEN** o resultado SHALL ser `FAILED`, e não `NO_DATA`

### Requirement: Suíte de testes offline com fixtures reais

Os testes do feeder SHALL rodar sem acesso à internet, contra fixtures gravadas de respostas reais da B3, versionadas com data de captura, URL de origem e encoding. Um teste de contrato que acessa a B3 real SHALL existir separadamente e MUST NOT fazer parte do build padrão.

#### Scenario: Build sem rede

- **WHEN** a suíte padrão é executada em uma máquina sem acesso à internet
- **THEN** todos os testes SHALL passar

#### Scenario: Detecção de mudança de contrato da fonte

- **WHEN** o teste de contrato opcional é executado deliberadamente e a resposta real da B3 diverge da fixture em estrutura
- **THEN** ele SHALL falhar apontando a divergência, permitindo atualizar o cliente e a fixture

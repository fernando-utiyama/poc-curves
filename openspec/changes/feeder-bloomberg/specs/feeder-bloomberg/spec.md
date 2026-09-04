## Purpose

Define o comportamento observável da aquisição de dado de mercado via Bloomberg (juros, câmbio e outros insumos de curva), entregue por arquivo em lote assíncrono — submeter pedido, aguardar geração, buscar o arquivo pronto — dentro do mesmo contrato de feeder já usado por B3/ANBIMA/BCB.

## ADDED Requirements

### Requirement: Aquisição assíncrona sem novo estado de resultado

O feeder Bloomberg SHALL submeter o pedido de dado, aguardar a geração do arquivo de saída e buscá-lo, tudo antes de devolver um resultado — o resultado final SHALL ser um dos três estados já existentes de aquisição (publicado, sem dado, ou falha), nunca um estado intermediário de "aguardando".

#### Scenario: Arquivo gerado e disponível dentro do prazo de espera

- **WHEN** o pedido é submetido e o arquivo de saída fica disponível antes do tempo máximo de espera configurado
- **THEN** o feeder SHALL buscar o arquivo, publicar seu conteúdo e retornar sucesso

#### Scenario: Tempo máximo de espera excedido

- **WHEN** o arquivo de saída não fica disponível dentro do tempo máximo de espera configurado
- **THEN** o feeder SHALL retornar falha nomeando o tempo de espera excedido e o identificador do pedido submetido, sem inventar um resultado parcial

#### Scenario: Fonte responde sem dado para o pedido

- **WHEN** Bloomberg processa o pedido e responde que não há dado disponível para os instrumentos/data solicitados
- **THEN** o feeder SHALL retornar "sem dado", nomeando os instrumentos e a data pedidos — não é uma falha

### Requirement: Restrito ao disparo agendado, nunca ao disparo síncrono interativo

O feeder Bloomberg SHALL estar disponível apenas pelo caminho de disparo agendado/em lote, nunca pelo caminho de disparo manual síncrono onde um operador aguarda a resposta na tela.

#### Scenario: Disparo pelo caminho agendado/em lote

- **WHEN** o dataset Bloomberg é solicitado pelo caminho de disparo agendado
- **THEN** o feeder SHALL processar a aquisição normalmente, incluindo a espera assíncrona

#### Scenario: Tentativa de disparo síncrono interativo

- **WHEN** o dataset Bloomberg é solicitado pelo caminho de disparo manual síncrono (onde um operador aguarda a resposta imediata)
- **THEN** o sistema SHALL recusar a solicitação imediatamente, nomeando que o dataset não está disponível nesse caminho — SHALL NOT bloquear a resposta esperando a geração do arquivo

### Requirement: Modelo de registro genérico por classe de ativo

Cada registro adquirido da Bloomberg SHALL carregar a classe de ativo (ex.: juros, câmbio, outro insumo de curva), o tipo de instrumento, o identificador do instrumento na Bloomberg, o nome do campo/medida consultado, o valor bruto (como texto, sem conversão numérica) e a data de referência — sem restringir a um instrumento ou classe de ativo específicos.

#### Scenario: Registro de instrumento de juros

- **WHEN** o pedido Bloomberg traz um instrumento de juros
- **THEN** o registro publicado SHALL identificar a classe de ativo como juros, preservando o mesmo formato genérico usado para qualquer outra classe

#### Scenario: Registro de instrumento de câmbio

- **WHEN** o pedido Bloomberg traz um instrumento de câmbio
- **THEN** o registro publicado SHALL identificar a classe de ativo como câmbio, sem exigir nenhum campo específico de juros

#### Scenario: Valor nunca convertido no feeder

- **WHEN** um registro é publicado
- **THEN** o campo de valor SHALL permanecer como texto bruto, exatamente como recebido da fonte — a conversão numérica e a política de arredondamento SHALL NOT acontecer nesta camada

### Requirement: Corte estrutural, não semântico

O feeder Bloomberg SHALL cortar o arquivo de saída em blocos por fronteira estrutural do próprio arquivo (ex.: uma linha/registro por bloco), preservando o conteúdo original de cada registro sem interpretar seu significado.

#### Scenario: Arquivo com múltiplos registros

- **WHEN** o arquivo de saída contém múltiplos registros de instrumentos
- **THEN** cada bloco publicado SHALL corresponder a um corte estrutural do arquivo original, sem agregação nem interpretação de conteúdo

### Requirement: Falha real da fonte é explícita, nunca fabricada

Qualquer falha real na submissão do pedido, na busca do arquivo ou na leitura do conteúdo SHALL ser retornada como falha nomeada com o motivo e o diagnóstico técnico — o feeder SHALL NOT retornar sucesso parcial nem inventar dado ausente.

#### Scenario: Falha ao submeter o pedido

- **WHEN** a submissão do pedido à Bloomberg falha (rede, autenticação, ou rejeição da fonte)
- **THEN** o feeder SHALL retornar falha nomeando a etapa (submissão) e o motivo real reportado pela fonte ou pela camada de transporte

#### Scenario: Falha ao buscar o arquivo pronto

- **WHEN** o arquivo é sinalizado como pronto mas a busca falha (rede, arquivo corrompido, conteúdo vazio)
- **THEN** o feeder SHALL retornar falha nomeando a etapa (busca do arquivo) e o motivo real, sem publicar conteúdo parcial ou corrompido

## ADDED Requirements

### Requirement: Modelo relacional canônico

O banco SHALL conter as tabelas `definicao_curva`, `versao_definicao_curva`, `ponto_dado_mercado`, `lote_ingestao`, `execucao_curva`, `versao_curva`, `vertice_curva` e `procedencia_curva`, com as relações declaradas por chave estrangeira. Nenhum componente SHALL persistir estado de domínio fora deste modelo.

#### Scenario: Esquema criado do zero

- **WHEN** as migrações são aplicadas em um banco vazio
- **THEN** todas as tabelas do modelo SHALL existir com suas chaves primárias, estrangeiras e restrições de unicidade

#### Scenario: Vértice sem versão de curva

- **WHEN** uma tentativa de inserir `vertice_curva` referencia uma `versao_curva` inexistente
- **THEN** a inserção SHALL falhar por violação de chave estrangeira

### Requirement: Modo de origem na definição de curva

`definicao_curva` SHALL ter uma coluna de modo de origem restrita a `BOOTSTRAPPED` e `IMPORTED`. Curvas dos dois modos SHALL compartilhar as tabelas `versao_curva`, `vertice_curva` e `procedencia_curva`, sem estrutura paralela.

#### Scenario: Definição sem modo de origem

- **WHEN** uma definição de curva é criada sem modo de origem
- **THEN** a inserção SHALL falhar por restrição de coluna obrigatória

#### Scenario: Modo inválido

- **WHEN** uma definição declara um modo de origem fora do conjunto permitido
- **THEN** a inserção SHALL falhar por violação de restrição de valor

#### Scenario: Consulta uniforme entre modos

- **WHEN** vértices de uma curva construída e de uma curva importada são consultados
- **THEN** ambos SHALL vir das mesmas tabelas, com o mesmo formato de linha

### Requirement: Proveniência conforme o modo de origem

`procedencia_curva` SHALL registrar, para curvas `BOOTSTRAPPED`, a versão da definição aplicada e os pontos de market data usados; e, para curvas `IMPORTED`, o lote de ingestão, o arquivo de origem e seu hash. Em ambos os casos o registro SHALL ser obrigatório para a versão chegar a `PUBLISHED`.

#### Scenario: Proveniência de curva construída

- **WHEN** uma curva construída é publicada
- **THEN** a proveniência SHALL listar os insumos individuais usados e a versão da definição aplicada

#### Scenario: Proveniência de curva importada

- **WHEN** uma curva importada é publicada
- **THEN** a proveniência SHALL identificar o lote de ingestão, o arquivo de origem e o hash do conteúdo recebido

### Requirement: Prazo de publicação e orçamento na definição de curva

`definicao_curva` SHALL ter `horario_limite_publicacao` obrigatório, e `versao_definicao_curva` SHALL declarar o orçamento de tempo por etapa e a duração da janela de bloqueio de carga histórica.

#### Scenario: Definição sem horário limite

- **WHEN** uma definição de curva é criada sem horário limite de publicação
- **THEN** a inserção SHALL falhar por restrição de coluna obrigatória

#### Scenario: Orçamento consultável

- **WHEN** o orçamento de uma etapa é consultado
- **THEN** SHALL vir da versão de definição vigente na data da execução

### Requirement: Tempo e faixa na execução

`execucao_curva` SHALL registrar a faixa de ingestão usada (`ROTINA`, `PRIORITARIA` ou `MASSA`), o horário limite aplicável, a margem em relação a ele no encerramento, e a duração efetiva de cada etapa percorrida.

#### Scenario: Margem registrada

- **WHEN** uma execução conclui com publicação
- **THEN** a margem em relação ao horário limite SHALL ser persistida, permitindo acompanhar a distribuição ao longo dos dias

#### Scenario: Faixa registrada

- **WHEN** uma execução é criada por disparo manual
- **THEN** a faixa `PRIORITARIA` SHALL constar no registro

#### Scenario: Duração por etapa

- **WHEN** uma etapa conclui
- **THEN** a sua duração efetiva SHALL ser persistida, para calibrar o orçamento com série medida

### Requirement: Origem e contingência na versão de curva

`versao_curva` SHALL ter coluna de origem restrita a `CALCULADA`, `IMPORTADA` e `CARREGADA`. Para versões `CARREGADA`, `procedencia_curva` SHALL registrar o nome do arquivo, o hash do conteúdo, o autor da carga, o instante e a justificativa — todos obrigatórios.

#### Scenario: Origem obrigatória

- **WHEN** uma versão de curva é criada sem origem declarada
- **THEN** a inserção SHALL falhar por restrição de coluna obrigatória

#### Scenario: Carga sem justificativa

- **WHEN** uma versão de origem `CARREGADA` é gravada sem justificativa
- **THEN** a gravação SHALL falhar, porque contingência sem motivo registrado não é auditável

#### Scenario: Consulta uniforme entre origens

- **WHEN** vértices de versões de origens diferentes são consultados
- **THEN** todos SHALL vir das mesmas tabelas, com o mesmo formato de linha e a origem indicada

### Requirement: Registro de validação de curva

O banco SHALL conter `validacao_curva`, registrando, por `versao_curva`, o resultado de cada teste de consistência: identificador do teste, classificação (`BLOQUEANTE` ou `AVISO`), resultado (`APROVADO`, `REPROVADO` ou `NAO_APLICAVEL`), a medida observada, o limite aplicado e o instante da execução do teste.

O registro SHALL ser persistido tanto em aprovação quanto em reprovação, e SHALL permanecer consultável junto com a versão a que se refere.

#### Scenario: Reprovação auditável

- **WHEN** uma versão é reprovada por um teste bloqueante
- **THEN** o resultado de todos os testes executados SHALL permanecer persistido, com valores observados e limites aplicados

#### Scenario: Teste não aplicável

- **WHEN** um teste depende da curva do dia anterior e ela não existe
- **THEN** o resultado SHALL ser registrado como não aplicável, e MUST NOT ser contado como aprovação

#### Scenario: Aviso visível junto da curva

- **WHEN** uma curva publicada teve teste de aviso reprovado
- **THEN** a consulta da versão SHALL permitir recuperar o aviso, o teste e a medida observada

### Requirement: Precisão numérica no armazenamento

Todo valor com política de arredondamento de mercado — taxa, preço, cotação, fator e VNA — SHALL ser armazenado como `DECIMAL(28,12)`. As colunas correspondentes MUST NOT usar `FLOAT`, `REAL` ou qualquer tipo de ponto flutuante.

#### Scenario: Coluna de taxa em ponto flutuante

- **WHEN** uma migração declara uma coluna de taxa como `FLOAT`
- **THEN** a revisão de esquema SHALL rejeitar a migração, e o teste de conformidade de esquema SHALL falhar

#### Scenario: Ida e volta sem perda

- **WHEN** um valor de taxa com doze casas decimais é gravado e lido de volta
- **THEN** o valor lido SHALL ser exatamente igual ao gravado, dígito a dígito

### Requirement: Idempotência da persistência de market data

`ponto_dado_mercado` SHALL ter restrição de unicidade sobre `(fonte, conjunto_dados, data_referencia, chave_instrumento)`. A gravação SHALL ser um *upsert* determinístico: reprocessar o mesmo insumo não SHALL criar linha duplicada.

#### Scenario: Mesmo ponto gravado duas vezes

- **WHEN** o mesmo `(fonte, conjunto_dados, data_referencia, chave_instrumento)` é gravado duas vezes com o mesmo valor
- **THEN** SHALL existir exatamente uma linha, e o contador de versões do lote SHALL registrar o reprocessamento

#### Scenario: Mesmo ponto com valor divergente

- **WHEN** o mesmo ponto é regravado com valor diferente do anterior
- **THEN** o valor SHALL ser atualizado, e a divergência SHALL ser registrada em `lote_ingestao` para auditoria

### Requirement: Versionamento de curva publicada

Cada construção bem-sucedida SHALL criar uma nova linha em `versao_curva` para `(definicao_curva_id, data_referencia, momento_curva)`, com número de versão incremental. A versão anterior SHALL ser marcada como `SUPERSEDED`, e seus vértices MUST NOT ser alterados nem removidos.

#### Scenario: Segunda publicação da mesma data

- **WHEN** a mesma data é publicada uma segunda vez
- **THEN** a nova versão SHALL ter número incrementado e estado `PUBLISHED`, e a versão anterior SHALL ter estado `SUPERSEDED` com seus vértices preservados

#### Scenario: Unicidade da versão vigente

- **WHEN** o banco é consultado por `(definicao_curva_id, data_referencia, momento_curva)`
- **THEN** SHALL existir no máximo uma versão em estado `PUBLISHED`

### Requirement: Proveniência da curva

Toda `versao_curva` SHALL ter registro em `procedencia_curva` identificando o `execucao_curva` que a gerou, a `versao_definicao_curva` aplicada, a lista dos insumos usados e o hash do conjunto de insumos. Uma versão sem proveniência completa MUST NOT ser marcada como `PUBLISHED`.

#### Scenario: Consultar a origem de um vértice

- **WHEN** um usuário consulta a proveniência de uma curva publicada
- **THEN** a resposta SHALL identificar o run, a versão da definição, os insumos e o hash do conjunto de insumos

#### Scenario: Publicação sem proveniência

- **WHEN** a construção conclui mas o registro de proveniência não pode ser gravado
- **THEN** a transação inteira SHALL ser revertida e nenhuma versão SHALL ficar `PUBLISHED`

### Requirement: Catálogo de modelos de construção

O banco SHALL conter `modelo_curva` registrando os modelos de construção disponíveis, com identificador, nome, tipo (`BUILTIN` ou `GROOVY`), estado (`ACTIVE` ou `DISABLED`) e, para modelos Groovy, o código-fonte ou sua referência, o checksum e quem importou. `versao_definicao_curva` SHALL referenciar o modelo que a curva usa.

#### Scenario: Curva aponta para modelo padrão

- **WHEN** uma definição de curva é criada sem escolha explícita de modelo
- **THEN** ela SHALL referenciar o modelo embutido padrão daquele tipo de curva

#### Scenario: Troca de modelo

- **WHEN** a definição passa a apontar para um modelo Groovy importado
- **THEN** a referência SHALL ser atualizada em uma nova versão da definição, preservando a anterior

#### Scenario: Modelo referenciado não existe

- **WHEN** uma definição referencia um modelo ausente do catálogo
- **THEN** a operação SHALL falhar por violação de chave estrangeira

### Requirement: Modelo usado na proveniência da curva

`procedencia_curva` SHALL registrar, para toda curva `BOOTSTRAPPED`, o modelo de construção que produziu os vértices e — para modelos Groovy — o checksum do código executado. Uma curva construída sem essa informação MUST NOT chegar a `PUBLISHED`.

#### Scenario: Curva mudou porque o modelo mudou

- **WHEN** a mesma data é reconstruída com outro modelo e os mesmos insumos
- **THEN** uma nova versão de curva SHALL ser publicada, e a proveniência de cada uma SHALL identificar o modelo correspondente, tornando a causa da diferença explícita

#### Scenario: Curva importada não tem modelo

- **WHEN** uma curva `IMPORTED` é publicada
- **THEN** a proveniência SHALL identificar o arquivo de origem, e o campo de modelo SHALL ficar vazio, porque não houve cálculo

### Requirement: Registro de pendências de dead-letter

O banco SHALL conter `pendencia_dlq`, registrando cada mensagem enviada a uma dead-letter, com `id_evento` (único), `correlacao_id`, motivo, detalhe, fonte, conjunto de dados, data de referência, tópico original, tópico de dead-letter e suas coordenadas, versão da aplicação, instante da falha, contagem de tentativas, estado, e — quando encerrada — o desfecho, o instante, o responsável e a justificativa.

A tabela SHALL ter unicidade sobre `id_evento` e índice que sustente a contagem de pendências abertas agrupadas por `(motivo, fonte, conjunto_dados, data_referencia)`.

#### Scenario: Unicidade por evento

- **WHEN** a mesma mensagem de dead-letter é materializada duas vezes
- **THEN** a segunda inserção SHALL falhar por violação de unicidade, e SHALL existir exatamente uma pendência

#### Scenario: Payload não é armazenado

- **WHEN** uma pendência é gravada
- **THEN** SHALL conter as coordenadas da mensagem na dead-letter, e MUST NOT conter o payload original

#### Scenario: Contagem eficiente para o alerta

- **WHEN** a contagem de grupos com pendência aberta é consultada
- **THEN** o plano de execução SHALL usar o índice de agrupamento, sem varredura completa da tabela

#### Scenario: Encerramento registrado

- **WHEN** uma pendência é resolvida, descartada ou marcada como obsoleta
- **THEN** o desfecho, o instante e o responsável SHALL ser persistidos, e a linha SHALL permanecer consultável

### Requirement: Migrações versionadas com Flyway

O esquema SHALL ser gerenciado exclusivamente por migrações Flyway versionadas em `db/migration`. Migração já aplicada MUST NOT ser editada; correção SHALL ser feita por nova migração. Nenhum componente SHALL alterar esquema em tempo de execução.

#### Scenario: Aplicação em banco limpo

- **WHEN** o container de migração roda contra um banco vazio
- **THEN** todas as migrações SHALL ser aplicadas em ordem e o container SHALL terminar com código de saída zero

#### Scenario: Migração já aplicada foi alterada

- **WHEN** o conteúdo de uma migração já aplicada é modificado
- **THEN** o Flyway SHALL falhar por divergência de checksum e a subida SHALL ser interrompida

### Requirement: Índices para o caminho de consulta

O modelo SHALL prover índices que sustentem as consultas de produto sem varredura completa: curva vigente por `(definicao_curva_id, data_referencia, momento_curva, estado)`, vértices por `(versao_curva_id, prazo)`, e market data por `(fonte, conjunto_dados, data_referencia)`.

#### Scenario: Consulta de vértices por versão

- **WHEN** a lista de vértices de uma versão de curva é consultada
- **THEN** o plano de execução SHALL usar o índice de `(versao_curva_id, prazo)`, sem varredura completa da tabela

### Requirement: Retenção e reprocessamento

O modelo SHALL preservar indefinidamente as curvas publicadas e sua proveniência dentro do escopo da POC. A remoção de dado histórico SHALL ser uma operação explícita e registrada, nunca um efeito colateral de reprocessamento ou de nova publicação.

#### Scenario: Reprocessamento não apaga histórico

- **WHEN** uma data é reprocessada várias vezes
- **THEN** todas as versões anteriores SHALL permanecer consultáveis com seus vértices e sua proveniência

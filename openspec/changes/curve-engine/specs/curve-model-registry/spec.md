## ADDED Requirements

### Requirement: Catálogo de modelos de construção

O motor SHALL manter um catálogo de modelos de construção, cada um com identificador, nome, tipo (`BUILTIN` ou `GROOVY`), estado (`ACTIVE` ou `DISABLED`), autor e data de registro. Os dois tipos SHALL implementar a mesma interface de construção e ser intercambiáveis do ponto de vista do motor.

#### Scenario: Listar modelos disponíveis

- **WHEN** o catálogo de modelos é consultado
- **THEN** a resposta SHALL listar os modelos embutidos e os Groovy importados, cada um com seu tipo e estado

#### Scenario: Modelo desabilitado

- **WHEN** um modelo é marcado como `DISABLED`
- **THEN** ele MUST NOT ser selecionável por nenhuma definição de curva, e definições que já o referenciam SHALL falhar a construção nomeando o modelo desabilitado

### Requirement: Modelos embutidos registrados na inicialização

O motor SHALL registrar automaticamente seus modelos embutidos no catálogo ao iniciar, sem exigir importação. Toda curva suportada SHALL ter um modelo embutido padrão.

#### Scenario: Plataforma recém-instalada

- **WHEN** o motor sobe contra um banco sem nenhum modelo importado
- **THEN** os modelos embutidos SHALL estar disponíveis, e a construção da curva PRE de DI1 da B3 SHALL funcionar com o modelo padrão

#### Scenario: Modelo embutido não pode ser removido

- **WHEN** uma tentativa de excluir um modelo embutido é feita
- **THEN** a operação SHALL ser recusada, porque ele é versionado junto com o motor

### Requirement: Importação de modelo Groovy

O motor SHALL permitir importar um modelo Groovy, registrando o código-fonte, seu checksum, o autor e o momento da importação. A importação SHALL validar que o script compila e que executa contra insumos de amostra produzindo vértices. Script que falha em qualquer etapa MUST NOT ser registrado.

#### Scenario: Importação bem-sucedida

- **WHEN** um script Groovy válido é importado
- **THEN** ele SHALL ser registrado no catálogo com checksum e autor, e SHALL ficar disponível para seleção

#### Scenario: Script não compila

- **WHEN** o script tem erro de compilação
- **THEN** a importação SHALL falhar informando o erro, e nada SHALL ser registrado

#### Scenario: Script não produz vértices

- **WHEN** o script compila mas não devolve vértices na execução contra insumos de amostra
- **THEN** a importação SHALL falhar informando o comportamento observado

#### Scenario: Reimportação com conteúdo diferente

- **WHEN** um modelo já existente é reimportado com código diferente
- **THEN** o checksum registrado SHALL ser atualizado, e as curvas já publicadas SHALL continuar apontando na proveniência para o checksum que efetivamente rodou

### Requirement: Seleção do modelo pela definição de curva

Cada versão de definição de curva SHALL referenciar exatamente um modelo de construção. Definição criada sem escolha explícita SHALL referenciar o modelo embutido padrão do seu tipo de curva. Trocar o modelo SHALL gerar uma nova versão da definição, preservando a anterior.

#### Scenario: Curva nasce com o modelo padrão

- **WHEN** a curva PRE de DI1 da B3 é cadastrada sem escolha de modelo
- **THEN** ela SHALL apontar para o modelo embutido padrão dessa curva

#### Scenario: Troca para modelo Groovy

- **WHEN** um operador autorizado troca o modelo da definição para um Groovy importado
- **THEN** uma nova versão da definição SHALL ser criada apontando para o novo modelo, e a próxima construção SHALL usá-lo

#### Scenario: Volta ao modelo embutido

- **WHEN** a definição é apontada de volta para o modelo embutido
- **THEN** a construção seguinte SHALL usar o modelo embutido, sem necessidade de recompilar ou redeployar o motor

#### Scenario: Histórico preservado

- **WHEN** o modelo de uma curva é trocado
- **THEN** as curvas publicadas antes da troca SHALL continuar explicadas pela versão de definição vigente na época

### Requirement: Troca de modelo sem build nem deploy

Importar um modelo Groovy e trocar o modelo de uma definição SHALL surtir efeito com o motor em execução, sem recompilação, sem novo artefato e sem reinício do serviço.

#### Scenario: Efeito imediato com o serviço no ar

- **WHEN** o modelo de uma curva é trocado enquanto o motor está em execução
- **THEN** a construção seguinte SHALL usar o novo modelo, sem que o motor tenha sido reiniciado

### Requirement: Contenção da execução de modelo Groovy

A execução de modelo Groovy SHALL ocorrer em classloader isolado, sem acesso a arquivo, rede ou criação de processo, e sob limite de tempo e de memória. Violação SHALL abortar a execução, falhar o run nomeando a violação, e nada SHALL ser publicado.

#### Scenario: Modelo tenta acessar rede

- **WHEN** um modelo Groovy tenta abrir conexão de rede
- **THEN** a execução SHALL ser abortada, o run SHALL ir para `FAILED` nomeando a violação, e nenhuma curva SHALL ser publicada

#### Scenario: Modelo tenta ler arquivo

- **WHEN** um modelo Groovy tenta ler ou escrever arquivo
- **THEN** a execução SHALL ser abortada com a violação nomeada

#### Scenario: Modelo excede o tempo limite

- **WHEN** a execução ultrapassa o limite de tempo configurado
- **THEN** ela SHALL ser interrompida, o run SHALL falhar informando o limite e o tempo decorrido, e o motor SHALL continuar atendendo os demais pedidos

#### Scenario: Modelo lança exceção

- **WHEN** o modelo lança exceção durante a execução
- **THEN** o run SHALL falhar registrando o erro e o modelo responsável, sem derrubar o motor

### Requirement: Superfície exposta ao modelo

O modelo SHALL receber as estruturas do kernel, os insumos já resolvidos em modo somente-leitura e um contexto com data de referência, momento da curva e parâmetros da definição, e SHALL devolver a lista de vértices. O modelo MUST NOT receber conexão de banco, cliente HTTP ou relógio mutável, e MUST NOT consultar dado por conta própria.

#### Scenario: Insumo entregue pronto

- **WHEN** um modelo é executado
- **THEN** ele SHALL receber os insumos já resolvidos pelo motor, e MUST NOT ter meio de buscar dado adicional

#### Scenario: Tentativa de alterar insumo

- **WHEN** o modelo tenta modificar a coleção de insumos recebida
- **THEN** a operação SHALL falhar, porque os insumos são somente leitura

### Requirement: Autorização para gerir modelos

Importar modelo Groovy, desabilitar modelo e trocar o modelo de uma definição de curva SHALL exigir perfil de administrador de curva. Consultar o catálogo SHALL ser permitido a qualquer usuário autenticado.

#### Scenario: Usuário sem permissão tenta trocar modelo

- **WHEN** um usuário com perfil de leitor tenta trocar o modelo de uma curva
- **THEN** a operação SHALL ser recusada por falta de autorização, e nenhuma versão de definição SHALL ser criada

### Requirement: Comparação entre modelos

O motor SHALL permitir construir a mesma curva e a mesma data com dois modelos distintos e comparar os vértices resultantes, reportando as diferenças prazo a prazo. A construção de comparação MUST NOT publicar curva.

#### Scenario: Comparar modelo Groovy contra o embutido

- **WHEN** a comparação é solicitada entre o modelo embutido e um Groovy importado, para a mesma curva e data
- **THEN** a resposta SHALL listar, por prazo, o valor de cada modelo e a diferença, e nenhuma `versao_curva` SHALL ser criada

#### Scenario: Um dos modelos falha

- **WHEN** um dos modelos falha durante a comparação
- **THEN** a resposta SHALL informar qual modelo falhou e por quê, sem mascarar a falha como diferença de valor

## ADDED Requirements

### Requirement: Ambiente local completo em Podman rootless

O ambiente local SHALL subir a plataforma inteira com Podman rootless, sem Docker e sem qualquer recurso da Azure. Nenhum serviço SHALL exigir privilégio de root, rede em modo host, nem porta abaixo de 1024.

#### Scenario: Subida em máquina sem Docker

- **WHEN** o desenvolvedor executa o comando de subida em uma máquina que tem Podman e não tem Docker
- **THEN** todos os serviços SHALL subir e ficar saudáveis, sem erro de socket, de privilégio ou de porta

#### Scenario: Nenhuma dependência de Azure

- **WHEN** o ambiente sobe sem qualquer credencial ou conectividade com a Azure
- **THEN** o fluxo ponta a ponta SHALL funcionar integralmente com os serviços locais

### Requirement: Serviços de infraestrutura local

O ambiente SHALL prover Kafka em modo KRaft, SQL Server 2022, Redis e um provedor OIDC local, cada um com healthcheck declarado, volume nomeado para persistência e portas publicadas de forma determinística.

#### Scenario: Infra saudável

- **WHEN** a etapa de infraestrutura termina
- **THEN** Kafka, SQL Server, Redis e o provedor OIDC SHALL responder ao respectivo healthcheck antes de a etapa seguinte iniciar

#### Scenario: Estado preservado entre reinícios

- **WHEN** o ambiente é derrubado sem remover volumes e subido novamente
- **THEN** o banco e os tópicos SHALL preservar o dado do ciclo anterior

### Requirement: Inicialização determinística

A subida SHALL executar, em ordem e antes de qualquer serviço de aplicação: criação dos tópicos do catálogo, aplicação das migrações Flyway e carga do seed de dados. Cada etapa SHALL terminar com código de saída zero antes de a próxima iniciar, sem depender de espera por tempo fixo.

#### Scenario: Ordem respeitada mesmo sem suporte a depends_on

- **WHEN** a versão do compose em uso ignora `depends_on` com condição de saúde
- **THEN** o script de subida SHALL fazer espera ativa por serviço, verificando prontidão real, e SHALL falhar com mensagem clara se o limite de espera for excedido

#### Scenario: Migração falha

- **WHEN** a aplicação das migrações falha
- **THEN** a subida SHALL ser interrompida com erro, e nenhum serviço de aplicação SHALL iniciar contra um banco em estado inconsistente

### Requirement: Comando único de subida e derrubada

O repositório SHALL prover um comando único para subir o ambiente completo, um para derrubar preservando dado, e um para derrubar removendo volumes. Os comandos SHALL funcionar tanto em Windows quanto em Linux.

#### Scenario: Subida em um comando

- **WHEN** o desenvolvedor executa o comando de subida em um clone limpo do repositório
- **THEN** o ambiente SHALL ficar pronto para uso sem nenhum passo manual adicional

#### Scenario: Ambiente descartável

- **WHEN** o comando de derrubada com remoção de volumes é executado
- **THEN** todo o estado local SHALL ser removido, e uma nova subida SHALL partir de um ambiente limpo

### Requirement: Seed de dados B3 para demonstração

O ambiente SHALL carregar um seed determinístico com pelo menos uma data de pregão B3 completa e a taxa de referência oficial correspondente, permitindo executar o fluxo sem acesso à internet.

#### Scenario: Demonstração offline

- **WHEN** o ambiente sobe sem conectividade externa
- **THEN** o fluxo ponta a ponta SHALL rodar sobre o seed, e a curva SHALL ser publicada normalmente

#### Scenario: Seed reprodutível

- **WHEN** o ambiente é recriado do zero
- **THEN** o seed SHALL produzir exatamente o mesmo conjunto de dados da execução anterior

### Requirement: Smoke test de aceite ponta a ponta

O repositório SHALL prover um smoke test executável em um comando que dispara a ingestão de uma data, aguarda a publicação da curva, consulta a curva interpolada e reconcilia os vértices contra a taxa de referência oficial da B3, falhando com diagnóstico se qualquer etapa não concluir.

#### Scenario: Aceite verde

- **WHEN** o smoke test roda contra um ambiente recém-subido
- **THEN** ele SHALL concluir com sucesso, e SHALL reportar a curva publicada e o resultado da reconciliação vértice a vértice

#### Scenario: Aceite vermelho com diagnóstico

- **WHEN** a curva não é publicada dentro do tempo limite
- **THEN** o smoke test SHALL falhar informando em qual etapa o fluxo parou e qual foi o `correlationId` do run

### Requirement: Perfis de recurso do ambiente

O ambiente SHALL oferecer um perfil completo e um perfil reduzido, para máquinas com menos memória disponível. O perfil reduzido SHALL manter o fluxo essencial de ingestão, construção e consulta de curva.

#### Scenario: Máquina com pouca memória

- **WHEN** o desenvolvedor sobe o perfil reduzido
- **THEN** os serviços opcionais SHALL ser omitidos, e o fluxo essencial SHALL continuar funcionando

### Requirement: Procedimento de emergência para fila travada

O repositório SHALL documentar e prover o procedimento de emergência para destravar uma partição, incluindo a inspeção do offset e do lag por partição, o avanço manual do offset e o registro da mensagem pulada como pendência para tratamento posterior. O procedimento SHALL ser ensaiado no ambiente local.

#### Scenario: Ensaio do procedimento

- **WHEN** o procedimento é executado no ambiente local contra uma partição deliberadamente travada
- **THEN** a partição SHALL voltar a avançar, e a mensagem pulada SHALL ficar registrada como pendência

#### Scenario: Operação destrutiva sinalizada

- **WHEN** a documentação do procedimento é consultada
- **THEN** SHALL deixar explícito que avançar o offset manualmente descarta o processamento daquela mensagem e exige aprovação

### Requirement: Requisitos e diagnóstico documentados

O repositório SHALL documentar os requisitos do ambiente — versão mínima do Podman, memória necessária, portas usadas e configuração do socket para testes de integração — e prover um comando de diagnóstico que verifique esses pré-requisitos.

#### Scenario: Pré-requisito ausente

- **WHEN** o comando de diagnóstico roda em uma máquina sem o socket do Podman habilitado
- **THEN** ele SHALL apontar exatamente o pré-requisito ausente e como corrigi-lo

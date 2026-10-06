## Context

A tela de tarefas já existe, feita por outro desenvolvedor (CRUD e execução). O backend v0 (`orquestrador-v0-disparo-manual`) mudou a execução: data-base opcional, `incluirDownload` e a resposta com o resultado da carga. A v1 (`orquestrador-curvas`) acrescenta `GET /api/v1/alertas`. Não há autenticação na v0 e na v1.

## Goals / Non-Goals

**Goals:** encaixar o contrato novo na tela que existe, com o menor diff possível.

**Non-Goals:** redesenhar a tela, mudar o CRUD, criar uma tela nova, autenticação.

## Decisions

### D1. Aberto para quem implementa
A change dá o contrato e o comportamento; onde colocar cada campo, que componente usar e como nomear segue o que a tela já tem. Em dúvida, vale o menor diff que cumpra a spec.

### D2. Caminho da chamada
A execução vai pelo bff, como manda a `orquestrador-v0-disparo-manual`. Se a tela já chama o orquestrador por outro caminho que funciona, manter e deixar `TODO(revisao)`; não refazer o proxy por causa desta change.

### D3. Tempo limite
A execução espera a carga inteira; o tempo limite padrão do front (3 s no `fed`) não serve. No `fed`, usar o `TEMPO_LIMITE_MS` da change `fed-curvas-mercado` com 130000; em outro projeto, o mecanismo equivalente.

## Risks / Trade-offs

- [A tela pode estar diferente do que a spec supõe] → a spec descreve o resultado, não a estrutura; o agente ajusta ao que encontrar.
- [Alertas antes da v1] → a consulta falha em silêncio até a rota existir.

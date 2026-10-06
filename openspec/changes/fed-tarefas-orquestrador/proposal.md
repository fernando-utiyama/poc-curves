## Why

O orquestrador v0 passou a aceitar a execução manual com data-base opcional e a devolver o resultado da carga, e a v1 grava alertas consultáveis (`CARGA_NAO_RECEBIDA`). A tela de tarefas do front já existe (CRUD e execução, começada por outro desenvolvedor), mas ainda não conhece esse contrato. Esta change só ajusta essa tela; não reescreve nem cria outra.

## What Changes

- Na execução de uma tarefa de download: campo de data-base opcional, opção "Baixar de novo da fonte" e o resultado devolvido em pt-BR.
- Um quadro de alertas do dia (orquestrador v1), na própria tela de tarefas ou onde couber melhor no que já existe.
- Sem autenticação na v0 e na v1: nenhuma chamada manda token.

## Capabilities

### New Capabilities
- `fed-tarefas-orquestrador`: ajustes da tela de tarefas que já existe para a execução com data (orquestrador v0) e para os alertas (orquestrador v1).

### Modified Capabilities

## Impact

- **Front** (o projeto onde a tela de tarefas estiver): o componente de execução, o serviço de tarefas e, se faltar, a rota do proxy para o bff.
- Contratos: `POST /api/v1/agendador/tarefas/{id}/executar?dataBase=&incluirDownload=` (pelo bff, change `orquestrador-v0-disparo-manual`) e `GET /api/v1/alertas?dataInicial=&dataFinal=` (change `orquestrador-curvas`).
- Nenhuma mudança de backend.

## Why

O endpoint de carga já existe (feito por outros desenvolvedores para B3 e ANBIMA): o orquestrador chama **um endpoint só**, com o provedor no corpo, e ele entrega ao caso de uso e ao serviço da fonte, que baixa do site, grava o original no Blob e grava o dado no banco. A Bloomberg (`processor-bloomberg`) entra como mais um provedor desse mesmo endpoint. Falta o último passo, igual nas três: **disparar o engine** para construir as curvas daquela carga. Sem um contrato único, cada serviço pode disparar de um jeito, ou nem disparar.

Esta change define só esse último passo: o que o serviço de cada fonte manda ao engine depois de gravar, e o que o engine faz com isso.

## What Changes

- **Contrato do aviso:** um `POST /api/v1/cargas` do engine por carga gravada, depois do commit, sem segurar a resposta ao orquestrador, com `idCarga`, `fonte`, `produto`, `dataBase` e `verticesPorCurva`.
- **`verticesPorCurva`, pelo nome da curva** (o campo `verticesPorCodigo` do webhook é renomeado): nome da curva e linhas gravadas dela, igual nas três fontes.
- **Repetição, prazos e logs iguais** para as três fontes: espera dobrando até 60 s, janela de 10 minutos, `AVISO_ATRASADO` aos 2 minutos, `CARGA_FALHOU` ao fim, contador `processor.carga.falhou` por fonte.
- **Quando avisa:** download, upload e reprocessamento, com o `idCarga` da carga lida; **quando não avisa:** carga rejeitada, ou sem nenhum código mapeado.
- **Engine confere a quantidade:** a quantidade de vértices lida da tabela bruta tem de ser igual à avisada; senão a curva falha com `INSUMO_INVALIDO` e a mensagem diz as duas quantidades.

Fica para outra change: agendamento (orquestrador), construção da data inteira, curvas derivadas e o formato de erro do engine.

## Capabilities

### New Capabilities
- `aviso-engine-cargas`: o aviso dos três alimentadores ao engine, com `verticesPorCurva` por fonte, repetição e conferência da quantidade.

### Modified Capabilities
<!-- Nenhuma em openspec/specs. Refina o requisito "Aviso ao engine depois do commit" (`processor-v0`) e o "Webhook de carga concluída" (`curve-load-trigger`, `engine-construcao-curvas`), que continuam valendo no que não é alterado aqui. -->

## Impact

- **processor:** `AvisoEngineService` e `AvisoEngineAdapter` (base comum) e a montagem de `verticesPorCurva` em cada provedor (`interpretar`).
- **engine:** a conferência da quantidade no tratamento da carga (`application/service/ProcessarCargaService.java:93-105`, pela inspeção).
- **orquestrador:** nada muda (recebe o 200 logo depois do commit).
- **Banco e front:** nenhuma mudança.

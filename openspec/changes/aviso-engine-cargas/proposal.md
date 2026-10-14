## Why

Depois que um alimentador (B3, ANBIMA ou Bloomberg) grava o dado bruto, o engine precisa saber, para construir as curvas daquela carga. Hoje isso está espalhado: o aviso do processor está na base comum da `processor-v0` (seção 8), o webhook do engine está na spec `curve-load-trigger` (`engine-construcao-curvas`), e cada fonte está com uma pessoa (B3 e ANBIMA com outros desenvolvedores, a Bloomberg na `processor-bloomberg`). Sem um contrato único dos três alimentadores, cada um pode contar os vértices de um jeito, repetir de outro e logar de outro.

A inspeção do engine também mostrou que o webhook recebe `verticesPorCurva` mas **não compara** a quantidade lida com a avisada (`linhasAvisadas` só é repassada), embora a spec do webhook diga que compara.

Esta change define **um contrato só, dos três alimentadores para o engine**, e fecha a conferência da quantidade no engine.

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

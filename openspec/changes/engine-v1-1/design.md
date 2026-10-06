## Context

O engine v1 já está aplicado (relatório `docs/inspecao/inspecao-engine.md` do poc). `ResultadoConstrucao` é um tipo selado com variantes (`Construida`, `Reconstruida`, `Existente`, `Ignorada`, `SemInsumo`, `Falhou`) serializadas sem discriminador; o controller devolve o resultado direto (`CurvaController.java:84-95`). `PreparacaoVertices.java:27-30` usa DU `0` quando `diasUteis` é nulo.

## Goals / Non-Goals

**Goals:** o mínimo que a tela Curvas precisa do engine e o descarte indevido de vértice.

**Non-Goals:** qualquer item da lista "Fica para a v2" da proposta; mudar os outros campos das respostas.

## Decisions

### D1. `situacao` como campo, sem mudar o resto
Cada variante do resultado expõe `situacao` (um método ou componente com o nome do resultado em maiúsculas com `_`). **Por quê:** o front só precisa saber qual resultado veio; renomear ou reorganizar os outros campos quebraria o que já funciona. **Alternativa rejeitada:** `@JsonTypeInfo` com outro nome de propriedade, que muda o contrato à toa.

### D2. Dias úteis pelo calendário quando faltam
Quando o eixo é `Business252` e o vértice não traz dias úteis, `PreparacaoVertices` calcula pelo calendário da curva (o mesmo que a construção usa) antes de checar prazo não positivo e repetido. Se a conferência mostrar que nenhum modelo produz vértice sem dias úteis com eixo `Business252`, a tarefa registra isso e não altera o código.

## Risks / Trade-offs

- [Cliente que já lê a resposta da construção] → só ganha um campo; nada sai.

> **Como executar com `/opsx-apply`** (o apply já leu `proposal.md`, `design.md` e a spec; não releia):
> 1. A v1 já está aplicada: abra só os arquivos citados na tarefa; não crie segunda versão do que existe.
> 2. Não pare para perguntar. Se faltar algo, deixe `// TODO(revisao): <dúvida>` e siga.
> 3. Verifique com `mvn -q compile` depois de alterar código e rode só os testes citados na tarefa, não a suíte.
> 4. Modelo forte: são poucas tarefas e de julgamento.

## 1. Situação na resposta da construção

- [x] 1.1 Em `domain/curva/ResultadoConstrucao.java`, expor `situacao` em todas as variantes (`CONSTRUIDA`, `RECONSTRUIDA`, `EXISTENTE`, `IGNORADA`, `SEM_INSUMO`, `FALHOU`), sem mudar os outros campos (design D1); conferir que a resposta de `CurvaController.java:84-95` passa a trazer o campo; escrever um teste do serializador com uma variante `Reconstruida` e uma `Existente` e fazê-lo passar

## 2. Dias úteis de vértice sem dias úteis informados

- [x] 2.1 Conferir em `domain/interpolacao/PreparacaoVertices.java:27-30` e nos modelos (`TaxaSwapB3`, `NtnbBootstrapAnbima`, `SofrZeroBloomberg`) se algum vértice chega sem dias úteis com eixo `Business252`; se chegar, calcular pelo calendário da curva antes das checagens de prazo (design D2) e escrever o teste do cenário da spec; se não chegar, registrar a prova no resumo e não alterar

## 3. Fechamento

- [x] 3.1 Escrever o resumo: o que foi alterado ou já estava certo em cada tarefa, com `arquivo:linha`

## Resultado no repositório real (2026-10-06)

- 1.1: `situacao` serializado nas seis variantes de `ResultadoConstrucao`, sem mudar os campos; teste com `Reconstruida` e `Existente` (`JsonConfigurationTest`).
- 2.1: sem alteração. B3 recusa DU nulo; NTN-B calcula o DU pelo calendário; SOFR usa `Actual360`.
- Verificado com `mvn -q compile` e o teste do item 1.1.

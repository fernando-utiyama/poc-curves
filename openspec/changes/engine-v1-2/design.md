## Context

O engine v1 (change `engine-construcao-curvas`) e a v1.1 já estão aplicados. A inspeção (`docs/inspecao/inspecao-engine.md`, seção 5.2 e a linha da NTN-B) mostrou que `NtnbBootstrapAnbima.java:113-116` faz `a.withDayOfMonth(15)` depois de `base + prazo`. A `services/curves` fechou a v1.2 com convenções de estrutura que valem para o engine também.

**Dado real (conferido em 13/10/2026):** os arquivos `ms{AAMMDD}.txt` da ANBIMA de 02, 05, 06, 07, 08, 09 e 13/10/2026 trazem, por pregão, 14 NTN-B com `Codigo SELIC` `760199` (título inteiro) e `Data Vencimento` sempre no dia 15 (de `2027-05-15` a `2060-08-15`). Nenhum `760198` (NTN-B Principal, desmembrado) em nenhum dos 7 dias; os outros títulos são LTN (`100000`), NTN-C (`770100`), LFT (`210100`) e NTN-F (`950199`). O processor grava `prazoDiasCorridos = vencimento − dataBase`, sem ajuste de dia útil.

## Goals / Non-Goals

**Goals:** (1) o vencimento nominal da NTN-B passa a ser exatamente `base + vVertcCurva`; (2) conferir a estrutura do engine contra as convenções da `curves` v1.2 e corrigir só o que diverge.

**Non-Goals:** a lista "Fica para a v2" da proposta; mudar contrato de rota, formato de erro ou schema; criar infra nova (Kafka, tabela, DLQ); mexer em `curves`, `processor` ou `fed`.

## Decisions

### D1. Vencimento nominal sem forçar o dia 15
`V = dataBase + vVertcCurva`. As datas nominais de evento são `V`, `V − 6 meses`, ... e cada pagamento é a data nominal ajustada por *Following* no calendário cadastrado. **Por quê:** com a carga real o resultado é o mesmo, mas o `withDayOfMonth(15)` corrige em silêncio um prazo errado (ou um título com vencimento em outro dia) em vez de deixar o erro aparecer, e a spec do modelo manda usar o vencimento nominal exato. **Alternativa rejeitada:** manter o dia 15 e validar que o prazo cai nele: recusaria título legítimo de outro dia e repete a regra da ANBIMA dentro do modelo.

### D2. Estrutura por conferência, não por reescrita
A estrutura é conferida arquivo a arquivo (spec `convencoes-engine`); cada item fica **manter**, **alterar** ou **criar**. **Por quê:** o engine já está aplicado e funcionando; reescrever o que já cumpre a regra só cria risco. **Alternativa rejeitada:** refatoração geral por pacote.

### D3. Sem teste de suíte nas conferências
As tarefas de conferência são por leitura, com prova `arquivo:linha`; compila-se (`mvn -q compile`) só depois de alterar código, e roda-se só o teste citado na tarefa.

### D4. Lista de excluídos do processor fica
Mesmo sem nenhum `760198` nos 7 pregões vistos, a exclusão no processor continua: custa nada e protege a curva se a ANBIMA passar a publicar o desmembrado. O engine não filtra por código SELIC (não recebe o código).

## Risks / Trade-offs

- [Um prazo gravado que não cai em dia 15 passa a gerar uma curva diferente da de hoje] → é o comportamento da spec; o teste novo cobre.
- [Conferência por leitura pode deixar passar algo que só aparece rodando] → aceitável: cada correção é pequena e compilada; vetores reais ficam para a v2.
- [A SOFR sem validação dos 21 tenores continua] → registrada como v2; uma carga incompleta da Bloomberg gera curva com menos tenores sem aviso.

## Open Questions

1. Os 21 tenores da SOFR devem virar validação já na v1.2? (Hoje: v2.)

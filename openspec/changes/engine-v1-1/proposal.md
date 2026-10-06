## Why

O engine v1 (change `engine-construcao-curvas`, já aplicada) constrói e serve as curvas, mas a inspeção (`docs/inspecao/inspecao-engine.md` do poc) mostrou: a resposta da construção não diz se a curva foi construída, recalculada ou já existia, que é o que a tela Curvas do `fed` mostra; e um caso em que um vértice sem dias úteis informados pode ser descartado. Esta v1.1 trata só isso.

## What Changes

- **`situacao` na resposta da construção** (`CONSTRUIDA`, `RECONSTRUIDA`, `EXISTENTE`, e os demais resultados que já existem), para a tela Curvas.
- **Vértice sem dias úteis informados:** com eixo em dias úteis, os dias úteis passam a ser calculados pelo calendário, em vez de virar 0 e descartar o vértice (conferir se ocorre; corrigir se ocorrer).

Fica para a v2 (não entra aqui): NTN-B sem o `withDayOfMonth(15)` (com o prazo exato do arquivo não muda o resultado), `cDiaUtil` com o DU calculado, `X-Usuario`, rota de pontos interpolados, `quantidadePontos` e `avisos` em todos os resultados, regravação sem vértices, `INSUMO_INCOMPLETO`, formato de erro, frequências recusadas, teste de vetores reais, `DATA_INTEIRA`, situação da data completa, `LogLinear`, logs de falha, isolamento explícito, 21 tenores da SOFR, renomes e segurança.

## Capabilities

### New Capabilities
- `ajustes-engine-v1-1`: `situacao` na resposta da construção e os dias úteis de vértice sem dias úteis informados.

### Modified Capabilities
<!-- Nenhuma em openspec/specs (não há specs arquivadas). -->

## Impact

- **engine:** `ResultadoConstrucao` e a serialização da resposta da construção; `PreparacaoVertices`.
- **curves e fed:** a curves repassa a resposta como vem (change `curves-v1-1`); o `fed` lê `situacao` (change `fed-curvas-mercado`).
- **Banco:** nenhuma mudança de schema.

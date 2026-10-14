## Why

O engine v1 e a v1.1 já estão aplicados e as 7 curvas têm modelo nativo. A inspeção do engine (`docs/inspecao/inspecao-engine.md` do poc) deixou uma divergência de resultado no modelo da NTN-B, e a v1.2 da `curves` consolidou convenções de estrutura que o engine ainda não conferiu. Esta v1.2 trata só isso:

- **NTN-B:** o bootstrap reconstrói o vencimento como `base + vVertcCurva` e depois força o dia 15 (`withDayOfMonth(15)`), em vez de usar o vencimento nominal exatamente em `base + vVertcCurva` e ajustar só a data de pagamento por *Following*. Com a carga real da ANBIMA (todas as NTN-B vencem no dia 15) o número não muda, mas o código esconde um prazo errado em vez de mostrá-lo, e deixa de valer assim que entrar outro tipo de título (a CurvaZero, `CZ`, está planejada).
- **Estrutura:** conferir o engine contra o que a `curves` v1.2 estabeleceu (hexagonal de verdade, controller chamando use case, tipos de domínio fortes, sem record dentro de interface, comentários curtos, Jackson 3, tempo sem constante de fuso, Swagger completo e testes que testam regra).

## What Changes

- **`NtnbBootstrapAnbima`:** o vencimento nominal do título é `dataBase + vVertcCurva`, sem forçar o dia do mês. As datas nominais de evento são `V`, `V − 6 meses`, `V − 12 meses`...; cada pagamento é a data nominal ajustada por *Following* no calendário cadastrado. Teste com o vencimento fora do dia 15 e com vencimento em dia não útil.
- **Conferência de estrutura (leitura, sem teste de suíte):** cada item da spec `convencoes-engine` é conferido nos arquivos citados na tarefa, com `arquivo:linha`, e fica como **manter**, **alterar** ou **criar**. Só o que diverge é corrigido.
- **Dado real conferido:** nos arquivos `ms` de 02/10 a 13/10/2026 (7 pregões) todas as NTN-B vêm com `Codigo SELIC` `760199` (título inteiro) e vencimento no dia 15; **nenhum desmembrado** (`760198`) apareceu. A lista de excluídos do processor continua como proteção e não muda.

Fica para a v2 do engine (não entra aqui): `cDiaUtil` com o DU calculado, `X-Usuario`, rota de pontos interpolados, regravação sem vértices, `INSUMO_INCOMPLETO`, formato de erro, frequências recusadas, teste de vetores reais (278 DU em `2026-09-14`), `DATA_INTEIRA`, situação da data completa, logs de falha, isolamento explícito, **21 tenores da SOFR**, renomes (`TAXA_SWAP_B3` para `PRONTA_TS_B3`) e segurança.

## Capabilities

### New Capabilities
- `ntnb-vencimento-exato`: vencimento nominal da NTN-B em `base + vVertcCurva`, sem forçar o dia, com pagamento por *Following*.
- `convencoes-engine`: as convenções de estrutura e de código que o engine confere, vindas da `curves` v1.2.

### Modified Capabilities
<!-- Nenhuma em openspec/specs (não há specs arquivadas). O requisito da NTN-B vem de `ntnb-anbima-curve-model` (change `engine-construcao-curvas`) e é refinado aqui. -->

## Impact

- **engine:** `domain/construcao/NtnbBootstrapAnbima.java` e o teste do modelo; a conferência toca (só se divergir) controllers, services e portas listados nas tarefas.
- **curves, processor e fed:** nenhuma mudança. O processor já grava `prazoDiasCorridos` exato.
- **Banco:** nenhuma mudança de schema.
- **Resultado numérico:** igual ao de hoje para a carga real da ANBIMA; muda só quando o prazo gravado não cai num dia 15 (passa a ser respeitado, não corrigido).

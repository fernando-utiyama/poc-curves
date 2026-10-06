## Why

A change `curves-cadastro-curvas` ficou grande demais para uma entrega e foi dividida em 2026-09-30. A primeira parte, que fica lá, entrega os CRUDs de que o engine e o processor precisam: curva de mercado, provedores da curva, configuração de cálculo e bruto da B3. Esta segunda parte entrega o que o gestor usa no dia a dia depois do cadastro pronto: a edição em lote por planilha, a edição manual dos vértices da curva construída (e a planilha de vértices), o painel de acompanhamento e as origens secundárias. O motivo, as decisões e o guia de implementação são os da change `curves-cadastro-curvas` (`design.md` e `implementacao.md`).

## What Changes

- **Planilha do cadastro:** exportar curvas, provedores da curva e configurações num `.xlsx`, editar e importar de volta, com simulação e aplicação atômica.
- **Edição manual dos vértices** (`tDadoVertcCurva`), com preferência sobre o engine, recusa só por consistência de banco, avisos de regra de negócio, `hashPontos` e regravação da interpolada pedida ao engine.
- **Planilha de vértices** com simulação e aplicação atômica.
- **Painel de acompanhamento** por data-base, com a situação de cada curva, atraso (só para data-base passada) e o que precisa de atenção.
- **Origens secundárias:** `MODELOS_POR_ORIGEM` na configuração, na planilha e no painel, e `diasUteis` opcional nos vértices.

## Capabilities

### New Capabilities
- `cadastro-curvas-planilha`: exportação e importação em lote por planilha, com simulação e aplicação atômica.
- `painel-curvas`: painel de acompanhamento das curvas por data-base.
- `vertices-curva-manual`: edição manual dos vértices de uma curva numa data-base.
- `vertices-curva-planilha`: exportação e importação em lote dos vértices por planilha.

### Modified Capabilities
- `configuracao-calculo-curva` (criada pela change `curves-cadastro-curvas`): modelo de construção por origem secundária.

## Impact

- **services/curves:** rotas de planilha, painel e vértices; dependência nova `poi-ooxml` se ainda não entrou pela auditoria em `xlsx`.
- **Banco:** escreve `tDadoVertcCurva` (edição manual) e apaga `tDadoCurva` da data quando os vértices da data são apagados; sem mudança de schema.
- **Engine:** usa `GET /api/v1/curvas/situacao`, `POST .../interpolada` e os feriados em JSON, todos da change `engine-construcao-curvas`.
- **Depende de:** change `curves-cadastro-curvas` (cadastro pronto) e change `engine-construcao-curvas`.

# Backlog da v2 (fora da v1.1)

Itens apontados nas inspeções de 2026-10-06 (`docs/inspecao/`) que ficaram fora da v1.1 (`engine-v1-1`, `curves-v1-1`, `fed-curvas-mercado`, `fed-dados-mercado`).

Onde estão: engine → `engine-modelos-curva`, tarefas 18.x (com a spec nova `pontos-interpolados-engine`); curves → `curves-operacao-curvas`, tarefas 12.x. Os itens de front e de segurança ainda não estão em nenhuma change.

## Engine

- `cDiaUtil` com o DU calculado, não só o publicado (SOFR fica nula).
- Ler o `X-Usuario` opcional; tirar `"OPERADOR"`/`SISTEMA` fixos.
- Rota `GET /curvas/{codigo}/{dataBase}/pontos` (lê `tDadoCurva`) para a aba de pontos interpolados.
- Regravar a interpolada sem vértices: apagar de fato (hoje o rollback desfaz) e responder 404.
- Carga: comparar a quantidade lida com `verticesPorCodigo` → `INSUMO_INCOMPLETO`.
- Formato de erro com `correlationId` e `detalhes` sempre.
- Recusar e tirar do catálogo `NoFrequency`, `Once`, `OtherFrequency`; tipo errado → `CADASTRO_INVALIDO`, não falha de cast.
- Teste de vetores reais de verdade (`TaxaSwap.txt` de 2026-09-14: 5 hashes calculados, 278 DU, oráculo, 12.390 linhas).
- `DisparoConstrucao` = `DATA_INTEIRA` na construção da data.
- Situação da data completa (insumo, fonte, interpolada desatualizada, tempo esgotado).
- Logs `CONSTRUCAO_FALHOU` e `INSUMO_DESCARTADO`.
- Isolamento `READ_COMMITTED` explícito.
- SOFR: exigir os 21 tenores (decisão).
- Renomes (`TaxaSwapB3` → `ProntaTsB3`, `fatorAcum` → `fatorAcumulado`, `pontos` → `vertices`).
- Construção de derivadas depois dos provedores.

## Curves

- Rota `/dados-mercado/{provedor}` unificada (tickers, consulta por código na fonte) sobre os CRUDs `primaria-*`.
- Formato de erro com `correlationId` no lugar de `errorId`; `detalhes` sempre.
- Decimais da ANBIMA e da Bloomberg como texto.
- Instantes com `-03:00`.
- `avisos` sempre presente (provedores da curva, brutos).
- `PUT` do provedor da curva que troca o provedor → 422; `CURVA_COM_FILHAS` na inativação.
- Configuração: correção retroativa (decisão).
- `/valores`: rótulo e descrição dos parâmetros; enums no Swagger.
- Repasse de pontos interpolados (depende da rota do engine).
- `X-Usuario` repassado ao engine.

## Front

- Aba "Pontos" da tela Curvas.
- Tela Dados de mercado sobre `/dados-mercado` com filtro por ticker da fonte.
- Planilhas (parte 2: `curves-operacao-curvas` e `engine-modelos-curva`).

## Transversal

- Segurança (autenticação e papéis).

## Curves: dias úteis sem dado na listagem do bruto

Na listagem `primaria-*` com período, mostrar também os dias úteis em que a curva não tem dado (`quantidadeVertices` = 0), para os buracos aparecerem na tela. A curves não tem calendário: precisa chamar o engine (`GET /api/v1/calendarios/{nome}?mercado=&anoInicial=&anoFinal=&formato=json`, spec `calendar-management`), com um método `feriados(...)` novo no `EnginePort`/`EngineHttpClient`, e ler o JSON para um record. Partir das curvas ligadas ao provedor em `tCurvaPrvdr` e do calendário da configuração vigente de cada uma. Levantado em 09/10/2026 e adiado.

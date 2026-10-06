## Why

O gestor não tem uma tela para ver e corrigir o dado bruto que alimenta as curvas (os vértices de `tBtrsCurvaPrimr`, `tAnbmaCurvaPrimr` e `tBbergCurvaPrimr`), nem para enviar à mão o arquivo de uma fonte quando o download falha. A `services/curves` já tem os CRUDs desse bruto para os três provedores (rotas `/curvas-mercado/.../primaria-*`), e o upload do arquivo da fonte já existe no processor (change `processor-v0`), mas nada disso está numa tela do `fed`.

## What Changes

- **Tela "Dados de mercado"** no `fed` (`/dados-mercado`, no menu): escolhe o provedor (B3, ANBIMA, Bloomberg), lista as curvas e datas-base que têm bruto daquele provedor e abre os vértices de uma curva numa data.
- **Manutenção dos vértices brutos** nessa tela, pelos CRUDs que já existem na curves: incluir, alterar e excluir um vértice, e excluir todos os vértices da curva na data, com os campos de cada fonte.
- **Envio do arquivo da fonte** na mesma tela (TaxaSwap ou `.ex_`, `ms{AAMMDD}.txt`, resposta do Data License), escolhendo o provedor e o arquivo, sem data: a data-base vem do conteúdo. O envio passa pelo bff (`POST /api/v1/cargas/upload`).
- **`fed`:** o `request.interceptor` deixa de forçar `Content-Type: application/json` quando o corpo é um arquivo; o upload usa tempo limite próprio; o proxy ganha o destino do bff para `/api/v1/cargas`.

Fica para a v2: a rota unificada `/dados-mercado/{provedor}` com filtro por ticker da fonte, e a planilha dos vértices brutos (parte 2 da curves).

## Capabilities

### New Capabilities
- `fed-dados-mercado`: tela do `fed` para listar o bruto por provedor, manter os vértices de uma curva numa data e enviar o arquivo da fonte.

### Modified Capabilities
<!-- Nenhuma em openspec/specs (não há specs arquivadas). -->

## Impact

- **web/fed:** tela e serviço novos (`dados-mercado`), item no cabeçalho, `request.interceptor` (corpo de arquivo e tempo limite por requisição, o mesmo `TEMPO_LIMITE_MS` da change `fed-curvas-mercado`), `proxy.conf.js` com o destino do bff.
- **services/curves:** nenhuma mudança; usa os CRUDs `primaria-b3`, `primaria-anbima` e `primaria-bloomberg` como estão.
- **bff:** usa a rota `POST /api/v1/cargas/upload` da change `processor-v0` (capability `upload-carga-bff`), sem mudança.
- **Processor e engine:** sem mudança; o engine continua lendo o bruto só na construção (a correção vale num recálculo).

## Why

O gestor não tem uma tela para ver e corrigir o dado bruto que alimenta as curvas (os vértices de `tBtrsCurvaPrimr`, `tAnbmaCurvaPrimr` e `tBbergCurvaPrimr`), nem para enviar à mão o arquivo de uma fonte quando o download falha. A `services/curves` só especifica a manutenção do bruto da B3, linha a linha e por curva; ANBIMA e Bloomberg não têm manutenção, e não há importação em lote. O upload do arquivo da fonte já existe no processor (change `processor-v0`), mas não está em nenhuma tela do `fed`.

## What Changes

- **Tela "Dados de mercado"** no `fed` (`/dados-mercado`, no menu), com filtros de provedor (B3, ANBIMA, Bloomberg), ticker (o código na fonte, que depende do provedor) e data de referência, e a tabela dos vértices brutos de cada curva ligada àquele código.
- **CRUD de vértices brutos** nessa tela, para os três provedores: incluir, alterar e excluir um vértice, e excluir todos os vértices de uma curva na data, com os campos próprios de cada fonte.
- **Importação por planilha `.xlsx`** dos vértices brutos de uma curva numa data, com exportação do modelo preenchido, simulação e aplicação de uma vez.
- **Envio do arquivo da fonte** na mesma tela (TaxaSwap ou `.ex_`, `ms{AAMMDD}.txt`, resposta do Data License), escolhendo o provedor e o arquivo, sem data: a data-base vem do conteúdo, como já define o processor. O envio passa pelo bff (`POST /api/v1/cargas/upload`).
- **`services/curves` ganha a manutenção do bruto dos três provedores**, por provedor, código na fonte e data, com validação que só recusa o que não pode ser gravado e avisos de regra de negócio, e com a planilha.
- **Backend:** as rotas `/dados-mercado/{provedor}` ficam na change `curves-cadastro-curvas` (spec `vertices-brutos-provedor`), sobre os CRUDs dos três provedores que já existem.
- **`fed`:** o `request.interceptor` deixa de forçar `Content-Type: application/json` quando o corpo é um arquivo; o upload e a planilha usam tempo limite próprio; o proxy ganha o destino do bff para `/api/v1/cargas`.

## Capabilities

### New Capabilities
- `fed-dados-mercado`: tela do `fed` para filtrar os vértices brutos por provedor, ticker e data, mantê-los, importá-los por planilha e enviar o arquivo da fonte.

### Modified Capabilities
<!-- Nenhuma em openspec/specs (não há specs arquivadas). -->

## Impact

- **web/fed:** tela e serviço novos (`dados-mercado`), item no cabeçalho, `request.interceptor` (corpo de arquivo e tempo limite por requisição, o mesmo `TEMPO_LIMITE_MS` da change `fed-curvas-mercado`), `proxy.conf.js` com o destino do bff.
- **services/curves:** controller, serviço e adaptadores de persistência das três tabelas brutas (o `BtrsCurvaPrimr`, o `AnbmaCurvaPrimr` e o `BbergCurvaPrimr` já existem como entidades na curves), planilha com Apache POI (já usado na auditoria).
- **bff:** usa a rota `POST /api/v1/cargas/upload` da change `processor-v0` (capability `upload-carga-bff`), sem mudança.
- **Processor e engine:** sem mudança; o engine continua lendo o bruto só na construção (a correção vale num recálculo).
- **Banco:** nenhuma mudança de schema.

## Why

As APIs de curva já existem hoje — cadastro, consulta e curva interpolada — mas sem contrato escrito e sem responder às perguntas que o versionamento e a procedência tornaram possíveis: *qual curva estava publicada às 15h de ontem?*, *que insumos geraram este vértice?*, *como a curva que a casa construiu difere da que a B3 divulgou?*

Além disso, a definição de curva ganhou responsabilidades novas — modo de origem (`BOOTSTRAPPED` ou `IMPORTED`), modelo de construção apontado, dependência entre curvas — e o cadastro precisa expor isso de forma que o operador consiga configurar sem tocar em código.

Esta mudança especifica o `curve-api` como a superfície de domínio da plataforma: cadastro versionado de definição de curva e consulta de tudo o que foi publicado.

## What Changes

- **Cadastro versionado de curva**: criar e editar definição gera nova versão da definição em vez de alterar a vigente, preservando a explicação das curvas já publicadas. Campos: código, nome, moeda, modo de origem, convenções, contagem de dias, calendário, interpolador, política de extrapolação, política de arredondamento, modelo de construção apontado, vínculos de fonte e dependências.
- **Validação de coerência no cadastro**: modo de origem compatível com os vínculos de fonte declarados; interpolador e política existentes; dependências sem ciclo; modelo existente e habilitado.
- **Consulta de curva publicada** em três formas: versão corrente, versão explícita e `asOf` por instante.
- **Consulta de vértices** com paginação, e valores serializados como texto numérico para não perder dígito.
- **Curva interpolada** exposta pela API, delegando ao motor a interpolação síncrona — o `curve-api` não recalcula nada.
- **Histórico de versões** de uma curva e data, mostrando o que foi publicado, quando, por qual execução e com qual modelo.
- **Procedência** de uma versão publicada: execução, versão da definição, insumos, hash do conjunto, modelo usado e checksum quando Groovy; para curva importada, o lote de ingestão e o arquivo de origem.
- **Comparação de curvas**: mesma data, duas curvas — tipicamente a construída contra a importada da B3 — reportando a diferença prazo a prazo e sinalizando prazos presentes em apenas uma.
- **Contrato OpenAPI versionado** em `contracts/openapi`, fonte de verdade compartilhada com o BFF.

## Capabilities

### New Capabilities

- `curve-catalog-api`: cadastro e edição versionada da definição de curva, incluindo modo de origem, convenções, modelo apontado, vínculos de fonte e dependências; validações de coerência; ciclo de vida da definição; e consulta do catálogo.
- `curve-query-api`: consulta de curva publicada por versão corrente, versão explícita ou `asOf`; listagem de vértices; curva interpolada delegada ao motor; histórico de versões; procedência; comparação entre curvas; paginação, filtros e precisão de serialização.

### Modified Capabilities

<!-- Nenhuma. -->

## Impact

- **Novo serviço**: `services/curve-api/` em Java 21 / Spring Boot 3.4.x, sem Lombok.
- **Depende de** `curves-solution-architecture` (modelo de dados) e do `curve-engine` para interpolação.
- **Escreve** apenas em `definicao_curva` e `versao_definicao_curva`. Todo o resto é somente leitura.
- **Lê** `versao_curva`, `vertice_curva`, `procedencia_curva`, `modelo_curva`, `lote_ingestao` e `execucao_curva`.
- **Expõe** API HTTP interna consumida pelo `curve-bff`; não é exposta ao navegador.
- **Contrato**: OpenAPI versionado em `contracts/openapi/curve-api.yaml`, validado em teste contra a implementação.
- **Precisão**: taxa, fator e cotação são serializados como texto numérico, nunca como número de ponto flutuante em JSON.
- **Fora de escopo**: construir curva, publicar vértices, interpolar por conta própria, agendar ou ingerir.

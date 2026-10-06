# Guia de implementação: fed-dados-mercado

A spec manda no comportamento; este guia dá o contrato e o código. Tudo o que a integração precisa está aqui: **não abra outras changes, outros serviços nem o Swagger**. Se faltar algo, deixe `// TODO(revisao): <dúvida>` e siga.

## 0. O que abrir

| Tarefa | Abrir só | Criar |
|---|---|---|
| 2.x | `core/interceptors/request.interceptor.ts`, `proxy.conf.js`, `core/services/provedores/provedores.service.ts` (molde), `app.routes.ts`, `components/header/header.component.ts` | §3 |

O `TEMPO_LIMITE_MS` do `request.interceptor` vem da change `fed-curvas-mercado` (guia dela, §2.1); se ainda não existir, crie-o como está lá.

## 1. Contrato

Base da curves: `{urlAPI}/api/v1/dados-mercado` (proxy `/api`). Upload: `{urlAPI}/api/v1/cargas/upload` (proxy `/api/v1/cargas` → bff).

| Ação | Chamada | Tempo front |
|---|---|---|
| Tickers | `GET /dados-mercado/{provedor}/tickers` | 3 s |
| Consultar | `GET /dados-mercado/{provedor}?codigoNaFonte=PRE&dataBase=2026-09-14` | 3 s |
| Incluir / alterar / excluir vértice | `POST /dados-mercado/{provedor}/{codigo}/{dataBase}/vertices`, `PUT .../vertices/{id}`, `DELETE .../vertices/{id}` | 3 s |
| Excluir todos da data | `DELETE /dados-mercado/{provedor}/{codigo}/{dataBase}` | 3 s |
| Enviar arquivo da fonte | `POST /api/v1/cargas/upload` (`FormData`: `fonte`, `arquivo`) | 130 s |

`{provedor}` = `B3`, `ANBIMA` ou `BLOOMBERG`. Campos do vértice por provedor (decimais sempre string):

| Provedor | Corpo do POST/PUT | Só leitura na resposta |
|---|---|---|
| `B3` | `{ "diasCorridos": 112, "diasUteis": 75, "valor": "13.589", "fatorAcumulado": null, "fatorDia": null }` | `id`, `dataVertice` |
| `ANBIMA` | `{ "prazoDiasCorridos": 229, "taxa": "5.5415" }` | `id`, `vencimento` |
| `BLOOMBERG` | `{ "ticker": "S0490Z 15M BLC2 Curncy", "valor": "4.31" }` | `id` |

```jsonc
// GET /dados-mercado/B3/tickers
[ { "codigoNaFonte": "PRE", "produto": "TS", "curvas": [ { "codigo": "PRE", "nome": "DIxPRE" } ] } ]
// GET /dados-mercado/B3?codigoNaFonte=PRE&dataBase=2026-09-14
{ "provedor": "B3", "codigoNaFonte": "PRE", "dataBase": "2026-09-14",
  "curvas": [ { "codigo": "PRE", "nome": "DIxPRE", "curvaConstruida": true,
                "avisos": [ { "codigo": "CURVA_JA_CONSTRUIDA", "mensagem": "...", "detalhes": [] } ],
                "vertices": [ { "id": 101, "dataVertice": "2026-09-15", "diasCorridos": 1, "diasUteis": 1,
                                "valor": "13.900000000000", "fatorAcumulado": null, "fatorDia": null } ] } ] }
// POST/PUT vértice (201/200): o vértice gravado + "avisos" da curva na data
// upload (200): { "idCarga": "B3-TS-20260914-46a249c60bec", "dataBase": "2026-09-14", "origem": "UPLOAD",
//                 "verticesPorCodigo": { "PRE": 278 }, "usuario": "maria", "correlationId": "..." }
// erro: curves no formato de erro dela (o do CRUD de provedores), com detalhes [ { campo, linha, valor, motivo } ];
//       upload pelo bff no formato do processor { codigoErro, mensagem, correlationId }. O front lê os dois pelo lerErro.
```

## 2. services/curves

Implementado na change `curves-cadastro-curvas` (spec `vertices-brutos-provedor`, guia §11). Aqui só o contrato da §1.

## 3. web/fed

### 3.1 Interceptor e proxy

- `request.interceptor`: sem `Content-Type` quando `req.body instanceof FormData` (o código completo está no guia da `fed-curvas-mercado`, §2.1).
- `proxy.conf.js`: acrescentar **antes** de `'/api'`:

```js
const BFF_TARGET = process.env.PROXY_BFF_TARGET || 'https://curve-bff.poc.local';
// ...
  '/api/v1/cargas': { target: BFF_TARGET, logLevel: 'debug', secure: false, changeOrigin: true },
```

### 3.2 Serviço (`core/services/dados-mercado/dados-mercado.service.ts`)

Molde do `ProvedoresService`. Métodos: `tickers(provedor)`, `consultar(provedor, codigoNaFonte, dataBase)`, `incluir(provedor, codigo, dataBase, campos)`, `alterar(..., id, campos)`, `excluir(..., id)`, `excluirTodos(provedor, codigo, dataBase)`, `enviarArquivo(fonte, arquivo: File)` (`FormData` com `fonte` e `arquivo`, `TEMPO_LIMITE_MS` 130000).

### 3.3 Tela (`components/dados-mercado/`)

- Topo: provedor (`B3`, `ANBIMA`, `Bloomberg`), ticker (recarrega a cada troca de provedor, limpa a tabela), data (`dd/mm/aaaa`, padrão hoje), "Consultar".
- Uma tabela do Liquid por curva da resposta (`#table-vertices-{codigo}`), colunas por provedor: B3 Data do vértice, Dias corridos, Dias úteis, Valor; ANBIMA Vencimento, Prazo (dias corridos), Taxa; Bloomberg Ticker, Valor. Botões Editar e Excluir por linha (como em `curva-detalhe`), e acima: Incluir vértice e Excluir todos da data.
- Avisos acima de cada tabela; com `CURVA_JA_CONSTRUIDA`, o link "Recalcular a curva" → `/curvas?codigo={codigo}`.
- Bloco "Enviar arquivo da fonte": provedor + arquivo + aviso de substituição + "Enviar"; no sucesso, mostrar data-base, `idCarga` e `verticesPorCodigo`, e trocar os filtros para o provedor e a data devolvidos.
- Erros sempre na tela: `error: (e) => this.erro.set(lerErro(e))`, com o helper `core/error/ler-erro.ts` do guia da `fed-curvas-mercado` (§2.3; crie-o como está lá se ainda não existir); `detalhes` do 422 vão para os campos do modal.
- Decimais: exibir `valor.replace('.', ',')`; ao enviar, `texto.replace(',', '.')`.
- Rota `dados-mercado` em `app.routes.ts`; item "Dados de mercado" em `subMenuItems` e `'dados-mercado': 'Dados de mercado'` em `BREADCRUMB_LABELS`.

## 4. Testes (poucos, sem subir o Spring)

| Classe | Onde | Cobre |
|---|---|---|
| `dados-mercado.service.spec.ts` | fed | `HttpTestingController`: URLs, `FormData` sem `Content-Type` JSON, `TEMPO_LIMITE_MS` |

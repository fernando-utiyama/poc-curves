# Guia de implementação: fed-dados-mercado

A spec manda no comportamento; este guia dá o contrato e o código. Não abra outras changes. Dos serviços, abra só os DTOs do bruto da curves citados em §1 (para os campos da ANBIMA e da Bloomberg). Se faltar algo, deixe `// TODO(revisao): <dúvida>` e siga.

## 0. O que abrir

| Tarefa | Abrir só |
|---|---|
| 1.x e 2.x | `core/interceptors/request.interceptor.ts`, `proxy.conf.js`, `core/services/provedores/provedores.service.ts` (molde), `app.routes.ts`, `components/header/header.component.ts` |
| campos da ANBIMA e da Bloomberg | na curves, `AnbmaCurvaPrimr*Request`/`*Response` e `BbergCurvaPrimr*Request`/`*Response` (ou o Swagger da curves) |

O `TEMPO_LIMITE_MS` e o `lerErro` vêm da change `fed-curvas-mercado` (guia dela, §2.1 e §2.3); se ainda não existirem, crie-os como estão lá.

## 1. Contrato (CRUDs que já existem na curves)

Base: `{urlAPI}/api/v1/curvas-mercado` (proxy `/api`). `{p}` = `b3`, `anbima` ou `bloomberg`. Upload: `{urlAPI}/api/v1/cargas/upload` (proxy `/api/v1/cargas` → bff).

| Ação | Chamada | Tempo front |
|---|---|---|
| Listagem geral | `GET /primaria-{p}?de=&ate=&codigo=&nome=&pagina=&tamanho=` | 3 s |
| Vértices da curva na data | `GET /{codigo}/primaria-{p}/{dataBase}` | 3 s |
| Incluir / alterar / excluir vértice | `POST /{codigo}/primaria-{p}/{dataBase}/vertices`, `PUT .../vertices/{id}`, `DELETE .../vertices/{id}` | 3 s |
| Excluir todos da data | `DELETE /{codigo}/primaria-{p}/{dataBase}` | 3 s |
| Enviar arquivo da fonte | `POST /api/v1/cargas/upload` (`FormData`: `fonte` = `B3`/`ANBIMA`/`BLOOMBERG`, `arquivo`) | 130 s |

Referência: a B3 (os nomes exatos, inclusive o da lista de vértices e o envelope da página, são os dos DTOs do repositório; confira antes de codificar):

```jsonc
// GET /primaria-b3 → página do Spring com itens:
{ "codigo": "PRE", "nome": "DIxPRE", "situacao": "ATIVO", "dataBase": "2026-09-14",
  "quantidadeLinhas": 278, "codigosNaFonte": ["PRE"], "curvaConstruida": true }
// GET /PRE/primaria-b3/2026-09-14
{ "curvaConstruida": true, "avisos": [ { "codigo": "CURVA_JA_CONSTRUIDA", "mensagem": "...", "detalhes": [] } ],
  "linhas": [ { "id": 101, "diasCorridos": 1, "diasUteis": 1, "dataPonto": "2026-09-15",
                "valor": "13.900000000000", "fatorAcumulado": null, "fatorDia": null } ] }
// POST/PUT corpo
{ "diasCorridos": 112, "diasUteis": 75, "valor": 13.589, "fatorAcumulado": null, "fatorDia": null }
// POST/PUT resposta: { "linha": { ...vértice gravado... }, "avisos": [...] }
// DELETE resposta: { "avisos": [...] }
// upload (200): { "idCarga": "B3-TS-20260914-46a249c60bec", "dataBase": "2026-09-14", "origem": "UPLOAD",
//                 "verticesPorCodigo": { "PRE": 278 }, "usuario": null, "correlationId": "..." }
```

ANBIMA e Bloomberg têm a mesma forma de rota e de envelope, com os campos próprios (ANBIMA: prazo em dias corridos e taxa; Bloomberg: ticker e valor), números no JSON em vez de texto. Leia os nomes nos DTOs citados em §0.

## 2. web/fed

### 2.1 Interceptor e proxy

- `request.interceptor`: sem `Content-Type` quando `req.body instanceof FormData` (código completo no guia da `fed-curvas-mercado`, §2.1).
- `proxy.conf.js`: acrescentar **antes** de `'/api'`:

```js
const BFF_TARGET = process.env.PROXY_BFF_TARGET || 'https://curve-bff.poc.local';
// ...
  '/api/v1/cargas': { target: BFF_TARGET, logLevel: 'debug', secure: false, changeOrigin: true },
```

### 2.2 Serviço (`core/services/dados-mercado/dados-mercado.service.ts`)

Molde do `ProvedoresService`. `type Provedor = 'b3' | 'anbima' | 'bloomberg'`. Métodos: `listar(p, filtro)`, `vertices(p, codigo, dataBase)`, `incluir(p, codigo, dataBase, campos)`, `alterar(p, codigo, dataBase, id, campos)`, `excluir(p, codigo, dataBase, id)`, `excluirData(p, codigo, dataBase)`, `enviarArquivo(fonte, arquivo: File)` (`FormData`, `TEMPO_LIMITE_MS` 130000). Uma tabela de colunas e campos por provedor (`COLUNAS[p]`, `CAMPOS[p]`), com os nomes dos DTOs.

### 2.3 Tela (`components/dados-mercado/`)

- Topo: provedor, período (`dd/mm/aaaa`, padrão últimos 30 dias), curva (texto), "Consultar". Trocar de provedor limpa tudo.
- Listagem geral numa tabela do Liquid (como `provedores-lista`): código, nome, data-base, quantidade, códigos na fonte, "Construída"/"Não construída"; paginação de 50. Clique na linha abre os vértices abaixo.
- Vértices: tabela com as colunas do provedor; Editar e Excluir por linha; acima, "Incluir vértice" e "Excluir todos da data"; avisos acima da tabela; com `curvaConstruida`, o lembrete e o link "Recalcular a curva" → `/curvas?codigo={codigo}`.
- Bloco "Enviar arquivo da fonte": provedor + arquivo + aviso de substituição + "Enviar"; no sucesso, data-base, `idCarga` e `verticesPorCodigo`, e filtros no provedor com o período até a data devolvida.
- Erros sempre na tela: `error: (e) => this.erro.set(lerErro(e))`; `detalhes` vão para os campos do modal.
- Decimais: exibir `String(valor).replace('.', ',')`; ao enviar, `texto.replace(',', '.')`, sem conta.
- Rota `dados-mercado` em `app.routes.ts`; item "Dados de mercado" em `subMenuItems` e `'dados-mercado': 'Dados de mercado'` em `BREADCRUMB_LABELS`.

## 3. Testes (poucos)

| Classe | Cobre |
|---|---|
| `dados-mercado.service.spec.ts` | `HttpTestingController`: caminho por provedor, `FormData` sem `Content-Type` JSON, `TEMPO_LIMITE_MS` do upload |

# Guia de implementação: fed-curvas-mercado

A spec manda no comportamento; este guia dá o contrato e o código. Tudo o que a integração precisa está aqui: **não abra outras changes, outros serviços nem o Swagger**. Se faltar algo, deixe `// TODO(revisao): <dúvida>` e siga.

## 0. O que abrir

| Tarefa | Abrir só | Criar |
|---|---|---|
| 3.1 (fed) | `core/interceptors/request.interceptor.ts` | — |
| 3.2 a 3.7 (fed) | `core/services/provedores/provedores.service.ts` (molde), `app.routes.ts`, `components/header/header.component.ts` e os três componentes de curva | §2 |

## 1. Contrato ponta a ponta

Front → curves (`{urlAPI}/api/v1/curvas-mercado/{nome}/{dataBase}/...`, proxy `/api`) → engine (`{curves.engine.url}/api/v1/curvas/{codigo}/{dataBase}/...`; a curves troca o nome pelo código da curva, e uma curva sem código recebe 422). No 2xx, a curves repassa **status e corpo sem alterar**; no 4xx, mantém status, código, mensagem e `detalhes` do engine no **formato de erro da curves**; tempo esgotado, rede ou 5xx viram `503 ENGINE_INDISPONIVEL`.

| Ação | Front → curves | curves → engine | Tempo front / curves |
|---|---|---|---|
| Construir | `POST .../construcao` | `POST .../construcao` | 130 s / 120 s |
| Recalcular | `POST .../construcao?forcarRecalculo=true` | idem | 130 s / 120 s |
| Origem secundária | `POST .../construcao?forcarRecalculo=true&fonte=B3&produto=TS` | idem | 130 s / 120 s |
| Regravar interpolada | `POST .../interpolada` | `POST .../interpolada` | 70 s / 60 s |
| Vértices | `GET .../vertices` | `GET /api/v1/curvas/{codigo}/{dataBase}` | 40 s / 30 s |
| Interpolar | `GET .../interpolacao?du=21&du=252&data=2027-01-04` | idem (repetir os parâmetros como vieram) | 40 s / 30 s |
| Apagar curva construída | `DELETE .../vertices` (não passa pelo engine) | — | 3 s (padrão) |

Cabeçalhos curves → engine: `X-Correlation-Id` (o recebido), `X-Usuario` (o recebido, quando vier; sem autenticação na v0 e na v1), **nunca** `Authorization`.

Respostas que o front lê. Sem avisos de cadastro nas respostas da curves (v1.2): o front só mostra a mensagem do erro. Os nomes abaixo são **os que o engine da v1 devolve** (relatório de inspeção do engine); confira no DTO do engine se algo não bater e use o nome real:

```jsonc
// construcao (200) — `situacao` vem da change engine-v1-1; `quantidadePontos` e `avisos` já vêm em todos os resultados
{ "situacao": "RECONSTRUIDA", "codigo": "PRE", "dataBase": "2026-09-14", "hashPontos": "7c49...",
  "quantidadePontos": 278, "avisos": [ { "codigo": "ORIGEM_SECUNDARIA", "mensagem": "...", "detalhes": [] } ] }
// vertices (200) — a lista se chama "pontos"; na tela, "Vértices"
{ "codigo": "PRE", "dataBase": "2026-09-14", "hashPontos": "7c49...", "avisos": [],
  "pontos": [ { "data": "2026-09-15", "valor": "13.9000000", "diasUteis": 1, "diasCorridos": 1,
                "fatorAcum": "1.0005166043641946", "fatorDia": "1.0005166043641946" } ] }
// interpolacao (200) — sem "pedido": a lista vem na ordem pedida
{ "prazos": [ { "data": "2026-10-14", "du": 21, "dc": 30, "x": "...", "valor": "13.8123456",
                "classificacao": "INTERPOLADO", "fatorAcum": "...", "fatorDia": "..." } ] }
// erro: no formato de erro da curves (code, error, message, detalhes); o front lê pelo helper lerErro (§2.3)
```

## 2. web/fed

### 2.1 Tempo limite por requisição (`core/interceptors/request.interceptor.ts`)

Trocar o arquivo por:

```ts
import { HttpContextToken, HttpEvent, HttpHandlerFn, HttpRequest } from '@angular/common/http';
import { inject, isDevMode } from '@angular/core';
import { Observable, timeout } from 'rxjs';
import { CookieService } from '../services/cookie/cookie.service';

/** Tempo limite da requisição em ms; padrão 3 s. Ações longas passam um valor maior pelo HttpContext. */
export const TEMPO_LIMITE_MS = new HttpContextToken<number>(() => 3000);

export function requestInterceptor(req: HttpRequest<unknown>, next: HttpHandlerFn): Observable<HttpEvent<unknown>> {
  const cookieService = inject(CookieService);
  const csrfToken: string | null = cookieService.getCsrfToken();
  isDevMode() && console.log('[Request Interceptor] CSRF Token:', csrfToken);
  const headers: Record<string, string> = { 'x-csrf-token': csrfToken ?? '' };
  if (!(req.body instanceof FormData)) {
    headers['Content-Type'] = 'application/json';
  }
  req = req.clone({ setHeaders: headers, withCredentials: true });
  return next(req).pipe(timeout(req.context.get(TEMPO_LIMITE_MS)));
}
```

### 2.2 Serviço (`core/services/curvas-mercado/curvas-mercado.service.ts`)

Mesmo molde do `ProvedoresService` (`inject(HttpClient)`, `inject(AppConfigService)`, URL = `this._app.config.urlAPI + '/api/v1/curvas-mercado'`).

```ts
export interface CurvaMercado {
  nome: string; codigo: string | null; unidade: 'TAXA' | 'PRECO' | 'PONTOS';
  dayCounterCotacao: string | null; compounding: string | null; moeda: string; pais: string;
  classificacao: string | null; classeAtivo: string | null; situacao: 'ATIVO' | 'INATIVO';
  inicioVigencia: string; fimVigencia: string | null; dono: string | null;
}
export interface ItemListaCurvas extends Pick<CurvaMercado, 'nome' | 'codigo' | 'unidade' | 'situacao' | 'moeda' | 'inicioVigencia' | 'fimVigencia' | 'dono'> {
  provedores: string[];                                       // ordem de prioridade; [0] = principal
  ultimaExecucao: { dataBase: string; usuario: string | null } | null;
}
export interface PaginaCurvas { /* nomes do CurvasMercadoPaginadaResponse real: a lista de itens, totalElementos, totalPaginas */ itens: ItemListaCurvas[]; totalElementos: number; totalPaginas: number; }
export interface ProvedorDaCurva { idCurvaProvedor: number; provedor: string; produto: string; tickerProvedor: string; prioridade: number; }
export interface FiltroCurvas { nome?: string; codigo?: string; provedor?: string; dono?: string; unidade?: string; situacao?: string; pagina: number; tamanho: number; }

const ctx = (ms: number) => ({ context: new HttpContext().set(TEMPO_LIMITE_MS, ms) });
const base = (nome: string, dataBase: string) => `${url}/${encodeURIComponent(nome)}/${dataBase}`;

export interface VersaoConfiguracao { versao: number; modeloConstrucao: string; interpolador: string; parametros: Record<string, unknown>; inicioVigencia: string; fimVigencia: string | null; }
export interface NovaVersao { modeloConstrucao: string; interpolador: string; parametros: Record<string, unknown>; inicioVigencia: string; }
```

| Método | Chamada |
|---|---|
| `listar(f: FiltroCurvas)` → `PaginaCurvas` | `GET url` com `HttpParams` só dos filtros preenchidos |
| `consultar(nome)` | `GET url/{nome}` (curva + `provedores` + `configuracaoVigente`) |
| `criar(c)` / `alterar(nome, c)` | `POST url` / `PUT url/{nome}` |
| `excluir(nome)` | `DELETE url/{nome}` (409 `CURVA_COM_HISTORICO` com a mensagem) |
| `inativar(nome)` / `reativar(nome)` | `POST url/{nome}/inativacao` / `.../reativacao` |
| `baixarAuditoria(nome)` | `GET url/{nome}/auditoria` (JSON, baixado como `auditoria-{nome}.json`) |
| `valores()` | `GET url/valores` |
| `incluirProvedor`, `alterarProvedor`, `excluirProvedor` | `POST/PUT/DELETE url/{nome}/provedores[/{idCurvaProvedor}]` |
| `listarVersoes(nome)` / `versaoVigente(nome, data?)` | `GET url/{nome}/configuracoes` / `.../configuracoes/vigente?data=` |
| `validarVersao(nome, v)` / `criarVersao(nome, v)` | `POST url/{nome}/configuracoes/validacao` / `POST url/{nome}/configuracoes` |
| `excluirVersao(nome, versao)` | `DELETE url/{nome}/configuracoes?versao={n}` (409 `VERSAO_EM_USO`) |
| `construir(nome, dataBase, forcar = false, fonte?, produto?)` | `POST base/construcao`, `ctx(130000)` |
| `regravarInterpolada(nome, dataBase)` | `POST base/interpolada`, `ctx(70000)` |
| `apagarConstruida(nome, dataBase)` | `DELETE base/vertices` |
| `vertices(nome, dataBase)` | `GET base/vertices`, `ctx(40000)` |
| `interpolar(nome, dataBase, dus: number[], datas: string[])` | `GET base/interpolacao` com um `du` e um `data` por item (`params.append`), `ctx(40000)` |

### 2.3 Telas

- **Rotas** (`app.routes.ts`): `cadastro-curvas` → `CurvasListaComponent`; `cadastro-curvas/nova` → `CurvaAddComponent`; `cadastro-curvas/:nome` → `CurvaDetalheComponent`; `cadastro-curvas/:nome/configuracoes` → `ConfiguracaoCurvaComponent` (novo, `components/configuracao-curva/`); `curvas` → `CurvasDiaComponent` (novo, `components/curvas-dia/`). Sai `curvas/nova` e `curvas/:id`. Nomes têm espaço e acento: sempre `encodeURIComponent` nas URLs e `decodeURIComponent` no parâmetro da rota.
- **Cabeçalho**: em `subMenuItems`, `{ label: 'Curvas', route: '/curvas', ... }` e `{ label: 'Cadastro de curvas', route: '/cadastro-curvas', ... }`; em `BREADCRUMB_LABELS`, `'cadastro-curvas': 'Cadastro de curvas'`.
- **Erros na tela, nunca no handler global**: toda gravação, ação e consulta usa `subscribe({ next, error: (e) => this.erro.set(lerErro(e)) })`, com o helper novo `core/error/ler-erro.ts`, que entende o formato da curves e o do processor (upload pelo bff):

```ts
export interface ErroTela { codigo?: string; mensagem: string; detalhes: { campo?: string; linha?: number; motivo?: string }[] }
export function lerErro(e: HttpErrorResponse): ErroTela {
  const b: any = e.error ?? {};
  return {
    codigo: b.codigoErro ?? b.error ?? b.code,           // processor | curves (error) | Problem Details
    mensagem: b.mensagem ?? b.message ?? b.detail ?? 'Erro inesperado.',
    detalhes: b.detalhes ?? [],
  };
}
```
 Tempo esgotado (`e.name === 'TimeoutError'`) → "A ação não respondeu a tempo; consulte os vértices para conferir se terminou."
- **Formato**: datas `dd/mm/aaaa` na tela e `AAAA-MM-DD` na API; decimais exibidos com `valor.replace('.', ',')` (são strings; nunca `Number(...)`).
- **Tabelas**: tabela do Liquid como em `provedores-lista` (`BradTableService.getInstance({ targetSelector, table })`, `patchLiquidA11yCheckboxBug`, `rowClick`).
- **Curvas (`curvas-dia`)**: topo com seleção da curva (pesquisa pelo `listar`) e data; barra de ações; abas `Vértices` (padrão) e `Interpolar`. Depois de ação 2xx, recarregar a aba aberta. "Origem secundária" só se `provedores.length > 1`, listando os de prioridade maior que a menor.
- **Textos de situação**: `CONSTRUIDA` "Construída", `RECONSTRUIDA` "Recalculada", `EXISTENTE` "Já construída"; classificação `PONTO` "Vértice", `INTERPOLADO` "Interpolado", `EXTRAPOLADO_INICIO` "Extrapolado no início", `EXTRAPOLADO_FIM` "Extrapolado no fim".

## 3. services/curves

Implementado na change `curves-cadastro-curvas` (spec `acoes-curva-mercado`, guia §16). Aqui só o contrato da §1.

## 5. Testes (poucos, sem subir o Spring)

| Classe | Onde | Cobre |
|---|---|---|
| `curvas-mercado.service.spec.ts` | fed | `HttpTestingController`: URL, parâmetros e `TEMPO_LIMITE_MS` de cada ação |

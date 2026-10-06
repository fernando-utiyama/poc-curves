> **Como executar com `/opsx-apply`** (o apply já leu `proposal.md`, `design.md` e as specs; não releia):
> 1. Trabalhe tarefa por tarefa. Cada tarefa cita a seção de [`implementacao.md`](implementacao.md) com o contrato e o código; leia só ela e abra só os arquivos da tabela §0 do guia; não explore o resto do repositório.
> 2. Não pare para perguntar: o design já decidiu. Se faltar algo, deixe `// TODO(revisao): <dúvida>` e siga.
> 3. Cada tarefa termina com a verificação dela; corrija até passar e marque a tarefa.
> 4. As rotas `/dados-mercado` da curves são da change `curves-cadastro-curvas` (tarefa 5.1); se ainda não existirem, use os contratos do guia e teste com `HttpTestingController`.
> 5. Depende da change `fed-curvas-mercado` no `fed` (`TEMPO_LIMITE_MS` e `lerErro`, tarefas 2.1 e 2.3 dela); se ainda não estiver aplicada, faça essas duas antes.
> 6. Planilhas **não fazem parte da v0 nem da v1**: a planilha desta tela entra com a parte 2 da curves. Ignore qualquer menção a planilha que tenha sobrado no guia ou no design.
>
> **Blocos por modelo.** Cada tarefa é **[básico]** (mecânica, com molde no projeto e verificação objetiva) ou **[forte]** (julgamento, regra ou integração). Rode o bloco básico com um **modelo barato** até a PAUSA e pare. Abra uma sessão nova com o **modelo forte** e rode `/opsx-apply` de novo: ele segue da primeira tarefa desmarcada, começando por conferir o básico (só o diff; corrige o que contradiz a spec, não reescreve).

## 1. web/fed: bloco básico (modelo barato)

- [ ] 1.1 [básico] (guia §3.1) `request.interceptor`: sem `Content-Type: application/json` quando o corpo é `FormData`; `proxy.conf.js` com `/api/v1/cargas` → `PROXY_BFF_TARGET`, antes de `/api` (design D5); verificar com `ng build`
- [ ] 1.2 [básico] (guia §3.2) `core/services/dados-mercado/dados-mercado.service.ts` (tickers, consulta, incluir, alterar, excluir um e todos, envio do arquivo ao bff com `TEMPO_LIMITE_MS` 130000) e o `dados-mercado.service.spec.ts` (`HttpTestingController`: URLs, `FormData` sem `Content-Type` JSON, tempo limite); verificar com `ng build` e `ng test`
- [ ] 1.3 [básico] (guia §3.3) `app.routes.ts` com `dados-mercado` e o item "Dados de mercado" no `header.component`; `components/dados-mercado` com os filtros (provedor, ticker dependente do provedor, data `dd/mm/aaaa`) e a consulta com uma tabela do Liquid por curva ligada, nas colunas do provedor; verificar no navegador "Tickers dependentes do provedor" e "Consulta da PRE"
- [ ] 1.4 **PAUSA (troca para o modelo forte):** pronto quando `ng build` e `ng test` passam e a consulta mostra as tabelas contra a curves; escrever o resumo (arquivos, testes, `TODO(revisao)`) e parar.

## 2. web/fed: bloco forte (modelo forte)

- [ ] 2.1 [forte] Conferir o diff do bloco básico contra a spec `fed-dados-mercado` e o guia §3; corrigir só o que diverge
- [ ] 2.2 [forte] (guia §3.3) Manutenção por modais (incluir, editar, excluir, excluir todos da data), avisos depois da gravação, erros 422 com `detalhes` nos campos do modal e o link para `/curvas?codigo={codigo}` com `CURVA_JA_CONSTRUIDA`; decimais com vírgula na tela e ponto na API; verificar no navegador os cenários do requisito "Manutenção dos vértices"
- [ ] 2.3 [forte] (guia §3.3) Bloco "Enviar arquivo da fonte": provedor e arquivo, aviso de substituição, envio ao bff, resultado com data-base, `idCarga` e vértices por código, filtros posicionados no provedor e na data devolvidos; erros pelo `lerErro` (formato do processor); verificar no navegador "TaxaSwap enviado" e "Arquivo rejeitado"

## 3. Fechamento

- [ ] 3.1 [forte] Rodar `ng build` e `ng test` no `fed` e `openspec validate fed-dados-mercado --strict`; verificar que tudo passa
- [ ] 3.2 Homologação: enviar pela tela o `TaxaSwap.txt` e o `ms260928.txt`, consultar os vértices gravados e corrigir um vértice da NTN-B; conferir as tabelas brutas no banco

> **Como executar com `/opsx-apply`** (o apply já leu `proposal.md`, `design.md` e as specs; não releia):
> 1. Trabalhe tarefa por tarefa. Cada tarefa cita a seção de [`implementacao.md`](implementacao.md) com o contrato e o código; leia só ela e abra só os arquivos da tabela §0 do guia; não explore o resto do repositório.
> 2. Não pare para perguntar: o design já decidiu. Se faltar algo, deixe `// TODO(revisao): <dúvida>` e siga.
> 3. Cada tarefa termina com a verificação dela; corrija até passar e marque a tarefa.
> 4. As rotas `/dados-mercado` da curves são da change `curves-cadastro-curvas` (tarefas 5.x); se ainda não existirem, use os contratos do guia e teste com `HttpTestingController`.
> 5. Testes sem subir o contexto do Spring nem banco real: MockMvc `standaloneSetup`, portas simuladas e objetos montados à mão; no `fed`, `HttpTestingController`.
> 6. Depende da change `fed-curvas-mercado` no `fed` (`TEMPO_LIMITE_MS` no `request.interceptor`); se ela ainda não estiver aplicada, faça a tarefa 2.1 daquela change antes da 2.1 daqui.

## 2. web/fed: tela Dados de mercado

- [ ] 2.1 (guia §3.1) `request.interceptor`: sem `Content-Type: application/json` quando o corpo é `FormData`; `proxy.conf.js` com `/api/v1/cargas` → `PROXY_BFF_TARGET`, antes de `/api` (design D5); verificar com `ng build`
- [ ] 2.2 (guia §3.2) `core/services/dados-mercado/dados-mercado.service.ts`: tickers, consulta, incluir, alterar, excluir um e todos, baixar planilha, importar planilha (`TEMPO_LIMITE_MS` 70000) e envio do arquivo ao bff (`TEMPO_LIMITE_MS` 130000); verificar com `ng build`
- [ ] 2.3 (guia §3.3) `components/dados-mercado`: filtros (provedor, ticker dependente, data), consulta com uma tabela do Liquid por curva ligada, colunas por provedor; verificar no navegador "Tickers dependentes do provedor" e "Consulta da PRE"
- [ ] 2.4 (guia §3.3) Na mesma tela, a manutenção por modais (incluir, editar, excluir, excluir todos da data), avisos depois da gravação, erros 422 no modal e o link para `/curvas?codigo={codigo}` com `CURVA_JA_CONSTRUIDA`; verificar no navegador os cenários do requisito "Manutenção dos vértices"
- [ ] 2.5 (guia §3.3) Planilha: baixar, importar com simulação, lista de mudanças e erros, aviso de exclusões e "Aplicar" só sem erros; verificar no navegador os cenários do requisito "Importação por planilha"
- [ ] 2.6 (guia §3.3) Bloco "Enviar arquivo da fonte": provedor e arquivo, aviso de substituição, envio ao bff, resultado com data-base, `idCarga` e vértices por código, filtros posicionados no provedor e na data; erros na tela; verificar no navegador "TaxaSwap enviado" e "Arquivo rejeitado"
- [ ] 2.7 (guia §3.3) `app.routes.ts` com `dados-mercado` e o item "Dados de mercado" no `header.component`; verificar com `ng build` e a navegação pelo menu

## 3. Fechamento

- [ ] 3.1 Rodar `ng build` no `fed` e `openspec validate fed-dados-mercado --strict`; verificar que tudo passa
- [ ] 3.2 Homologação: enviar pela tela o `TaxaSwap.txt` e o `ms260928.txt`, consultar os vértices gravados, corrigir um vértice da NTN-B e importar uma planilha da `PRE`; conferir as tabelas brutas no banco

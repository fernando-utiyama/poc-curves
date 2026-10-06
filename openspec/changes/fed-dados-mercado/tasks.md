> **Como executar com `/opsx-apply`** (o apply já leu `proposal.md`, `design.md` e as specs; não releia):
> 1. Trabalhe tarefa por tarefa. Cada tarefa cita a seção de [`implementacao.md`](implementacao.md) com o contrato e o código; leia só ela e abra só os arquivos da tabela §0 do guia; não explore o resto do repositório.
> 2. Não pare para perguntar: o design já decidiu. Se faltar algo, deixe `// TODO(revisao): <dúvida>` e siga.
> 3. Cada tarefa termina com a verificação dela; corrija até passar e marque a tarefa.
> 4. Usa os CRUDs `primaria-*` que já existem na curves; nenhuma mudança na curves.
> 5. Depende da change `fed-curvas-mercado` no `fed` (`TEMPO_LIMITE_MS` e `lerErro`, tarefas 2.1 e 2.3 dela); se ainda não estiver aplicada, faça essas duas antes.
> 6. Fora da v1: planilha, rota `/dados-mercado` unificada e filtro por ticker da fonte. Ignore menções a isso que tenham sobrado.
>
> **Blocos por modelo.** Cada tarefa é **[básico]** (mecânica, com molde no projeto e verificação objetiva) ou **[forte]** (julgamento, regra ou integração). Rode o bloco básico com um **modelo barato** até a PAUSA e pare. Abra uma sessão nova com o **modelo forte** e rode `/opsx-apply` de novo: ele segue da primeira tarefa desmarcada, começando por conferir o básico (só o diff; corrige o que contradiz a spec, não reescreve).

## 1. web/fed: bloco básico (modelo barato)

- [ ] 1.1 [básico] (guia §2.1) `request.interceptor`: sem `Content-Type: application/json` quando o corpo é `FormData`; `proxy.conf.js` com `/api/v1/cargas` → `PROXY_BFF_TARGET`, antes de `/api` (design D4); verificar com `ng build`
- [ ] 1.2 [básico] (guia §1 e §2.2) `core/services/dados-mercado/dados-mercado.service.ts` com os métodos do guia, as colunas e campos por provedor lidos dos DTOs da curves, e o `dados-mercado.service.spec.ts`; verificar com `ng build` e `ng test`
- [ ] 1.3 [básico] (guia §2.3) `app.routes.ts` com `dados-mercado` e o item "Dados de mercado" no `header.component`; `components/dados-mercado` com os filtros (provedor, período, curva) e a listagem geral paginada; verificar no navegador "Bruto da B3 no período" e "Troca de provedor"
- [ ] 1.4 **PAUSA (troca para o modelo forte):** pronto quando `ng build` e `ng test` passam e a listagem aparece contra a curves; escrever o resumo (arquivos, testes, `TODO(revisao)`) e parar.

## 2. web/fed: bloco forte (modelo forte)

- [ ] 2.1 [forte] Conferir o diff do bloco básico contra a spec `fed-dados-mercado` e o guia; corrigir só o que diverge
- [ ] 2.2 [forte] (guia §2.3) Vértices da curva na data ao clicar na listagem; manutenção por modais (incluir, editar, excluir, excluir todos da data), avisos depois da gravação, erros com `detalhes` nos campos do modal, lembrete e link "Recalcular a curva" com a curva construída; decimais com vírgula na tela e ponto na API, sem conta; verificar no navegador os cenários "Vértices da PRE", "Correção de um valor já construído" e "Campo inválido"
- [ ] 2.3 [forte] (guia §2.3) Bloco "Enviar arquivo da fonte": provedor e arquivo, aviso de substituição, envio ao bff, resultado com data-base, `idCarga` e vértices por código, filtros posicionados; erros pelo `lerErro` (formato do processor); verificar no navegador "TaxaSwap enviado" e "Arquivo rejeitado"

## 3. Fechamento

- [ ] 3.1 [forte] Rodar `ng build` e `ng test` no `fed`; verificar que passam

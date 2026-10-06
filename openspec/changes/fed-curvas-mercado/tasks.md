> **Como executar com `/opsx-apply`** (o apply já leu `proposal.md`, `design.md` e as specs; não releia):
> 1. Trabalhe tarefa por tarefa. Cada tarefa cita a seção de [`implementacao.md`](implementacao.md) com o contrato e o código; leia só ela e abra só os arquivos da tabela §0 do guia; não explore o resto do repositório.
> 2. Não pare para perguntar: o design já decidiu. Se faltar algo, deixe `// TODO(revisao): <dúvida>` e siga.
> 3. Cada tarefa termina com a verificação dela; corrija até passar e marque a tarefa.
> 4. Só front. As rotas da curves (listagem com provedor, dono e última execução; repasse ao engine) são da change `curves-v1.1`, e o `situacao` da construção é da `engine-v1.1`; se ainda não existirem, teste com `HttpTestingController`. Fica para a v2: aba de pontos interpolados.
> 5. Testes só no `fed`, com `HttpTestingController`.
>
> **Blocos por modelo.** Cada tarefa é **[básico]** (mecânica, com molde no projeto e verificação objetiva) ou **[forte]** (julgamento, regra ou integração). Rode cada bloco até a PAUSA dele e pare. Para trocar de modelo, abra uma sessão nova com o modelo indicado e rode `/opsx-apply` de novo: ele segue da primeira tarefa desmarcada. O bloco forte começa conferindo o básico (só o diff; corrige o que contradiz a spec, não reescreve).

## 2. web/fed: bloco básico (modelo barato)

- [ ] 2.1 [básico] (guia §2.1) `HttpContextToken` `TEMPO_LIMITE_MS` (padrão 3000) em `core/interceptors/request.interceptor.ts`, no lugar do `timeout(3000)` fixo, exatamente como o código do guia; verificar com `ng build`
- [ ] 2.2 [básico] (guia §2.2) `core/services/curvas-mercado/curvas-mercado.service.ts` com as interfaces e todos os métodos da tabela do guia (cadastro, provedores da curva, ações e consultas com `TEMPO_LIMITE_MS` de 130000, 70000 e 40000; sem `pontos`) e o `curvas-mercado.service.spec.ts` (`HttpTestingController`: URL, parâmetros e tempo limite de cada método); verificar com `ng build` e `ng test`
- [ ] 2.3 [básico] (guia §2.3) Helper `core/error/ler-erro.ts` como no guia; `app.routes.ts` com `cadastro-curvas`, `cadastro-curvas/nova`, `cadastro-curvas/:codigo` e `curvas`; `components/curvas-dia` criado só como casca (título "Curvas" e nada mais); `header.component` com os itens "Curvas" e "Cadastro de curvas" e as migalhas de pão; verificar com `ng build` e a navegação pelo menu
- [ ] 2.4 [básico] (guia §2.3) Cadastro, pesquisa: reescrever `components/curvas-lista` sobre o serviço (filtros da spec com 300 ms nos textos, padrão `ATIVO`, colunas da spec, tabela do Liquid como em `provedores-lista`, paginação de 50 com o `total`, "Nova curva"), sem `localStorage`; verificar no navegador "Filtro por trecho do nome" e "Curvas inativas"
- [ ] 2.5 [básico] (guia §2.3) Cadastro, criação: reescrever `components/curva-add` (campos da spec `fed-cadastro-curvas`, inclusive dono, listas de `valores`, cotação só com `TAXA`, erros por campo de `detalhes`, volta ao detalhe com o aviso); verificar no navegador "Nome em uso" e "Unidade de preço"
- [ ] 2.6 **PAUSA (troca para o modelo forte):** pronto quando `ng build` e `ng test` passam, o menu abre as quatro rotas e a pesquisa e a criação funcionam contra a curves; escrever o resumo (arquivos, testes, `TODO(revisao)`) e parar.

## 3. web/fed: bloco forte (modelo forte)

- [ ] 3.1 [forte] Conferir o diff do bloco básico contra as specs `fed-cadastro-curvas` e `fed-curvas-dia` e o guia §2; corrigir só o que diverge
- [ ] 3.2 [forte] (guia §2.3) Cadastro, detalhe: reescrever `components/curva-detalhe` com dados da curva (editar sem nome e situação, inativar e reativar com modal, auditoria em JSON, "Abrir na tela Curvas") e provedores da curva (tabela, modais, avisos, erros 409 no modal); retirar a parametrização, o import de Excel, as ações de data e o `localStorage` (design D6); verificar no navegador os cenários da spec `fed-cadastro-curvas`
- [ ] 3.3 [forte] (guia §2.3) Curvas: completar `components/curvas-dia` com a seleção da curva e da data (`?codigo=`), a barra de ações (construir, recalcular com confirmação, origem secundária, regravar interpolada), o resultado na tela e as abas Vértices (nomes do engine: `pontos`, `fatorAcum`, `fatorDia`) e Interpolar (lista `prazos` na ordem pedida); erros pelo `lerErro`, na tela, sem a página de erro global; tempo esgotado com a mensagem do guia (design D5 e D7); verificar no navegador os cenários da spec `fed-curvas-dia`

## 4. Fechamento

- [ ] 4.1 [forte] Rodar `ng build` e `ng test` no `fed`; verificar que passam

> **Como executar com `/opsx-apply`** (o apply já leu `proposal.md`, `design.md` e as specs; não releia):
> 1. Trabalhe tarefa por tarefa. Cada tarefa cita a seção de [`implementacao.md`](implementacao.md) com o contrato e o código; leia só ela e abra só os arquivos da tabela §0 do guia; não explore o resto do repositório.
> 2. Não pare para perguntar: o design já decidiu. Se faltar algo, deixe `// TODO(revisao): <dúvida>` e siga.
> 3. Cada tarefa termina com a verificação dela; corrija até passar e marque a tarefa.
> 4. Pare na tarefa **1.2** (PAUSA), escreva o resumo e espere a revisão. Depois, um novo `/opsx-apply` segue com a seção 3. As rotas da curves (cadastro, listagem e ações via engine) são da change `curves-cadastro-curvas`.
> 5. Testes sem subir o contexto do Spring nem servidor real: MockMvc `standaloneSetup` e objetos montados à mão no engine e na curves; no `fed`, `HttpTestingController`.

## 1. engine: pontos interpolados gravados

- [ ] 1.1 (guia §4) Rota `GET /api/v1/curvas/{codigo}/{dataBase}/pontos?de=&ate=` no controller de curva do engine e um caso de uso de leitura de `tDadoCurva` (porta `DadoCurvaPort` que já existe, com um método de leitura por intervalo), devolvendo total, `hashPontos` dos vértices atuais e os pontos; 404 `CURVA_NAO_CONSTRUIDA` sem vértices; aviso `INTERPOLADA_DESATUALIZADA` quando não confere (a mesma conferência da consulta de vértices); escrever o teste com portas simuladas para os três cenários da spec `pontos-interpolados-engine` e fazê-lo passar

- [ ] 1.2 **PAUSA:** rodar `mvn compile` e `mvn test` no engine; escrever um resumo curto e parar até a revisão

## 3. web/fed: Cadastro de curvas e Curvas

- [ ] 3.1 (guia §2.1) `HttpContextToken` `TEMPO_LIMITE_MS` (padrão 3000) em `core/interceptors/request.interceptor.ts`, no lugar do `timeout(3000)` fixo (design D4); verificar com `ng build`
- [ ] 3.2 (guia §2.2) `core/services/curvas-mercado/curvas-mercado.service.ts`: listar com filtros e paginação, consultar, criar, alterar, inativar, reativar, baixar auditoria, valores, provedores da curva (listar, incluir, alterar, excluir) e as ações e consultas (`construir`, `regravarInterpolada`, `vertices`, `pontos`, `interpolar`) com `TEMPO_LIMITE_MS` de 130000, 70000 e 40000; verificar com `ng build`
- [ ] 3.3 (guia §2.3) Cadastro, pesquisa: reescrever `components/curvas-lista` sobre o serviço (filtros com 300 ms nos textos, padrão `ATIVO`, tabela do Liquid, paginação de 50, "Nova curva"), sem `localStorage`; verificar no navegador os cenários "Filtro por trecho do nome" e "Curvas inativas"
- [ ] 3.4 (guia §2.3) Cadastro, criação: reescrever `components/curva-add` (campos da spec `fed-cadastro-curvas`, listas de `valores`, cotação só com `TAXA`, erros por campo, volta ao detalhe com o aviso); verificar no navegador "Nome em uso" e "Unidade de preço"
- [ ] 3.5 (guia §2.3) Cadastro, detalhe: reescrever `components/curva-detalhe` com dados da curva (editar sem nome e situação, inativar e reativar com modal, auditoria, "Abrir na tela Curvas") e provedores da curva (tabela, modais, avisos, erros 409 no modal); retirar a parametrização, o import de Excel, as ações de data e o `localStorage` (design D6); verificar no navegador os cenários da spec `fed-cadastro-curvas`
- [ ] 3.6 (guia §2.3) Curvas: componente novo `components/curvas-dia` com a seleção da curva e da data (`?codigo=`), a barra de ações (construir, recalcular com confirmação, origem secundária, regravar interpolada), o resultado na tela e as abas Vértices, Pontos (intervalo padrão de 30 dias, aviso `INTERPOLADA_DESATUALIZADA`) e Interpolar; erros das ações e consultas mostrados na tela, sem a página de erro global (design D5 e D7); verificar no navegador os cenários da spec `fed-curvas-dia`
- [ ] 3.7 (guia §2.3) `app.routes.ts`: `cadastro-curvas`, `cadastro-curvas/nova` e `cadastro-curvas/:codigo` (componentes do cadastro) e `curvas` (`curvas-dia`); `header.component` com os itens "Curvas" (`/curvas`) e "Cadastro de curvas" (`/cadastro-curvas`) e as migalhas de pão desses caminhos; verificar com `ng build` e a navegação pelo menu

## 4. Fechamento

- [ ] 4.1 Rodar `mvn test` no engine, `ng build` no `fed` e `openspec validate fed-curvas-mercado --strict`; verificar que tudo passa
- [ ] 4.2 Homologação: com curves e engine no ar, na tela Curvas, recalcular a `PRE` de uma data-base e conferir "Recalculada", os vértices, os pontos da primeira semana e uma interpolação de 252 dias úteis; derrubar o engine e conferir `ENGINE_INDISPONIVEL` na tela; no Cadastro de curvas, criar, alterar e inativar uma curva de teste

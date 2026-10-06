## Context

A curves v1 já está aplicada (relatório `docs/inspecao/inspecao-curves.md` do poc): tem o CRUD da curva, dos provedores da curva, da configuração e dos brutos, sem cliente do engine; a entidade já mapeia `cPprioDado`, mas o adaptador não o copia; a listagem devolve `totalElementos`/`totalPaginas` e não tem provedores, dono nem última execução. `dBaseReft` e `cUsuarCalc` são só leitura na entidade. O engine v1 expõe as rotas de construção, regravação, consulta e interpolação, sem autenticação.

## Goals / Non-Goals

**Goals:** o mínimo para as telas Cadastro de curvas e Curvas do `fed`.

**Non-Goals:** segurança; mudança de formato de erro, de decimais ou de instantes; `/dados-mercado`; qualquer item da lista "Fica para a v2" da proposta.

## Decisions

### D1. Repasse sem regra
A curves só confere a curva e os parâmetros e repassa ao engine; 2xx volta como veio. **Por quê:** o front fala só com a curves (um proxy), e o contrato da resposta é o do engine.

### D2. Erro do engine no formato que a curves já tem
4xx do engine vira a exceção de negócio da curves com o mesmo status, código e mensagem; o tratador atual monta a resposta. Sem mudar o formato de erro da curves.

### D3. Provedores da página numa consulta
Uma consulta `WHERE cTickerIndcd IN (...)` ordenada por `cPriorCsumo` para a página inteira, nunca uma por curva.

## Risks / Trade-offs

- [Engine fora deixa as ações indisponíveis] → o cadastro continua; as ações respondem `ENGINE_INDISPONIVEL`.
- [Rota de pontos interpolados ainda não existe] → a tela Curvas da v1.1 não tem a aba de pontos.

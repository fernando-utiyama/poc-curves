## Context

Existe **um endpoint só**, chamado pelo orquestrador, que recebe o provedor (B3, ANBIMA, Bloomberg) no corpo e o entrega ao caso de uso e ao serviço daquela fonte. O serviço baixa do site, grava o original no Blob e grava o dado no banco. Esta change acrescenta, no fim do serviço de cada fonte, o disparo do engine pela mesma porta de saída (`POST /api/v1/cargas` do engine, spec `curve-load-trigger`), com uma implementação só. O engine constrói na hora as curvas da carga, cada uma na sua transação, e devolve o resultado por curva. Se o disparo falha, a recuperação é chamar o endpoint de novo para a data (reprocessamento). O orquestrador só espera o 200 do endpoint e **não** espera o engine.

Três fontes, três donos: B3 e ANBIMA (outros desenvolvedores) e Bloomberg (`processor-bloomberg`). Cada dono acrescenta o disparo no serviço da sua fonte, com o mesmo corpo e as mesmas regras.

## Goals / Non-Goals

**Goals:** o mesmo aviso, o mesmo `verticesPorCurva`, a mesma repetição e o mesmo log nas três fontes; o engine conferindo a quantidade.

**Non-Goals:** mudar a rota do engine, o formato de erro, o agendamento; construir curva derivada; guardar a carga no engine.

## Decisions

### D1. Um aviso por carga, depois do commit, em segundo plano
O aviso só sai depois do commit e roda em segundo plano (virtual thread), sem segurar a resposta ao orquestrador. **Por quê:** o engine pode levar minutos para construir; o orquestrador só precisa saber que o arquivo foi recebido e gravado. **Alternativa rejeitada:** esperar o engine antes de responder (estoura o tempo limite de 120 s do orquestrador).

### D2. A recuperação é o reprocessamento
Se o aviso se esgota, o log tem `CARGA_FALHOU` e a recuperação é executar o reprocessamento da data, que relê o original e avisa de novo com o mesmo `idCarga`. **Por quê:** o dado bruto já está gravado e o engine não guarda estado da carga; não precisa de fila nem de tabela. **Alternativa rejeitada:** fila persistente de avisos (infra nova).

### D3. `verticesPorCurva` conta o que foi **gravado**
A quantidade por curva é a de linhas gravadas na tabela bruta, **inclusive as que o modelo vier a descartar**. **Por quê:** o engine confere o que leu da tabela; se a contagem fosse depois do descarte, a conferência diria "ok" para uma carga truncada. A chave é o **nome da curva** (`cTickerIndcd`), a identidade da curva no projeto; o código da fonte (B3, `Titulo`, membro do ticker) só serve ao processor para achar as curvas em `tCurvaPrvdr` e não vai no aviso. O campo antigo `verticesPorCodigo` do webhook do engine passa a se chamar `verticesPorCurva` (alteração do contrato, a combinar com quem mantém B3 e ANBIMA e com o engine).

### D4. Conferência da quantidade no engine
O engine lê as linhas da curva e da data na tabela bruta e compara com a quantidade avisada para o código. **Diferente = a curva falha** com `INSUMO_INVALIDO` e uma mensagem com as duas quantidades (avisadas e lidas). **Por quê:** é o que a spec do webhook já diz e a inspeção mostrou que não é feito; protege contra leitura de uma gravação ainda não visível ou truncada. **Alternativa rejeitada:** só logar a diferença (a curva truncada seria construída).

### D5. Código de erro existente
Sem `INSUMO_INCOMPLETO` (item da v2 do engine); a conferência usa `INSUMO_INVALIDO`, que o engine já tem.

## Risks / Trade-offs

- [O engine é avisado antes de o dado bruto estar visível para ele] → o aviso só sai depois do commit; a conferência da quantidade (D4) cobre o resto.
- [Aviso esgotado e ninguém olha o log] → `CARGA_FALHOU` é log de erro e contador por fonte; o alerta no front fica para o dashboard do orquestrador (fora desta change).
- [Reprocessar avisa de novo e reconstrói curvas] → o engine nunca recalcula curva existente pela carga: devolve `EXISTENTE` (com `PONTOS_DIFERENTES_DA_FONTE` se mudou); só recálculo forçado reconstrói.

## Open Questions

1. O alerta de `CARGA_FALHOU` deve ir também para o dashboard do front (via orquestrador), ou basta log e métrica nesta etapa?

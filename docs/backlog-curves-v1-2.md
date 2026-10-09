# Backlog da v1.2 da curves

Itens levantados na revisão do Swagger do dado bruto (`/api/v1/curvas-mercado/{codigo}/primaria-*`), em 07/10/2026. Nada aqui bloqueia a v1.1.

## Dado bruto (primária B3, ANBIMA e Bloomberg)

1. **`produces` JSON nos controllers.** O Swagger mostra `*/*` como media type das respostas. Declarar `produces = MediaType.APPLICATION_JSON_VALUE` no `@RequestMapping` da classe de cada controller do dado bruto. Só documentação; o corpo já sai em JSON.

2. **Dado bruto não mexe na curva construída.** Incluir, alterar ou apagar o bruto (`POST`/`PUT`/`DELETE .../primaria-*/...`) altera **só** a tabela do provedor. Nunca toca `tDadoVertcCurva` nem `tDadoCurva`, nem dispara construção: a curva construída só muda quando o usuário dispara o recálculo (`POST /curvas-mercado/{codigo}/{dataBase}/construcao`). Conferir que nenhuma das rotas do bruto apaga ou regrava construído.
   O bruto é gravado **por curva** (`cTickerIndcd` = nome da curva, com FK para `tCurvaMercd`; o processor grava as linhas sob o nome de cada curva ligada ao código da fonte). Duas curvas ligadas ao mesmo código (`PRE` e `PRE_252`) têm linhas separadas, então apagar o bruto de uma não afeta a outra: **não há aviso de ticker compartilhado**.

3. **Listagem geral para seleção** (`GET /curvas-mercado/primaria-*?de=&ate=&codigo=&nome=`) **com a última data gravada.** Hoje devolve uma linha por curva × data-base e, sem período, os últimos 30 dias (máximo de 366 dias). Mudar para:
   - **sem `de` e sem `ate`**: uma linha por curva, com a **última data-base gravada** no bruto da curva (`MAX(dBaseReft)` por `cTickerIndcd`), a quantidade de pontos daquela data e se está construída. Responde "qual a última data que tenho da B3 para essa curva";
   - **com `de` e/ou `ate`**: como hoje (todas as datas do período).
   O formato da resposta não muda; só `codigosNaFonte` vira `tickersProvedor` (item 4). A query tira o `AND m.cTickerIdtfdUnic IS NOT NULL`: o GET respeita só as regras do banco. Vale para B3, ANBIMA e Bloomberg.
   **Fora desta rodada:** mostrar os dias úteis sem dado (buracos) no período. Exigiria o calendário do engine (`GET /api/v1/calendarios/{nome}`), que a curves ainda não chama; fica para a v2.

4. **Renomear `codigosNaFonte` para `tickersProvedor`** na resposta da listagem geral (as 3 fontes). É o `cTickerPrvdr` da `tCurvaPrvdr`: o código com que o provedor identifica a curva (`PRE` na B3, `NTN-B` na ANBIMA, `S0490Z` na Bloomberg). Combinar a troca com o front, que lê o campo.

## Curvas de mercado

5. **Excluir curva de mercado** (`DELETE /api/v1/curvas-mercado/{codigo}`), só no modo seguro:
   - a `tCurvaMercd` tem chave estrangeira vinda de 11 tabelas. A curva só sai se **nenhuma** das que ela não apaga tiver linhas dela: `tDadoVertcCurva`, `tDadoCurva`, `tBtrsCurvaPrimr`, `tAnbmaCurvaPrimr`, `tBbergCurvaPrimr`, `tCmeCurvaPrimr`, `tLchCurvaPrimr`, `tLsegCurvaPrimr`, `tMtrizCurva`. Se houver, responde **409** `CURVA_COM_HISTORICO` com as linhas por tabela (em vez de deixar o banco recusar com 500);
   - sem dependentes, apaga na mesma transação todas as configurações (`tConfgCurva`, inclusive a vigente e as passadas, que as rotas da v1 não deixam apagar) e os provedores (`tCurvaPrvdr`), e depois a curva;
   - **não apaga dado de outra tabela**: construído sai pelo item 6 (por data) e dado bruto pelas rotas `primaria-*` (por data). O bruto é por curva, então apagá-lo é seguro, mas continua sendo escolha do usuário;
   - **409 se a curva é componente de outra curva**, listando as que dependem dela. **Conferir em 08/10/2026 se curva derivada já existe na curves**; se não, o bloqueio fica inofensivo (nunca dispara) e pode ser retirado;
   - o front pede confirmação.

6. **Apagar a curva construída de uma data** (`DELETE /api/v1/curvas-mercado/{codigo}/{dataBase}/vertices`), na seção "Ações da curva", ao lado do `GET` da mesma rota:
   - apaga os vértices construídos da curva na data (`tDadoVertcCurva`) e, em cascata, a interpolada da mesma data (`tDadoCurva`), na mesma transação, para ninguém ler interpolada de vértices que não existem mais;
   - feito na própria curves, com a trava da curva (`travarPorNome`); é o único caso em que a curves escreve em `tDadoCurva`, e só para apagar (mesma regra da spec `vertices-curva-manual` da parte 2, que esta rota antecipa);
   - não toca o dado bruto, a configuração nem o cadastro; depois, a data fica "não construída" e pode ser construída de novo pelo `POST .../construcao` ou pela carga;
   - data sem vértices construídos → 404 `CURVA_NAO_CONSTRUIDA`; sucesso → 200 com `avisos` (vazio) e quantas linhas saíram de cada tabela;
   - log `VERTICES_EDITADOS` com operação `EXCLUSAO`;
   - o front pede confirmação.
   Junto com o item 5, permite apagar uma curva com histórico: apaga data por data e depois exclui a curva no modo seguro.

## Configuração de cálculo

7. **Excluir qualquer versão de configuração** (hoje só a última, e só se ainda não começou).
   - Rota: `DELETE /api/v1/curvas-mercado/{codigo}/configuracoes?versao=N`. **Sem `versao`, exclui a vigente hoje** (horário de Brasília). As versões existentes, com número e vigência, vêm do `GET .../configuracoes` ("Listar versões"), que o front mostra antes de excluir. Substitui o `DELETE .../configuracoes/{versao}` atual.
   - **Bloqueio (409 `VERSAO_EM_USO`, decidido)**: a versão tem data construída na janela de vigência dela (alguma `tDadoVertcCurva` da curva com data-base entre `inicioVigencia` e `fimVigencia`). Essas construções usaram os parâmetros dela; apagá-la deixa o histórico sem explicação. A resposta diz quantas datas e a primeira e a última; para liberar, apagar as datas pelo item 6.
   - **Continuidade das vigências** (a sequência continua sem buraco), na mesma transação:
     - versão do meio ou a última: a **anterior** estende o `fimVigencia` até o fim da excluída (nulo, se a excluída era a última);
     - a primeira: a **seguinte** passa a começar no `inicioVigencia` da excluída;
     - a única: sai, e a resposta traz o aviso `SEM_CONFIGURACAO` (a curva não constrói até ter outra versão).
     Só `dValidAte`/`dInicVgcia` da vizinha mudam; nenhuma outra coluna é regravada.
   - Versão futura (ainda não começou) continua podendo ser excluída sempre, como hoje.
   - Os números das versões não são renumerados: a sequência pode ficar com lacuna (1, 3).
   - Trava da curva (`travarPorNome`) antes, para não excluir no meio de uma construção; o front pede confirmação mostrando a vigência que a vizinha vai assumir.
   - Ajustar a spec `configuracao-calculo-curva` (regra "só a última versão, e só se ainda não começou" e a tabela de rotas).

## Ordem para apagar tudo de uma curva

Nada some com histórico pendurado: cada exclusão só passa quando o que depende dela já saiu. A sequência é:

1. datas construídas: `DELETE .../{codigo}/{dataBase}/vertices` (item 6), uma por data; leva junto a interpolada;
2. dado bruto: `DELETE .../{codigo}/primaria-{b3|anbima|bloomberg}/{dataBase}`, uma por data e provedor (rotas que já existem);
3. versões de configuração: `DELETE .../configuracoes?versao=N` (item 7), liberadas porque não há mais construção na vigência delas (esta etapa pode ser pulada: o passo 4 apaga as que restarem);
4. a curva: `DELETE /curvas-mercado/{codigo}` (item 5), que leva junto provedores e as configurações que restarem.

Fora dessa ordem, a resposta é 409 dizendo o que ainda falta apagar.

## Decidido (não fazer)

- **Excluir curva com histórico (cascata).** Curva já construída sai pela inativação, ou apagando as datas pelo item 6 e depois excluindo pelo item 5.

- **Delete sem motivo.** Sem login na v0/v1, não há quem assine nem onde guardar. Motivo e usuário entram juntos quando houver auditoria (v2).

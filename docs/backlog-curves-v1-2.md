# Backlog da v1.2 da curves

Itens levantados na revisão do Swagger, em 07 a 09/10/2026. A curva é identificada pelo **nome** (`cTickerIndcd`, PK): rotas `/{nome}`. Sem trava por nome nesta versão. Arquivos prontos em `docs/curves-v1-2/`.

## Dado bruto (primária B3, ANBIMA e Bloomberg)

1. **`produces` JSON nos controllers.** O Swagger mostra `*/*` como media type das respostas. Declarar `produces = MediaType.APPLICATION_JSON_VALUE` no `@RequestMapping` da classe de cada controller do dado bruto. Só documentação; o corpo já sai em JSON.

2. **Dado bruto não mexe na curva construída.** Incluir, alterar ou apagar o bruto (`POST`/`PUT`/`DELETE .../primaria-*/...`) altera **só** a tabela do provedor. Nunca toca `tDadoVertcCurva` nem `tDadoCurva`, nem dispara construção: a curva construída só muda quando o usuário dispara o recálculo (`POST /curvas-mercado/{nome}/{dataBase}/construcao`). Conferir que nenhuma das rotas do bruto apaga ou regrava construído.
   O bruto é gravado **por curva** (`cTickerIndcd` = nome da curva, com FK para `tCurvaMercd`; o processor grava as linhas sob o nome de cada curva ligada ao código da fonte). Duas curvas ligadas ao mesmo código (`PRE` e `PRE_252`) têm linhas separadas, então apagar o bruto de uma não afeta a outra: **não há aviso de ticker compartilhado**.

3. **Listagem geral para seleção** (`GET /curvas-mercado/primaria-*?de=&ate=&codigo=&nome=`) **com a última data gravada.** Hoje devolve uma linha por curva × data-base e, sem período, os últimos 30 dias (máximo de 366 dias). Mudar para:
   - **sem `de` e sem `ate`**: uma linha por curva, com a **última data-base gravada** no bruto da curva (`MAX(dBaseReft)` por `cTickerIndcd`), a quantidade de pontos daquela data e se está construída. Responde "qual a última data que tenho da B3 para essa curva";
   - **com `de` e/ou `ate`**: como hoje (todas as datas do período).
   O formato da resposta não muda; só `codigosNaFonte` vira `tickersProvedor` (item 4). A query tira o `AND m.cTickerIdtfdUnic IS NOT NULL`: o GET respeita só as regras do banco. Vale para B3, ANBIMA e Bloomberg.
   **Fora desta rodada:** mostrar os dias úteis sem dado (buracos) no período. Exigiria o calendário do engine (`GET /api/v1/calendarios/{nome}`), que a curves ainda não chama; fica para a v2.

4. **Renomear `codigosNaFonte` para `tickersProvedor`** na resposta da listagem geral (as 3 fontes). É o `cTickerPrvdr` da `tCurvaPrvdr`: o código com que o provedor identifica a curva (`PRE` na B3, `NTN-B` na ANBIMA, `S0490Z` na Bloomberg). Combinar a troca com o front, que lê o campo.

## Curvas de mercado

5. **Excluir curva de mercado** (`DELETE /api/v1/curvas-mercado/{nome}`), só no modo seguro:
   - quem recusa é o banco: a `tCurvaMercd` tem chave estrangeira vinda do construído, do dado bruto e de tabelas legadas. Se ainda houver linhas da curva nelas, a exclusão falha, a transação desfaz tudo e a resposta é **409** `CURVA_COM_HISTORICO` com a mensagem "A curva ainda tem dados vinculados (vértices construídos ou dado bruto dos provedores). Apague antes, ou use a inativação";
   - **dentro do `CurvaMercadoService`** (sem service nem use case novos), ao lado de criar, alterar, inativar e reativar;
   - apaga na mesma transação todas as configurações (`tConfgCurva`, inclusive a vigente e as passadas, que as rotas da v1 não deixam apagar) e os provedores (`tCurvaPrvdr`), e depois a curva;
   - **não apaga dado de outra tabela**: construído sai pelo item 6 (por data) e dado bruto pelas rotas `primaria-*` (por data). O bruto é por curva, então apagá-lo é seguro, mas continua sendo escolha do usuário;
   - sem bloqueio de "componente de outra curva": curva derivada (provedor `TCEN`) ainda não existe na curves (confirmado em 09/10/2026); entra quando existir;
   - o front pede confirmação.

6. **Apagar a curva construída de uma data** (`DELETE /api/v1/curvas-mercado/{nome}/{dataBase}/vertices`), na seção "Ações da curva", ao lado do `GET` da mesma rota:
   - apaga os vértices construídos da curva na data (`tDadoVertcCurva`) e, em cascata, a interpolada da mesma data (`tDadoCurva`), na mesma transação, para ninguém ler interpolada de vértices que não existem mais;
   - feito na própria curves (sem trava nesta versão); é o único caso em que a curves escreve em `tDadoCurva`, e só para apagar (mesma regra da spec `vertices-curva-manual` da parte 2, que esta rota antecipa);
   - não toca o dado bruto, a configuração nem o cadastro; depois, a data fica "não construída" e pode ser construída de novo pelo `POST .../construcao` ou pela carga;
   - data sem vértices construídos → 404 `CURVA_NAO_CONSTRUIDA`; sucesso → 200 sem corpo;
   - log `VERTICES_EDITADOS` com operação `EXCLUSAO`;
   - o front pede confirmação.
   Junto com o item 5, permite apagar uma curva com histórico: apaga data por data e depois exclui a curva no modo seguro.

## Configuração de cálculo

7. **Excluir qualquer versão de configuração** (hoje só a última, e só se ainda não começou).
   - Rota: `DELETE /api/v1/curvas-mercado/{nome}/configuracoes?versao=N`. **Sem `versao`, exclui a vigente hoje** (horário de Brasília). As versões existentes, com número e vigência, vêm do `GET .../configuracoes` ("Listar versões"), que o front mostra antes de excluir. Substitui o `DELETE .../configuracoes/{versao}` atual.
   - **Bloqueio (409 `VERSAO_EM_USO`, decidido)**: a versão tem data construída na janela de vigência dela (alguma `tDadoVertcCurva` da curva com data-base entre `inicioVigencia` e `fimVigencia`). Essas construções usaram os parâmetros dela; apagá-la deixa o histórico sem explicação. A resposta diz quantas datas e a primeira e a última; para liberar, apagar as datas pelo item 6.
   - **Continuidade das vigências** (a sequência continua sem buraco), na mesma transação:
     - versão do meio ou a última: a **anterior** estende o `fimVigencia` até o fim da excluída (nulo, se a excluída era a última);
     - a primeira: a **seguinte** passa a começar no `inicioVigencia` da excluída;
     - a única: sai (a curva não constrói até ter outra versão).
     Só `dValidAte`/`dInicVgcia` da vizinha mudam; nenhuma outra coluna é regravada.
   - Versão futura (ainda não começou) continua podendo ser excluída sempre, como hoje.
   - Os números das versões não são renumerados: a sequência pode ficar com lacuna (1, 3).
   - O front pede confirmação mostrando a vigência que a vizinha vai assumir. Sem trava nesta versão.
   - Ajustar a spec `configuracao-calculo-curva` (regra "só a última versão, e só se ainda não começou" e a tabela de rotas).

## Regra para todos os endpoints (front sem tratamento de erro por enquanto)

O front não monta tela a partir do corpo do erro: ele mostra o texto como veio. Por isso:
- **Sucesso:** `200` simples, sem corpo ou contadores desnecessários (o `DELETE .../{dataBase}/vertices` responde `200` vazio; o delete de curva, `204`).
- **Erro:** a **mensagem** tem que explicar sozinha o que houve e o que fazer (ex.: "A curva ainda tem dados em tDadoVertcCurva, tBtrsCurvaPrimr. Apague antes ... ou use a inativação"); nada depende de `detalhes`, códigos ou `avisos` para o usuário entender.
- Não criar record de resultado só para devolver número (por isso saíram `DadoCurvaApagado`, `DependenciaCurva`, `DadoVertcCurvaResumo` e `ApagarConstrucaoResponse`; o resumo do bruto é um só, `CurvaPrimrDataBase`, para as três fontes; excluir versão responde 200 vazio).
- Vale para os endpoints novos desta versão e para os que forem revisados: ao revisar um service, conferir que cada recusa tem texto completo no `motivo`.

## Identificador da curva = nome (vale para toda a v1.2)

A curva é aberta pelo **nome** (`cTickerIndcd`, PK de `tCurvaMercd`). O código (`cTickerIdtfdUnic`) é opcional no banco e fica só como filtro da listagem (`?codigo=`). As specs do front não foram alteradas: quem implementar a v1.2 troca o identificador nas chamadas abaixo.

**Back (curves):**
- `CurvaMercadoController`, `ConfiguracaoCurvaController`, `CurvaMercadoAcoesController`: `/{nome}` (arquivos de `docs/curves-v1-2/` já estão assim).
- Controllers do bruto (`BtrsCurvaPrimrController`, `AnbmaCurvaPrimrController`, `BbergCurvaPrimrController`): renomear o path `{codigo}` para `{nome}` em `/{codigo}/primaria-*/...`. O service já busca a curva com `findByNome`; só o nome da variável e o Swagger mudam.
- `CurvaMercadoAcoesService`: recebe o nome, busca a curva e repassa `curva.codigo()` ao engine; curva sem código → 422 (arquivo pronto em `docs/curves-v1-2/09-identificador-nome/`).

**Front (chamadas que passam a usar `item.nome`, com `encodeURIComponent`):**

| Tela | Hoje | Passa a |
|---|---|---|
| Cadastro de curvas | `/cadastro-curvas/{codigo}` | `/cadastro-curvas/{nome}` |
| Cadastro de curvas | `GET /api/v1/curvas-mercado/{codigo}` e `/{codigo}/auditoria` | `/{nome}` e `/{nome}/auditoria` |
| Cadastro de curvas | arquivo `auditoria-{codigo}.json` | `auditoria-{nome}.json` |
| Cadastro e Dados de mercado | link `/curvas?codigo={codigo}` | `/curvas?nome={nome}` |
| Curvas | `/{codigo}/{dataBase}/vertices`, `/interpolacao`, `/construcao` | `/{nome}/{dataBase}/...` |
| Dados de mercado | `/curvas-mercado/{codigo}/primaria-{provedor}/{dataBase}` | `/{nome}/primaria-{provedor}/{dataBase}` |
| Novas da v1.2 | — | `DELETE /{nome}`, `DELETE /{nome}/{dataBase}/vertices`, `DELETE /{nome}/configuracoes?versao=N` |

## Auditoria e qualidade

8. **Auditoria tipada** (`GET /curvas-mercado/{nome}/auditoria`): a planilha continua.
   - O controller deixa de devolver `ResponseEntity<?>`: o JSON é `ResponseEntity<CurvaAuditoria>` e a planilha é outro método, `ResponseEntity<byte[]>`, escolhido por `params = "formato=xlsx"`. O Swagger passa a mostrar os campos da auditoria no JSON.
   - `formato` fora de `json` e `xlsx` (`pdf`, por exemplo) já responde 400 `PARAMETRO_INVALIDO` hoje, e continua assim.
   - **Sem "canônico":** o `ConfiguracaoCanonicoState`, o `CurvaProvedorCanonicoState` e o `toCanonicoState()` (usados na auditoria e no `detalhar`) saem do projeto. Com os parâmetros tipados (`ParametrosCalculo`), o record do domínio já serializa em ordem fixa, então o estado "canônico" não acrescenta nada. `CurvaAuditoria` e `CurvaMercadoDetalhada` passam a levar `CurvaProvedor` e `ConfiguracaoCurva` direto (nome descritivo, nunca "canônico"). Falta ver `CurvaAuditoria`, `CurvaMercadoDetalhada`, `CurvaProvedorCanonicoState`, `ConfiguracaoCanonicoState`, `CurvaAuditoriaExcelGenerator` e os DTOs de resposta que os usam. `CurvaMercadoController.java` (item 5, pasta `05-excluir-curva`) já traz a parte do controller.

9. **Duplicação nos 3 adaptadores do bruto** (Sonar: 88 linhas, em `Anbma`, `Bberg` e `Btrs` `CurvaPrimrPersistenceAdapter`), **se der**: extrair só o que é igual nos três (mapa de tickers do `tCurvaPrvdr` e conversão da linha agregada), sem classe base genérica. Fazer junto com o item 3, que mexe nos três.

## Ordem para apagar tudo de uma curva

Nada some com histórico pendurado: cada exclusão só passa quando o que depende dela já saiu. A sequência é:

1. datas construídas: `DELETE .../{nome}/{dataBase}/vertices` (item 6), uma por data; leva junto a interpolada;
2. dado bruto: `DELETE .../{nome}/primaria-{b3|anbima|bloomberg}/{dataBase}`, uma por data e provedor (rotas que já existem);
3. versões de configuração: `DELETE .../configuracoes?versao=N` (item 7), liberadas porque não há mais construção na vigência delas (esta etapa pode ser pulada: o passo 4 apaga as que restarem);
4. a curva: `DELETE /curvas-mercado/{nome}` (item 5), que leva junto provedores e as configurações que restarem.

Fora dessa ordem, a resposta é 409 dizendo o que ainda falta apagar.

## Decidido (não fazer)

- **`GET /curvas-mercado/provedores`**: fica como está. O front (`fed-cadastro-curvas`) filtra por provedor na própria listagem (`GET /curvas-mercado?provedor=`) e não usa essa rota; ela tem filtros a mais (`produto`, `tickerProvedor`) e não atrapalha.

- **Curva derivada (`TCEN`)**: sem aviso `CURVA_COM_FILHAS` no `inativar` e sem 409 `CURVA_COMPONENTE` no `excluir`; o `findByProvedor` da `CurvaPrvdrRepositoryPort` foi apagado por falta de uso e volta quando a derivada existir.

- **Aviso ao alterar curva com histórico** (`PUT /curvas-mercado/{nome}`): o gestor sabe que as datas já construídas usaram a regra anterior.

- **Descrição "Buscar provedor por id"**: o usuário corrige direto no repositório.

- **Excluir curva com histórico (cascata).** Curva já construída sai pela inativação, ou apagando as datas pelo item 6 e depois excluindo pelo item 5.

- **Delete sem motivo.** Sem login na v0/v1, não há quem assine nem onde guardar. Motivo e usuário entram juntos quando houver auditoria (v2).

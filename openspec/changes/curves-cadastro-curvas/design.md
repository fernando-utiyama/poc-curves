## Context

- O cadastro que o engine lê está descrito na spec `curve-build-pipeline` do change `engine-modelos-curva`: curva em `tCurvaMercd` (código em `cTickerIdtfdUnic`, nome em `cTickerIndcd`, unidade e cotação), origem em `tCurvaPrvdr` (menor `cPriorCsumo`), configuração vigente em `tConfgCurva` (uma por data) e parâmetros em JSON em `tConfgCurva.cModDado`.
- O processor (change `conector-b3-webhook-ingest`) grava os dados brutos sob cada curva ligada em `tCurvaPrvdr` ao código da fonte.
- O schema não pode mudar nesta fase. `tCurvaMercd.cTickerIndcd` é a PK e é referenciada por todas as FKs; `tCurvaPrvdr.cldtfdUnic` é `int NOT NULL` sem identity nem sequência; `tConfgCurva.cldtfdConfg` é identity.
- `tDadoVertcCurva` é a curva construída (os pontos, com dias e fatores) e `tDadoCurva` a curva interpolada (um valor por dia corrido), as duas com PK (`dBaseReft`, `cTickerIndcd`, `dVertcReft`), todas datas puras. `tCurvaData` sai do schema (change `banco-curvas-ajustes`).
- O engine (`engine-modelos-curva`) grava as duas na construção e no recálculo, numa transação que trava a linha da curva em `tCurvaMercd` (até 30 segundos para obter a trava, senão `CONSTRUCAO_EM_ANDAMENTO`). A construção automática (webhook de carga, construção sem recálculo) nunca sobrescreve uma data que já tem pontos.
- O `hashPontos` (SHA-256 das linhas `data;valor`) é definido na spec `curve-build-pipeline` do engine.
- O calendário de feriados, inclusive os calendários Groovy, está no engine, que o expõe por `GET /api/v1/calendarios/{nome}` (spec `calendar-management`).
- `services/curves` já está transcrito do sistema real (commit `b282670`): CRUD de provedores do outro dev, CRUD de `tBbergCurvaPrimr` e `CurvaMercdEntity`, com `GlobalExceptionHandler`/`ApiErrorResponse` e `BeanConfig`. Esta change acrescenta a ele, sem mudar a estrutura.

## Goals / Non-Goals

**Goals:**
- Criar e manter tudo o que o engine e o processor precisam ler, sem SQL manual.
- Nunca deixar o cadastro num estado que o engine não consiga ler: uma configuração vigente por data, parâmetros válidos, versões passadas intactas.
- Edição em lote segura por planilha, do cadastro e dos pontos.
- O gestor corrige ou digita pontos à mão, sem ser bloqueado, com preferência sobre o engine, e acompanha as curvas num painel.
- A edição nunca depende do engine: os dois compartilham o banco, e a única chamada de escrita do curves ao engine (regravar a curva interpolada depois de uma edição) não bloqueia a edição.

**Non-Goals:**
- CRUD de provedores (outro dev).
- Construção, recálculo e interpolação: são do engine.
- Auditoria da edição manual dos pontos: é contingência.
- Mudança de schema, tópico novo ou tabela nova.

## Decisions

### D1. Nome imutável, código alterável
`cTickerIndcd` (nome) é a PK referenciada por `tCurvaPrvdr`, `tConfgCurva`, `tDadoVertcCurva`, `tDadoCurva` e todas as tabelas brutas. Renomear exigiria atualizar todas as FKs, então o nome é imutável. O código (`cTickerIdtfdUnic`) é só identificador de rota e pode mudar. Como o schema não garante unicidade do código, o serviço garante (código único, nome único depois de normalizado), o que evita o `CODIGO_DUPLICADO` e o `NOME_AMBIGUO` do engine.

### D2. Sem exclusão física de curva
A curva pode ter pontos, brutos e configurações dependentes. Inativar (`cSitReg`) preserva o histórico; excluir fisicamente fica fora do serviço.

### D3. Configuração só por versão nova, vigente a partir de hoje
Alterar uma versão que já começou mudaria o resultado de um reprocessamento de data antiga, e o engine depende da vigência para ser reprodutível. Por isso versões não se alteram: cria-se uma nova, com início hoje ou depois, e o serviço fecha a anterior na mesma transação. A primeira versão pode começar no passado, porque ainda não existe construção feita com outra configuração. Excluir só vale para a última versão ainda não iniciada. O resultado é sempre uma sequência contínua, com uma e só uma configuração por data.

### D4. Validação de parâmetros igual à do engine, com modelos como aviso
Os parâmetros seguem a tabela da spec do engine (chaves, tipos, valores e combinações), para que o engine não encontre `CADASTRO_INVALIDO` depois. Os nomes de modelo não são bloqueados: um script Groovy pode criar um modelo novo sem deploy, e só o engine sabe o que está ativo. Nome fora dos nativos gera o aviso `MODELO_NAO_NATIVO`, e a simulação do engine confirma antes da produção.

### D5. `idLigacao` sem sequência
`tCurvaPrvdr.cldtfdUnic` não tem geração automática, e o schema não pode mudar. O serviço lê `MAX + 1` com `UPDLOCK, HOLDLOCK` na mesma transação da inserção, o que serializa inserções simultâneas. Uma sequência no banco é o alvo ideal quando o schema puder mudar.

### D6. Concorrência otimista por curva
Curva, ligações e configurações são editadas juntas, muitas vezes por pessoas diferentes. O `ETag` da curva cobre os três, e toda alteração exige `If-Match`. É um hash do conteúdo, e não o `dUltAtulz`: o `datetime` do SQL Server tem precisão de cerca de 3 ms, e o hash deixa de fora os campos que o engine grava (`dBaseReft`, `cUsuarCalc`), para uma construção não invalidar a edição de ninguém. A planilha guarda esse `ETag` na coluna `Controle`, então uma importação de planilha antiga não sobrescreve uma alteração feita depois da exportação.

### D7. Planilha como estado desejado, com simulação
A importação trata cada curva listada como estado completo desejado (curva, ligações e configurações), e compara com o banco:
- exportar e importar sem editar dá zero mudanças;
- apagar uma linha de ligação a exclui;
- editar uma versão existente é erro, porque versões não se alteram.

A simulação devolve a própria planilha marcada linha a linha, e a aplicação é uma transação única: ou todo o lote entra, ou nada entra.

**Alternativa rejeitada:** coluna de ação por linha (incluir, alterar, excluir). É mais fácil de errar, e uma planilha exportada e reimportada não seria automaticamente neutra.

### D8. Auditoria sem Blob e sem tabela: log e arquivo montado na hora
O Blob guarda só os originais dos feeders e os scripts Groovy, e o banco não pode mudar. Cada alteração do cadastro sai no log (`CADASTRO_ALTERADO`), com estado anterior e novo, e sempre com o nome, que é imutável, para o histórico sobreviver a uma troca de código. O front pede o arquivo de auditoria do cadastro de uma curva, que o serviço monta na hora com o estado atual completo, inclusive quem fez a última alteração (`cUsuarAtulz`, `dUltAtulz`). **Alvo ideal, quando o banco puder mudar:** tabela de histórico do cadastro, consultável pela API.

### D9. Avisos de coerência com engine e processor, sem bloquear
O serviço sinaliza curva sem ligação, origem incompatível com o modelo de construção nativo e modelo não nativo, mas não bloqueia, porque são estados válidos durante uma configuração em etapas. O engine e o processor continuam sendo quem rejeita no uso.

### D10. Efeito do cadastro no engine
Inativar a curva, ou deixar a data-base fora da vigência dela (`dInicVgcia`..`dValidAte`), faz a construção automática (carga e construção da data pelo orquestrador) não construir a curva naquela data. O usuário ainda pode construí-la pela rota de construção do engine, que responde com aviso. Os pontos já gravados continuam consultáveis. A edição manual dos pontos está em D13 a D18.

### D11. Valores aceitos vêm do engine
Quem sabe o que é aceito no cálculo é o engine, e os modelos crescem com scripts Groovy sem deploy. Por isso o engine expõe os valores aceitos (`GET /api/v1/valores-cadastro`), gerados dos próprios enums do validador, e o curves os repassa em `GET /api/v1/curvas-mercado/valores`, acrescidos dos provedores. O front, a aba `Valores` e as listas suspensas da planilha usam só essa rota. O Swagger declara os `enum` fixos para quem integra por API. Com o engine fora, o curves responde com a cópia embutida e aviso, e um teste de contrato impede que essa cópia divirja do engine.

**Alternativa rejeitada:** só `enum` no Swagger. Não mostra os modelos Groovy ativos nem as regras de combinação, e o front acabaria repetindo as listas.

### D12. Painel no curves, conferência na hora pelo engine
O painel é tela do gestor, e o gestor trabalha no curves (cadastro e pontos). O curves já sabe o cadastro, a última data publicada e os pontos gravados; falta o que só o engine sabe calcular: se há insumo na data, se o modelo roda, e se os pontos gravados batem com o que a fonte atual produz. O engine calcula isso na hora, numa rota só de leitura, sem guardar nada, e o curves compõe a situação. A comparação com a fonte atual revela de uma vez a edição manual, a republicação sem recálculo, a mudança de cadastro e, nas derivadas, a curva componente recalculada, sem nenhum registro guardado. Com o engine fora, o painel mostra o que o curves tem, com aviso, e nunca falha.

O atraso vale só para data-base passada: dia útil anterior sem carga ou sem construção. Na data de hoje a carga ainda pode chegar, e o serviço não guarda horário de publicação por provedor (seria configuração a manter e fonte de falso alarme). **Alternativas rejeitadas:** horário esperado por provedor em configuração, para marcar atraso no próprio dia (falso alarme quando a fonte atrasa pouco, e mais uma configuração por ambiente); painel no engine (misturaria tela de gestão com cálculo e exigiria que o engine lesse o cadastro para a tela); guardar a última tentativa de cada curva (exigiria Blob ou tabela).

### D13. Pontos: edição no curves, construção no engine
O engine fica com o que depende de modelo (construção, recálculo, interpolação, simulação), e o curves com o que o gestor faz à mão (cadastro e pontos). Os dois gravam os pontos em `tDadoVertcCurva`, então compartilham pelo banco a trava por curva e a fórmula do `hashPontos`. A curva interpolada (`tDadoCurva`) é só do engine: depois de cada edição, o curves chama a rota de regravação dela, e a edição nunca depende dessa chamada (sem resposta, a interpolada fica desatualizada, com aviso).

**Alternativa rejeitada:** o curves delegar a gravação ao engine. Tornaria a contingência dependente do engine no ar, o que contraria a razão de existir da edição manual.

### D14. Preferência sem coordenação
A preferência da edição manual resulta de regras que já existem:
- **Trava no banco:** a edição espera a transação curta do engine e grava por cima.
- **Construção automática nunca sobrescreve:** a regra do engine é "curva com pontos → `EXISTENTE`".
- **Recálculo forçado:** só por ação explícita de um usuário.

Não há `If-Match`: numa contingência, o gestor que salva vence, e o `hashPontos` anterior fica no log.

### D15. Só a consistência do banco barra a edição; regra de negócio é aviso
A edição manual é o caminho do gestor para contornar qualquer problema, inclusive de cadastro (um feriado errado, uma configuração ausente). Por isso o serviço só recusa o que não pode ser gravado de forma consistente em `tDadoVertcCurva`: lista vazia, data ou valor ausente ou malformado, data repetida (PK) e valor que não cabe em `DECIMAL(28,12)`. Toda regra de negócio vira aviso e o ponto é gravado: data igual ou anterior à data-base, fim de semana, feriado, preço ou pontos não positivos, sem configuração vigente, calendário não conferido. O engine trata esses pontos na interpolação (descarte de prazo não positivo e de ponto no mesmo prazo, com aviso), e o gestor vê os avisos na hora de salvar.

Pelo mesmo motivo, nenhuma dependência bloqueia: sem configuração vigente, grava sem arredondar; com o engine fora, grava sem conferir feriados.

### D16. Feriados pela exportação de calendário do engine
Um ponto num feriado tem o mesmo número de dias úteis do dia útil anterior, e na interpolação o engine fica só com o de menor data. Pela D15, o curves não recusa o ponto (o erro pode estar no cadastro de feriados); grava com o aviso `PONTO_EM_FERIADO` para o gestor conferir. O calendário (com os scripts Groovy) está no engine, então o curves lê os feriados pela rota de exportação, só leitura, com tempo limite curto. Se isso falhar (engine fora), o ponto é gravado com o aviso `CALENDARIO_NAO_VERIFICADO`. Nos dois casos, o engine trata o ponto na interpolação: fica o de menor data no mesmo prazo, com o aviso `PONTO_DESCARTADO_MESMO_PRAZO`.

### D17. Planilha como estado desejado por data-base
A importação segue o padrão da planilha de cadastro: cada par (curva, data-base) presente é a lista completa. Exportar e reimportar sem editar dá zero mudanças. A simulação marca ponto a ponto, e a aplicação é atômica. As travas são tomadas em ordem alfabética de nome, para não haver deadlock entre importações nem com o engine.

### D18. O que o gestor envia é exatamente o que fica
A edição manual existe para o gestor ter controle total, então o resultado não pode surpreender: para cada curva e data-base enviada, os pontos gravados ficam exatamente iguais à lista, inclusive com exclusão dos ausentes. A gravação é pela diferença (só as linhas que mudaram), o que diminui o tempo de trava e deixa o log só com mudanças reais, e termina relendo os pontos e conferindo o `hashPontos` contra o da lista antes do commit. Qualquer diferença desfaz tudo. A única transformação é o arredondamento pela configuração vigente, porque é assim que o engine grava e usa os pontos, e ela é avisada (`VALOR_ARREDONDADO`) e mostrada na simulação, linha a linha, em `ValorGravado`.

Ao gravar ou apagar pontos, o serviço regrava em `tDadoVertcCurva` só a linha dos pontos novos, alterados ou excluídos (D36 e D39 do change `engine-modelos-curva`), sem calcular fatores: os pontos que não mudaram mantêm os dias úteis publicados pela fonte e os fatores do engine, e a edição continua sem depender do engine.

**Alternativa rejeitada:** apagar e inserir tudo a cada gravação. Chega ao mesmo estado, mas reescreve pontos que não mudaram, segura a trava por mais tempo e gera log de edição sem edição.

### D19. Curvas derivadas pelo mesmo cadastro de ligações
Uma curva derivada de outras (ex.: inflação implícita = PRE sobre a NTN-B bootstrapada) liga-se às curvas componentes em `tCurvaPrvdr` pelo provedor interno `TCEN`, com o nome da curva componente no código na fonte e o papel no produto, como definido no engine (D34 do change `engine-modelos-curva`). O cadastro recusa componente inexistente e ciclo, porque um ciclo deixaria as curvas sem ordem de construção; inativar uma curva componente só avisa. O painel mostra a derivada `AGUARDANDO_COMPONENTES` enquanto falta componente e `DIVERGENTE_DA_FONTE` quando uma curva componente mudou depois da construção. Nenhum modelo derivado é construído nesta fase: a estrutura fica pronta para ele.

### Origens secundárias e modelo por origem
As ligações de prioridade maior são fontes de reserva. O curves não constrói nada: guarda as ligações e, na configuração, a chave opcional `MODELOS_POR_ORIGEM`, que diz ao engine qual modelo lê cada reserva (change `engine-modelos-curva`, D38). Chave sem ligação correspondente é só aviso (`MODELO_POR_ORIGEM_SEM_LIGACAO`), porque o engine ignora a entrada e a curva continua construindo pela principal; recusar obrigaria a criar uma versão nova de configuração só para excluir uma ligação. O painel lista as reservas de cada curva para o front oferecer a escolha na construção.

### Dias úteis informados pelo gestor
Os dias úteis que vêm da fonte ou do usuário são obedecidos pelo engine (change `engine-modelos-curva`, D39). A edição manual e a planilha de pontos aceitam `diasUteis` opcional por ponto; o curves grava a linha do ponto em `tDadoVertcCurva` com esses dias, os dias corridos e 30/360 (contas de data, sem calendário) e fatores nulos, porque fatores são do engine. Os pontos que não mudaram mantêm a linha do engine, com os dias publicados pela fonte; por isso a consulta e a exportação devolvem os dias úteis, e reenviá-los sem mudança não altera nada. Dias informados diferentes do calendário, incoerentes ou fora de ordem são avisos, nunca recusa.

### D20. Dado bruto da B3 mantido à mão, sem disparar o engine
Quando a carga da B3 falha ou traz um vértice errado, o gestor corrige o bruto (`tBtrsCurvaPrimr`) em vez dos pontos, e a curva sai pelo modelo `PRONTA_TS_B3` como sempre (memória de cálculo, dias úteis publicados, curvas que usam o mesmo código). O front abre uma listagem geral (curva, data-base, quantidade de linhas, código na fonte, curva construída ou não), feita por uma consulta agregada, e o gestor seleciona uma curva e data para o CRUD linha a linha.
- **Só grava o bruto:** nada é disparado no engine. A data ainda não construída sai na próxima construção (manual ou pelo orquestrador); a já construída só muda num recálculo forçado, e a resposta avisa `CURVA_JA_CONSTRUIDA`.
- **Mesmas regras da edição de pontos (D14, D15):** trava da curva em `tCurvaMercd` (o engine lê o bruto sob ela, então nunca constrói com a edição pela metade), sem `If-Match`, recusa só do que não cabe nas colunas ou falta para ser um vértice; o que faria o modelo falhar (`INSUMO_INVALIDO`) vira aviso, com o efeito descrito.
- **`cldtfdUnic` como o processor** (`MAX + 1` com `UPDLOCK, HOLDLOCK`), para os dois não colidirem.
- **Sem arredondamento:** é o dado da fonte, gravado como enviado.
- **Reprocessamento vence:** o processor apaga e insere as linhas da curva na data; a edição fica no log `CURVA_PRIMARIA_EDITADA`.

**Alternativa rejeitada:** substituir a lista completa da data, como nos pontos. O gestor quer corrigir um vértice entre 278 sem reenviar tudo, e a listagem mais o CRUD por linha é o que a tela pede.

## Risks / Trade-offs

- **Regras de parâmetros duplicadas entre curves e engine.** → A spec do engine é a fonte; os testes do curves usam os mesmos casos da tabela. Uma chave nova no engine exige atualizar os dois.
- **`MAX + 1` com trava serializa inserções de ligações.** → O volume é baixo (dezenas de ligações), e a trava dura só a transação.
- **Importação grande trava muitas linhas numa transação.** → Limite de 1.000 curvas e 5 MB; a simulação roda sem trava.
- **Painel depende do engine para a conferência com a fonte, calculada a cada consulta.** → Com o engine fora, mostra cadastro, última data publicada e pontos, com `SITUACAO_INDISPONIVEL` e aviso; uma chamada por consulta, com tempo limite de 60 segundos; o engine confere as curvas em paralelo.
- **Sem histórico do cadastro consultável pela API.** → Os eventos `CADASTRO_ALTERADO` no log têm o histórico completo; o arquivo de auditoria mostra o estado atual e a última alteração; a tabela de histórico entra quando o banco puder mudar.
- **Recálculo forçado apaga pontos manuais.** → É ação explícita de um usuário; o log `PONTOS_EDITADOS` da edição anterior e o evento `CURVA_GRAVADA` do recálculo no engine (`pontosAnteriores`) preservam o que existia.
- **Ponto fora das regras de negócio gravado.** → Aviso na gravação; o engine continua interpolando, descartando com aviso o ponto de prazo não positivo ou repetido no mesmo prazo; o gestor corrige depois.
- **Preço ou pontos não positivos gravados.** → Aviso `VALOR_NAO_POSITIVO`; a interpolação `LogLinear` dessa data falha no engine com `PONTOS_NAO_INTERPOLAVEIS` até a correção, mas a consulta dos pontos continua funcionando.
- **Importação grande de pontos trava muitas curvas.** → Limite de 100.000 pontos; travas em ordem fixa; a simulação não trava.
- **Fórmula do `hashPontos` em dois serviços.** → Forma canônica do valor definida na spec do engine (sem zeros à direita, independente da escala do banco) e um vetor de teste comum aos dois.
- **Bruto editado à mão e depois reprocessado.** → O processor substitui as linhas da data inteira; a edição anterior fica no log `CURVA_PRIMARIA_EDITADA` com a linha antes e depois.
- **Bruto corrigido numa curva já construída não muda nada sozinho.** → Aviso `CURVA_JA_CONSTRUIDA` na resposta; o gestor pede o recálculo forçado no engine.
- **Serviço ainda não transcrito.** → A change define comportamento, não estrutura; a implementação se encaixa no código real quando ele existir.

## Migration Plan

1. Entra ID: criar o papel `Curvas.Cadastro` e atribuí-lo a quem cadastra; atribuir `Curvas.Leitura` e `Curvas.Operador` do engine à identidade gerenciada do curves (o `Curvas.Operador` só para regravar a curva interpolada depois de uma edição manual), que chama o engine para os valores aceitos, a situação do painel e os calendários.
2. Log: retenção dos eventos `CADASTRO_ALTERADO`, `PONTOS_EDITADOS` e `CURVA_PRIMARIA_EDITADA` definida pela área de risco. O serviço não usa o Blob.
3. Deploy do serviço junto com o engine sem a edição de pontos. O banco começa vazio: o cadastro entra pela API ou pela planilha (ex.: `exemplo-cadastro-7-curvas.txt`). Linhas de `tCurvaMercd` sem código, se existirem, ficam invisíveis nas rotas.
4. **Rollback:** voltar os deploys do curves e do engine; os dados gravados continuam válidos para o engine.

## Open Questions

- Com o CRUD de provedores (outro dev): cadastrar o provedor interno `TCEN`, usado pelas curvas derivadas; os identificadores dos provedores (`iPrvdrDados`, o `nomeProvedor` da `ProvedorEntity` do CRUD de provedores) precisam ser exatamente `B3`, `ANBIMA` e `BLOOMBERG`, que o engine e o processor usam. O produto da ligação (`tCurvaPrvdr.cPrvdrMercd`) não é conferido contra `tPrvdrDadoMercd.cProdt`, porque a tabela tem uma linha por provedor e uma fonte pode ter vários produtos.

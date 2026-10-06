## Context

**Esta change é de conferência: boa parte do curves já foi desenvolvida no repositório real**, muitas vezes com outro nome (por exemplo, `Ponto` ou `Linha` no lugar de `Vertice`, ou em inglês). Esta change já foi aplicada no repositório real numa versão anterior, antes de ser separada em partes e renomeada; por isso há muita coisa feita, com nomes antigos e com peças que hoje estão em outra change (ex.: cliente do engine, vértices manuais, `/primaria-b3`) ou saíram. Cada tarefa SHALL seguir este roteiro:
1. **Conferir:** procurar pela **funcionalidade** (o que faz, que tabela lê ou grava, que rota expõe), nunca só pelo nome da spec.
2. Decidir uma de três saídas:
   - **já existe e cumpre a spec → deixar como está** (não reescrever, não "melhorar"; só o nome segue o passo 3);
   - **existe e diverge → alterar** só o que diverge, no código que já existe;
   - **não existe → criar**, com os nomes desta change.
3. Nunca criar uma segunda versão do que já está pronto. **Nome diferente do desta change é divergência:** renomeie para o nome da change (classe, arquivo, método, campo interno e todas as referências, até compilar), menos o que outro serviço ou o banco já usa (rota exposta, campo de JSON de resposta, coluna, tópico): isso fica como está e vai para a anotação como `nome-real → nome-da-change`.
4. Ao marcar a tarefa, anotar ao lado a saída e o arquivo: `[conferido: pronto | alterado | renomeado de X | criado] caminho/Arquivo.java`. Na dúvida se é a mesma coisa, `// TODO(revisao): <dúvida>` e siga.
5. Código que a spec atual não pede (sobra da versão anterior): não apagar; anotar como `[sobra] caminho/Arquivo.java` no resumo, para a revisão decidir.


- O cadastro que o engine lê está descrito na spec `curve-build-pipeline` do change `engine-construcao-curvas`: curva em `tCurvaMercd` (código em `cTickerIdtfdUnic`, nome em `cTickerIndcd`, unidade e cotação), origem em `tCurvaPrvdr` (menor `cPriorCsumo`), configuração vigente em `tConfgCurva` (uma por data) e parâmetros em JSON em `tConfgCurva.cModDado`.
- O processor (change `processor-carga-b3`) grava os dados brutos sob cada curva ligada em `tCurvaPrvdr` ao código da fonte.
- O schema não pode mudar nesta fase. `tCurvaMercd.cTickerIndcd` é a PK e é referenciada por todas as FKs; `tCurvaPrvdr.cIdtfdUnic` é `int NOT NULL` sem identity nem sequência; `tConfgCurva.cIdtfdConfg` é identity.
- `tDadoVertcCurva` é a curva construída (os vértices, com dias e fatores) e `tDadoCurva` a curva interpolada (um valor por dia corrido), as duas com PK (`dBaseReft`, `cTickerIndcd`, `dVertcReft`), todas datas puras. `tCurvaData` sai do schema (change `banco-curvas-ajustes`).
- O engine (`engine-construcao-curvas`) grava as duas na construção e no recálculo, numa transação que trava a linha da curva em `tCurvaMercd` (até 30 segundos para obter a trava, senão `CONSTRUCAO_EM_ANDAMENTO`). A construção automática (webhook de carga, construção sem recálculo) nunca sobrescreve uma data que já tem vértices.
- O `hashPontos` (SHA-256 das linhas `data;valor`) é definido na spec `curve-build-pipeline` do engine.
- O calendário de feriados, inclusive os calendários Groovy, está no engine, que o expõe por `GET /api/v1/calendarios/{nome}` (spec `calendar-management`).
- O serviço de curvas já existe (no poc, `services/curves`, transcrito do real): CRUD de provedores do outro dev, CRUD de `tBbergCurvaPrimr` e `CurvaMercdEntity`, com o tratamento de erro e o registro de beans próprios. Esta change acrescenta a ele, sem mudar a estrutura; os detalhes de implementação seguem o código real.

## Goals / Non-Goals

**Goals:**
- Criar e manter tudo o que o engine e o processor precisam ler, sem SQL manual.
- Nunca deixar o cadastro num estado que o engine não consiga ler: uma configuração vigente por data, parâmetros válidos, versões passadas intactas.
- Edição em lote segura por planilha, do cadastro e dos vértices.
- O gestor corrige ou digita vértices à mão, sem ser bloqueado, com preferência sobre o engine, e acompanha as curvas num painel.
- A edição nunca depende do engine: os dois compartilham o banco, e a única chamada de escrita do curves ao engine (regravar a curva interpolada depois de uma edição, na segunda parte) não bloqueia a edição. A primeira parte não chama o engine em lugar nenhum.

**Non-Goals:**
- CRUD de provedores (outro dev).
- Construção, recálculo e interpolação: são do engine.
- Auditoria da edição manual dos vértices: é contingência.
- Mudança de schema, tópico novo ou tabela nova.

## Decisions

### D1. Nome imutável, código alterável
`cTickerIndcd` (nome) é a PK referenciada por `tCurvaPrvdr`, `tConfgCurva`, `tDadoVertcCurva`, `tDadoCurva` e todas as tabelas brutas. Renomear exigiria atualizar todas as FKs, então o nome é imutável. O código (`cTickerIdtfdUnic`) é só identificador de rota e pode mudar. Como o schema não garante unicidade do código, o serviço garante (código único, nome único depois de normalizado), o que evita o `CODIGO_DUPLICADO` e o `NOME_AMBIGUO` do engine.

### D2. Sem exclusão física de curva
A curva pode ter vértices, brutos e configurações dependentes. Inativar (`cSitReg`) preserva o histórico; excluir fisicamente fica fora do serviço.

### D3. Configuração só por versão nova, vigente a partir de hoje
Alterar uma versão que já começou mudaria o resultado de um reprocessamento de data antiga, e o engine depende da vigência para ser reprodutível. Por isso versões não se alteram: cria-se uma nova, com início hoje ou depois, e o serviço fecha a anterior na mesma transação. A primeira versão pode começar no passado, porque ainda não existe construção feita com outra configuração. Excluir só vale para a última versão ainda não iniciada. O resultado é sempre uma sequência contínua, com uma e só uma configuração por data.

### D4. Validação de parâmetros igual à do engine, com modelos como aviso
Os parâmetros seguem a tabela da spec do engine (chaves, tipos, valores e combinações), para que o engine não encontre `CADASTRO_INVALIDO` depois. Os nomes de modelo não são bloqueados: um script Groovy pode criar um modelo novo sem deploy, e só o engine sabe o que está ativo. Nome fora dos nativos gera o aviso `MODELO_NAO_NATIVO`, e a simulação do engine confirma antes da produção.

### D5. `idCurvaProvedor` sem sequência
`tCurvaPrvdr.cIdtfdUnic` não tem geração automática, e o schema não pode mudar. O serviço lê `MAX + 1` com `UPDLOCK, HOLDLOCK` na mesma transação da inserção, o que serializa inserções simultâneas. Uma sequência no banco é o alvo ideal quando o schema puder mudar.

### D6. Sem controle de concorrência no cadastro
Curva, provedores da curva e configurações são editados por poucas pessoas (cerca de três), então o cadastro não faz controle de versão: quem salva por último vence, e o estado anterior de cada alteração fica no log `CADASTRO_ALTERADO` (D8), de onde se recupera. A numeração das decisões não muda.

### D7. Planilha como estado desejado, com simulação
A importação trata cada curva listada como estado completo desejado (curva, provedores da curva e configurações), e compara com o banco:
- exportar e importar sem editar dá zero mudanças;
- apagar uma linha de provedor da curva a exclui;
- editar uma versão existente é erro, porque versões não se alteram.

A simulação devolve a própria planilha marcada linha a linha, e a aplicação é uma transação única: ou todo o lote entra, ou nada entra.

**Alternativa rejeitada:** coluna de ação por linha (incluir, alterar, excluir). É mais fácil de errar, e uma planilha exportada e reimportada não seria automaticamente neutra.

### D8. Auditoria sem Blob e sem tabela: log e arquivo montado na hora
O Blob guarda só os originais dos feeders e os scripts Groovy, e o banco não pode mudar. Cada alteração do cadastro sai no log (`CADASTRO_ALTERADO`), com estado anterior e novo, e sempre com o nome, que é imutável, para o histórico sobreviver a uma troca de código. O front pede o arquivo de auditoria do cadastro de uma curva, que o serviço monta na hora com o estado atual completo, inclusive quem fez a última alteração (`cUsuarAtulz`, `dUltAtulz`). **Alvo ideal, quando o banco puder mudar:** tabela de histórico do cadastro, consultável pela API.

### D9. Avisos de coerência com engine e processor, sem bloquear
O serviço sinaliza curva sem provedor, origem incompatível com o modelo de construção nativo e modelo não nativo, mas não bloqueia, porque são estados válidos durante uma configuração em etapas. O engine e o processor continuam sendo quem rejeita no uso.

### D10. Efeito do cadastro no engine
Inativar a curva, ou deixar a data-base fora da vigência dela (`dInicVgcia`..`dValidAte`), faz a construção automática (carga e construção da data) não construir a curva naquela data. O usuário ainda pode construí-la pela rota de construção do engine, que responde com aviso. Os vértices já gravados continuam consultáveis. A edição manual dos vértices está em D13 a D18.

### D11. Valores aceitos: tabela embutida na primeira parte, engine na segunda
O curves já precisa ter a tabela de parâmetros aceitos para validar o `cModDado` (`ValidadorParametros`, cópia da regra do engine). Na primeira parte, `GET /api/v1/curvas-mercado/valores` serve essa tabela, com os modelos nativos, mais os provedores de `tPrvdrDadoMercd` e os enums e catálogos do serviço; não chama o engine, não tem cache e responde com o engine fora do ar. O front, a aba `Valores` e as listas suspensas da planilha usam só essa rota. O Swagger declara os `enum` fixos para quem integra por API. Só o engine sabe dos modelos Groovy ativos, que crescem sem deploy, e eles só existem na segunda parte do engine. Por isso a consulta ao engine (`GET /api/v1/valores-cadastro`, com cache e tempo limite curto), o aviso `VALORES_SEM_ENGINE` e o teste de contrato que impede a tabela embutida de divergir do engine entram com o cliente HTTP do engine na change `curves-operacao-curvas`.

**Alternativa rejeitada:** só `enum` no Swagger. Não mostra os modelos Groovy ativos nem as regras de combinação, e o front acabaria repetindo as listas.

### D12. Painel no curves, conferência na hora pelo engine
O painel é tela do gestor, e o gestor trabalha no curves (cadastro e vértices). O curves já sabe o cadastro, a última data publicada e os vértices gravados; falta o que só o engine sabe calcular: se há insumo na data, se o modelo roda, e se os vértices gravados batem com o que a fonte atual produz. O engine calcula isso na hora, numa rota só de leitura, sem guardar nada, e o curves compõe a situação. A comparação com a fonte atual revela de uma vez a edição manual, a republicação sem recálculo, a mudança de cadastro e, nas derivadas, a curva componente recalculada, sem nenhum registro guardado. Com o engine fora, o painel mostra o que o curves tem, com aviso, e nunca falha. O painel é da segunda parte (`curves-operacao-curvas`); a primeira parte não chama o engine.

O atraso vale só para data-base passada: dia útil anterior sem carga ou sem construção. Na data de hoje a carga ainda pode chegar, e o serviço não guarda horário de publicação por provedor (seria configuração a manter e fonte de falso alarme). **Alternativas rejeitadas:** horário esperado por provedor em configuração, para marcar atraso no próprio dia (falso alarme quando a fonte atrasa pouco, e mais uma configuração por ambiente); painel no engine (misturaria tela de gestão com cálculo e exigiria que o engine lesse o cadastro para a tela); guardar a última tentativa de cada curva (exigiria Blob ou tabela).

### D13. Vértices: edição no curves, construção no engine
O engine fica com o que depende de modelo (construção, recálculo, interpolação, simulação), e o curves com o que o gestor faz à mão (cadastro e vértices). Os dois gravam os vértices em `tDadoVertcCurva`, então compartilham pelo banco a trava por curva e a fórmula do `hashPontos`. A curva interpolada (`tDadoCurva`) é só do engine: depois de cada edição, o curves chama a rota de regravação dela, e a edição nunca depende dessa chamada (sem resposta, a interpolada fica desatualizada, com aviso).

**Alternativa rejeitada:** o curves delegar a gravação ao engine. Tornaria a contingência dependente do engine no ar, o que contraria a razão de existir da edição manual.

### D14. Preferência sem coordenação
A preferência da edição manual resulta de regras que já existem:
- **Trava no banco:** a edição espera a transação curta do engine e grava por cima.
- **Construção automática nunca sobrescreve:** a regra do engine é "curva com vértices → `EXISTENTE`".
- **Recálculo forçado:** só por ação explícita de um usuário.

Sem conferência de versão: numa contingência, o gestor que salva vence, e o `hashPontos` anterior fica no log.

### D15. Só a consistência do banco barra a edição; regra de negócio é aviso
A edição manual é o caminho do gestor para contornar qualquer problema, inclusive de cadastro (um feriado errado, uma configuração ausente). Por isso o serviço só recusa o que não pode ser gravado de forma consistente em `tDadoVertcCurva`: lista vazia, data ou valor ausente ou malformado, data repetida (PK) e valor que não cabe em `DECIMAL(28,12)`. Toda regra de negócio vira aviso e o vértice é gravado: data igual ou anterior à data-base, fim de semana, feriado, preço ou vértices não positivos, sem configuração vigente, calendário não conferido. O engine trata esses vértices na interpolação (descarte de prazo não positivo e de vértice no mesmo prazo, com aviso), e o gestor vê os avisos na hora de salvar.

Pelo mesmo motivo, nenhuma dependência bloqueia: sem configuração vigente, grava sem arredondar; com o engine fora, grava sem conferir feriados.

### D16. Feriados pela exportação de calendário do engine
Um vértice num feriado tem o mesmo número de dias úteis do dia útil anterior, e na interpolação o engine fica só com o de menor data. Pela D15, o curves não recusa o vértice (o erro pode estar no cadastro de feriados); grava com o aviso `PONTO_EM_FERIADO` para o gestor conferir. O calendário (com os scripts Groovy) está no engine, então o curves lê os feriados pela rota de exportação, só leitura, com tempo limite curto. Se isso falhar (engine fora), o vértice é gravado com o aviso `CALENDARIO_NAO_VERIFICADO`. Nos dois casos, o engine trata o vértice na interpolação: fica o de menor data no mesmo prazo, com o aviso `PONTO_DESCARTADO_MESMO_PRAZO`.

### D17. Planilha como estado desejado por data-base
A importação segue o padrão da planilha de cadastro: cada par (curva, data-base) presente é a lista completa. Exportar e reimportar sem editar dá zero mudanças. A simulação marca vértice a vértice, e a aplicação é atômica. As travas são tomadas em ordem alfabética de nome, para não haver deadlock entre importações nem com o engine.

### D18. O que o gestor envia é exatamente o que fica
A edição manual existe para o gestor ter controle total, então o resultado não pode surpreender: para cada curva e data-base enviada, os vértices gravados ficam exatamente iguais à lista, inclusive com exclusão dos ausentes. A gravação é pela diferença (só as linhas que mudaram), o que diminui o tempo de trava e deixa o log só com mudanças reais, e termina relendo os vértices e conferindo o `hashPontos` contra o da lista antes do commit. Qualquer diferença desfaz tudo. A única transformação é o arredondamento pela configuração vigente, porque é assim que o engine grava e usa os vértices, e ela é avisada (`VALOR_ARREDONDADO`) e mostrada na simulação, linha a linha, em `ValorGravado`.

Ao gravar ou apagar vértices, o serviço regrava em `tDadoVertcCurva` só a linha dos vértices novos, alterados ou excluídos (D36 e D39 do change `engine-construcao-curvas`), sem calcular fatores: os vértices que não mudaram mantêm os dias úteis publicados pela fonte e os fatores do engine, e a edição continua sem depender do engine.

**Alternativa rejeitada:** apagar e inserir tudo a cada gravação. Chega ao mesmo estado, mas reescreve vértices que não mudaram, segura a trava por mais tempo e gera log de edição sem edição.

### D19. Curvas derivadas pelo mesmo cadastro de provedores da curva
Uma curva derivada de outras (ex.: inflação implícita = PRE sobre a NTN-B bootstrapada) liga-se às curvas componentes em `tCurvaPrvdr` pelo provedor interno `TCEN`, com o nome da curva componente no código na fonte e o papel no produto, como definido no engine (D34 do change `engine-construcao-curvas`). O cadastro recusa componente inexistente e ciclo, porque um ciclo deixaria as curvas sem ordem de construção; inativar uma curva componente só avisa. O painel mostra a derivada `AGUARDANDO_COMPONENTES` enquanto falta componente e `DIVERGENTE_DA_FONTE` quando uma curva componente mudou depois da construção. Nenhum modelo derivado é construído nesta fase: a estrutura fica pronta para ele.

### Origens secundárias e modelo por origem
Os provedores da curva de prioridade maior são fontes de reserva. O curves não constrói nada: guarda os provedores da curva e, na configuração, a chave opcional `MODELOS_POR_ORIGEM`, que diz ao engine qual modelo lê cada reserva (change `engine-construcao-curvas`, D38). Chave sem provedor correspondente é só aviso (`MODELO_POR_ORIGEM_SEM_PROVEDOR`), porque o engine ignora a entrada e a curva continua construindo pela principal; recusar obrigaria a criar uma versão nova de configuração só para excluir um provedor da curva. O painel lista as reservas de cada curva para o front oferecer a escolha na construção.

### Dias úteis informados pelo gestor
Os dias úteis que vêm da fonte ou do usuário são obedecidos pelo engine (change `engine-construcao-curvas`, D39). A edição manual e a planilha de vértices aceitam `diasUteis` opcional por vértice; o curves grava a linha do vértice em `tDadoVertcCurva` com esses dias, os dias corridos e 30/360 (contas de data, sem calendário) e fatores nulos, porque fatores são do engine. Os vértices que não mudaram mantêm a linha do engine, com os dias publicados pela fonte; por isso a consulta e a exportação devolvem os dias úteis, e reenviá-los sem mudança não altera nada. Dias informados diferentes do calendário, incoerentes ou fora de ordem são avisos, nunca recusa.

### D20. Dado bruto da B3 mantido à mão, sem disparar o engine
Quando a carga da B3 falha ou traz um vértice errado, o gestor corrige o bruto (`tBtrsCurvaPrimr`) em vez dos vértices, e a curva sai pelo modelo `PRONTA_TS_B3` como sempre (memória de cálculo, dias úteis publicados, curvas que usam o mesmo código). O front abre uma listagem geral (curva, data-base, quantidade de linhas, código na fonte, curva construída ou não), feita por uma consulta agregada, e o gestor seleciona uma curva e data para o CRUD linha a linha.
- **Só grava o bruto:** nada é disparado no engine. A data ainda não construída sai na próxima construção (por curva ou da data inteira, pedida pelo operador); a já construída só muda num recálculo forçado, e a resposta avisa `CURVA_JA_CONSTRUIDA`.
- **Mesmas regras da edição de vértices (D14, D15):** trava da curva em `tCurvaMercd` (o engine lê o bruto sob ela, então nunca constrói com a edição pela metade), sem conferência de versão, recusa só do que não cabe nas colunas ou falta para ser um vértice; o que faria o modelo falhar (`INSUMO_INVALIDO`) vira aviso, com o efeito descrito.
- **`cIdtfdUnic` como o processor** (`MAX + 1` com `UPDLOCK, HOLDLOCK`), para os dois não colidirem.
- **Sem arredondamento:** é o dado da fonte, gravado como enviado.
- **Reprocessamento vence:** o processor apaga e insere as linhas da curva na data; a edição fica no log `CURVA_PRIMARIA_EDITADA`.

**Alternativa rejeitada:** substituir a lista completa da data, como nos vértices. O gestor quer corrigir um vértice entre 278 sem reenviar tudo, e a listagem mais o CRUD por linha é o que a tela pede.

## Risks / Trade-offs

- **Regras de parâmetros duplicadas entre curves e engine.** → A spec do engine é a fonte; os testes do curves usam os mesmos casos da tabela. Uma chave nova no engine exige atualizar os dois. Na primeira parte, `GET /valores` serve só a tabela embutida e não há teste de contrato contra o engine; ele entra na change `curves-operacao-curvas`, junto com a consulta ao engine.
- **`MAX + 1` com trava serializa inserções de provedores da curva.** → O volume é baixo (dezenas de provedores da curva), e a trava dura só a transação.
- **Importação grande trava muitas linhas numa transação.** → Limite de 1.000 curvas e 5 MB; a simulação roda sem trava.
- **Painel depende do engine para a conferência com a fonte, calculada a cada consulta.** → Com o engine fora, mostra cadastro, última data publicada e vértices, com `SITUACAO_INDISPONIVEL` e aviso; uma chamada por consulta, com tempo limite de 60 segundos; o engine confere as curvas em paralelo.
- **Sem controle de concorrência no cadastro:** com poucos usuários, o último a salvar vence e o estado anterior fica no log `CADASTRO_ALTERADO`; ETag descrito em Extras do guia.
- **Sem histórico do cadastro consultável pela API.** → Os eventos `CADASTRO_ALTERADO` no log têm o histórico completo; o arquivo de auditoria mostra o estado atual e a última alteração; a tabela de histórico entra quando o banco puder mudar.
- **Recálculo forçado apaga vértices manuais.** → É ação explícita de um usuário; o log `VERTICES_EDITADOS` da edição anterior e o evento `CURVA_GRAVADA` do recálculo no engine (`pontosAnteriores`) preservam o que existia.
- **Vértice fora das regras de negócio gravado.** → Aviso na gravação; o engine continua interpolando, descartando com aviso o vértice de prazo não positivo ou repetido no mesmo prazo; o gestor corrige depois.
- **Preço ou vértices não positivos gravados.** → Aviso `VALOR_NAO_POSITIVO`; a interpolação `LogLinear` dessa data falha no engine com `PONTOS_NAO_INTERPOLAVEIS` até a correção, mas a consulta dos vértices continua funcionando.
- **Importação grande de vértices trava muitas curvas.** → Limite de 100.000 vértices; travas em ordem fixa; a simulação não trava.
- **Fórmula do `hashPontos` em dois serviços.** → Forma canônica do valor definida na spec do engine (sem zeros à direita, independente da escala do banco) e um vetor de teste comum aos dois.
- **Bruto editado à mão e depois reprocessado.** → O processor substitui as linhas da data inteira; a edição anterior fica no log `CURVA_PRIMARIA_EDITADA` com a linha antes e depois.
- **Bruto corrigido numa curva já construída não muda nada sozinho.** → Aviso `CURVA_JA_CONSTRUIDA` na resposta; o gestor pede o recálculo forçado no engine.

## Migration Plan

1. Entra ID (quando a autenticação entrar, numa change própria): criar o papel `Curvas.Cadastro` e atribuí-lo a quem cadastra. O engine não exige autenticação: na segunda parte, o curves chama o engine sem token para os modelos Groovy dos valores aceitos, a situação do painel, os calendários e a regravação da curva interpolada (a primeira parte não chama o engine).
2. Log: retenção dos eventos `CADASTRO_ALTERADO`, `VERTICES_EDITADOS` e `CURVA_PRIMARIA_EDITADA` definida pela área de risco. O serviço não usa o Blob.
3. Deploy do serviço junto com o engine sem a edição de vértices. O banco começa vazio: o cadastro entra pela API ou pela planilha (ex.: `exemplo-cadastro-7-curvas.txt`). Linhas de `tCurvaMercd` sem código, se existirem, ficam invisíveis nas rotas.
4. **Rollback:** voltar os deploys do curves e do engine; os dados gravados continuam válidos para o engine.

## Open Questions

- Com o CRUD de provedores (outro dev): cadastrar o provedor interno `TCEN`, usado pelas curvas derivadas; os identificadores dos provedores (`iPrvdrDados`, o `nomeProvedor` da `ProvedorEntity` do CRUD de provedores) precisam ser exatamente `B3`, `ANBIMA` e `BLOOMBERG`, que o engine e o processor usam. O produto do provedor da curva (`tCurvaPrvdr.cPrvdrMercd`) não é conferido contra `tPrvdrDadoMercd.cProdt`, porque a tabela tem uma linha por provedor e uma fonte pode ter vários produtos.

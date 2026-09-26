## Context

- `tDadoCurva` tem PK (`dBaseReft`, `cTickerIndcd`, `dVertcReft`), todas datas puras: um conjunto de pontos por curva e data-base.
- O engine (`engine-modelos-curva`) grava `tDadoCurva` na construção e no recálculo, numa transação que trava a linha da curva em `tCurvaMercd` (até 30 segundos para obter a trava, senão `CONSTRUCAO_EM_ANDAMENTO`). A construção automática (webhook de carga, construção sem recálculo) nunca sobrescreve uma data que já tem pontos.
- O `hashPontos` (SHA-256 das linhas `data;valor`) é definido na spec `curve-build-pipeline` do engine.
- O calendário de feriados, inclusive os calendários Groovy, está no engine, que o expõe por `GET /api/v1/calendarios/{nome}` (spec `calendar-management`).
- O cadastro (curva, configuração vigente com arredondamento e calendário) é do `services/curves` (change `curves-cadastro-curvas`).

## Goals / Non-Goals

**Goals:**
- O gestor corrige ou digita pontos à mão, sem ser bloqueado, com preferência sobre o engine.
- Nenhuma dependência de escrita entre curves e engine: só o banco é compartilhado.
- Edição em lote por planilha.

**Non-Goals:**
- Construção, recálculo e interpolação: são do engine.
- Auditoria da edição manual: é contingência.
- Mudança de schema.

## Decisions

### D1. Edição no curves, construção no engine
O engine fica com o que depende de modelo (construção, recálculo, interpolação, simulação), e o curves com o que o gestor faz à mão (cadastro e pontos). Os dois gravam `tDadoCurva`, então compartilham pelo banco a trava por curva e a fórmula do `hashPontos`, sem chamadas de escrita entre eles.

**Alternativa rejeitada:** o curves delegar a gravação ao engine. Tornaria a contingência dependente do engine no ar, o que contraria a razão de existir da edição manual.

### D2. Preferência sem coordenação
A preferência da edição manual resulta de regras que já existem:
- **Trava no banco:** a edição espera a transação curta do engine e grava por cima.
- **Construção automática nunca sobrescreve:** a regra do engine é "curva com pontos → `EXISTENTE`".
- **Recálculo forçado:** só por ação explícita de um usuário.

Não há `If-Match`: numa contingência, o gestor que salva vence, e o `hashPontos` anterior fica no log.

### D3. Só a consistência do banco barra a edição; regra de negócio é aviso
A edição manual é o caminho do gestor para contornar qualquer problema, inclusive de cadastro (um feriado errado, uma configuração ausente). Por isso o serviço só recusa o que não pode ser gravado de forma consistente em `tDadoCurva`: lista vazia, data ou valor ausente ou malformado, data repetida (PK) e valor que não cabe em `DECIMAL(28,12)`. Toda regra de negócio vira aviso e o ponto é gravado: data igual ou anterior à data-base, fim de semana, feriado, preço ou pontos não positivos, sem configuração vigente, calendário não conferido. O engine trata esses pontos na interpolação (descarte de prazo não positivo e de ponto no mesmo prazo, com aviso), e o gestor vê os avisos na hora de salvar.

Pelo mesmo motivo, nenhuma dependência bloqueia: sem configuração vigente, grava sem arredondar; com o engine fora, grava sem conferir feriados.

### D4. Feriados pela exportação de calendário do engine
Um ponto num feriado tem o mesmo número de dias úteis do dia útil anterior, e na interpolação o engine fica só com o de menor data. Pela D3, o curves não recusa o ponto (o erro pode estar no cadastro de feriados); grava com o aviso `PONTO_EM_FERIADO` para o gestor conferir. O calendário (com os scripts Groovy) está no engine, então o curves lê os feriados pela rota de exportação, só leitura, com tempo limite curto. Se isso falhar (engine fora), o ponto é gravado com o aviso `CALENDARIO_NAO_VERIFICADO`. Nos dois casos, o engine trata o ponto na interpolação: fica o de menor data no mesmo prazo, com o aviso `PONTO_DESCARTADO_MESMO_PRAZO`.

### D5. Planilha como estado desejado por data-base
A importação segue o padrão da planilha de cadastro: cada par (curva, data-base) presente é a lista completa. Exportar e reimportar sem editar dá zero mudanças. A simulação marca ponto a ponto, e a aplicação é atômica. As travas são tomadas em ordem alfabética de nome, para não haver deadlock entre importações nem com o engine.

### D6. O que o gestor envia é exatamente o que fica
A edição manual existe para o gestor ter controle total, então o resultado não pode surpreender: para cada curva e data-base enviada, os pontos gravados ficam exatamente iguais à lista, inclusive com exclusão dos ausentes. A gravação é pela diferença (só as linhas que mudaram), o que diminui o tempo de trava e deixa o log só com mudanças reais, e termina relendo os pontos e conferindo o `hashPontos` contra o da lista antes do commit. Qualquer diferença desfaz tudo. A única transformação é o arredondamento pela configuração vigente, porque é assim que o engine grava e usa os pontos, e ela é avisada (`VALOR_ARREDONDADO`) e mostrada na simulação, linha a linha, em `ValorGravado`.

**Alternativa rejeitada:** apagar e inserir tudo a cada gravação. Chega ao mesmo estado, mas reescreve pontos que não mudaram, segura a trava por mais tempo e gera log de edição sem edição.

## Risks / Trade-offs

- **Recálculo forçado apaga pontos manuais.** → É ação explícita de um usuário; o log `PONTOS_EDITADOS` da edição anterior e a auditoria do recálculo (`pontosAnteriores`) preservam o que existia.
- **Ponto fora das regras de negócio gravado.** → Aviso na gravação; o engine continua interpolando, descartando com aviso o ponto de prazo não positivo ou repetido no mesmo prazo; o gestor corrige depois.
- **Preço ou pontos não positivos gravados.** → Aviso `VALOR_NAO_POSITIVO`; a interpolação `LogLinear` dessa data falha no engine com `PONTOS_NAO_INTERPOLAVEIS` até a correção, mas a consulta dos pontos continua funcionando.
- **Importação grande trava muitas curvas.** → Limite de 100.000 pontos; travas em ordem fixa; a simulação não trava.
- **Fórmula do `hashPontos` em dois serviços.** → Forma canônica do valor definida na spec do engine (sem zeros à direita, independente da escala do banco) e um vetor de teste comum aos dois.

## Migration Plan

1. Entra ID: a identidade gerenciada do curves precisa do papel `Curvas.Leitura` do engine, para a exportação de calendário (o mesmo do change `curves-cadastro-curvas`).
2. Deploy do curves com as rotas de pontos, junto com o engine sem a edição de pontos.
3. **Rollback:** voltar os dois deploys.

## Open Questions

Nenhuma.

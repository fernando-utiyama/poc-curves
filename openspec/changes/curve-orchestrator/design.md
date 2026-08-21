## Context

O orquestrador é o único componente com noção de tempo e de intenção. Todos os outros reagem: o feeder adquire quando mandam, o processor consome o que chega, o motor constrói quando pedem. O orquestrador é quem decide que hoje às 19h a curva PRE precisa ser atualizada, e é quem sabe dizer, depois, se isso aconteceu.

Na prática ele resolve três disparos com a mesma máquina: o agendado (rotina diária), o manual (operador na tela, escolhendo data e o que consumir — dado individual ou curva pronta) e o backfill (uma janela de datas de uma vez).

O que torna o problema não trivial é o comportamento da fonte. A B3 divulga dado em horários que variam, não divulga em feriado, e às vezes republica corrigido. Um orquestrador ingênuo trata "ainda não divulgou" como erro, alarma todo feriado e obriga alguém a redisparar manualmente o que deveria ser rotina.

## Goals / Non-Goals

**Goals:**

- Responder com precisão "a curva de hoje saiu, e se não saiu, em que etapa parou?".
- Tratar ausência de dado como estado legítimo, distinto de falha.
- Permitir ao operador disparar a ingestão pela tela, para qualquer das duas naturezas de insumo.
- Tornar backfill uma operação normal, não um script manual.
- Nunca produzir duas execuções concorrentes para a mesma curva, data e momento.

**Non-Goals:**

- Adquirir dado, normalizar, construir ou publicar curva.
- Ser um agendador genérico de propósito geral para a empresa.
- Orquestrar dependências arbitrárias entre tarefas; a única dependência modelada é insumo pronto → construção de curva.

## Decisions

### D1 — A execução é a entidade central, não o agendamento

Tudo o que acontece — agendado, manual ou backfill — cria uma linha em `execucao_curva` com o mesmo formato. O agendamento é só uma das origens possíveis. Isso faz a tela de monitoramento ter uma lista só, e faz o rastreio por `correlacao_id` funcionar igual nos três casos.

*Alternativa considerada*: tratar disparo manual como caminho paralelo, sem registrar execução. Rejeitada — é justamente o disparo manual que mais precisa de rastro, porque acontece quando alguma coisa já deu errado.

### D2 — `SEM_DADO` é um estado terminal legítimo

Três desfechos, não dois: sucesso, ausência de dado e falha. Ausência de dado se subdivide em "não é dia de pregão" e "é dia de pregão, mas ainda não divulgado". O segundo caso é reagendável dentro da janela de tentativa; o primeiro não.

*Por que é decisão e não detalhe*: sem essa distinção, ou o sistema alarma em todo feriado, ou alguém silencia o alarme e passa a não perceber falha real. As duas saídas são ruins.

### D3 — Janela de tentativa, não horário único

O agendamento declara horário inicial, janela de tentativa e intervalo entre tentativas. Se o dado ainda não saiu às 19h, tenta de novo às 19h30, até o fim da janela. Esgotada a janela sem divulgação, a execução termina em `SEM_DADO` — visível, não silencioso.

*Alternativa considerada*: horário fixo único com alerta em caso de ausência. Rejeitada — transforma variação normal da fonte em incidente operacional diário.

### D4 — Agendamento durável no banco, não em memória

Agendamentos vivem em tabela e são carregados na inicialização. Reiniciar o serviço não perde agendamento nem dispara em duplicidade. Na POC, uma única instância executa; o desenho deixa espaço para bloqueio de liderança quando houver mais de uma.

### D5 — Idempotência por chave de execução

Antes de criar execução, o orquestrador verifica se já existe uma ativa para a mesma `(curva | conjunto de dados, data de referência, momento)`. Se existe, o novo disparo é anexado à existente em vez de criar concorrente. O operador vê "já está rodando", não uma segunda barra de progresso.

### D6 — Backfill é uma execução-mãe com filhas por data

Uma janela de datas cria uma execução de backfill que gera uma execução por dia de pregão, com limite de concorrência configurável. Interromper o backfill para de criar novas, mas não aborta as em andamento. O progresso é a contagem de filhas concluídas.

*Alternativa considerada*: um laço síncrono que processa data a data. Rejeitada — sem visibilidade, sem retomada e sem controle de carga sobre a fonte.

### D7 — O encadeamento até a construção respeita o modo de origem

Concluída a ingestão de um conjunto de dados, o orquestrador identifica as definições de curva que o consomem e emite `curve.build.requested.v1` **apenas para as de modo `BOOTSTRAPPED`**. Curva `IMPORTED` já foi publicada pelo processor durante a própria ingestão; pedir construção para ela seria erro.

### D8 — Retentativa distingue transitório de permanente

Falha de transporte, indisponibilidade e timeout retentam com backoff exponencial até o limite. Erro de configuração, dataset inexistente e falha de validação não retentam — encerram com causa nomeada. Retentar erro permanente só produz ruído e atraso.

### D8b — A dead-letter é o gatilho do alerta, não a sua fonte

O front nunca consulta o Kafka. Um consumidor dedicado lê os tópicos de dead-letter e materializa cada mensagem como linha em `pendencia_dlq`; o alerta é uma consulta a essa tabela.

*Alternativa considerada*: o serviço que falha gravar a pendência junto com a publicação na dead-letter. Rejeitada — são dois destinos sem transação comum, e a escrita dupla falha de forma silenciosa exatamente quando mais importa. Com consumidor dedicado, se o banco estiver fora o offset não é confirmado e a pendência é materializada depois.

*Por que no orquestrador*: ele já é dono de `execucao_curva` e da visibilidade operacional, o que dá escritor único e liga a pendência à execução pelo `correlacao_id` sem esforço adicional.

### D8c — O alerta fecha sozinho, pelo `id_evento`

O reprocessamento republica a mensagem no tópico original preservando o `id_evento`. Quando o consumidor processa com sucesso, ele fecha a pendência aberta daquele `id_evento`.

*Consequência útil*: corrigir a causa e reprocessar do offset do tópico de origem — a rota indicada quando a falha é sistêmica e atinge milhares de mensagens — também resolve as pendências, porque a correspondência é pelo identificador do evento e não pelo caminho do reprocessamento.

*Por que não pedir baixa manual*: operador marcando item como resolvido erra, esquece e mascara falha que voltou a acontecer. O sinal precisa vir do próprio processamento.

### D8d — Alerta por grupo, não por mensagem

Um parser quebrado derruba milhares de mensagens. O alerta agrega por `(motivo, fonte, conjunto de dados, data de referência)`, porque essa é a unidade de correção: uma causa, uma ação. Contar mensagem individual produziria um painel ilegível justamente no momento em que ele precisa ser lido.

### D8e — O relógio é dado de primeira classe

Depois de alguns dias no ar, a pergunta operacional deixa de ser "o dado chegou?" e passa a ser "**vai dar tempo?**". O orquestrador é o único componente em posição de responder isso, porque é quem conhece o início, as etapas e o prazo.

Passa a manter, por execução: horário limite aplicável, tempo restante, orçamento das etapas que faltam e duração efetiva das já concluídas. Dois estados novos decorrem disso:

- **`EM_RISCO`** — ainda rodando, mas o tempo restante é menor que o orçamento do que falta. É o único estado que dá margem de manobra, e por isso o mais valioso.
- **`ATRASADA`** — passou do corte sem publicar. Distinto de falha: pode ainda concluir, só que tarde.

*O orçamento nasce provisório.* Os horários reais de divulgação da B3 são desconhecidos hoje, e chutá-los produziria alarme falso diário. Por isso cada etapa registra a duração efetiva, e o orçamento é calibrado com série medida.

### D8f — Retentativa é função do relógio

O teto da retentativa passa a ser `min(política padrão, fração do tempo restante até o corte)`. A mesma falha transitória merece dez minutos de paciência às 14h e resposta imediata às 18h50.

*Por que não um teto fixo conservador*: um teto curto o suficiente para ser seguro às 18h50 desperdiçaria recuperação automática o dia inteiro, e transformaria falha transitória de manhã em intervenção manual.

### D8g — Três faixas, e a de massa é a que cede

Agendamento publica na rotina, disparo manual na prioritária, backfill na de massa. A regra é fixa e sem condicional: **disparo manual é sempre prioritário**, tenha ou não algo travado, porque disparo manual é intervenção por definição — alguém está olhando a tela.

A faixa de massa é a única pausável, e é pausada na janela de bloqueio anterior ao corte. É o mecanismo que faz "hoje ganhar do histórico" ser propriedade do sistema em vez de disciplina do operador.

*Consequência sobre pendências antigas*: reprocessar a dead-letter de três semanas atrás às 18h50 é usar o orçamento de hoje para resolver o problema de ontem. O reprocessamento de pendência respeita a mesma janela.

### D9 — O orquestrador não conhece formato de dado

Ele sabe *o que* disparar (fonte, conjunto de dados, data) e *o que fazer com o resultado*. Não sabe o que é BVBG nem o que é vértice. Todo conhecimento de formato fica no feeder e no processor.

## Risks / Trade-offs

- **Orçamento calibrado por suposição** → alarme falso diário se apertado demais, alerta inútil se frouxo. Mitigação: registrar duração efetiva de cada etapa desde o primeiro dia e tratar o orçamento inicial como provisório.
- **`EM_RISCO` virar ruído** → se quase toda execução passa por ele, o estado perde valor. Mitigação: derivá-lo do orçamento medido, não de um percentual arbitrário, e acompanhar a frequência com que dispara.
- **Faixa prioritária usada como rotina** → indicaria que a rotina está cronicamente entupida. Mitigação: a proporção de execuções prioritárias é métrica de saúde, não apenas de uso.
- **Janela de tentativa mal calibrada** → ou a execução fecha em `SEM_DADO` antes de a B3 divulgar, ou fica tentando por horas. Mitigação: janela configurável por agendamento e registro do horário efetivo de divulgação observado, para calibrar com dado real em vez de palpite.
- **Instância única de agendamento é ponto de falha** → aceitável na POC; o desenho prevê bloqueio de liderança para múltiplas instâncias, mas não o implementa agora.
- **Backfill grande sobrecarregar a fonte ou o banco** → limite de concorrência obrigatório e possibilidade de interromper; o backfill nunca roda sem teto.
- **Execução presa em estado intermediário se o serviço cair** → reconciliação na inicialização: execuções em estado não terminal há mais tempo que o limite são marcadas como falhas com causa explícita, em vez de ficarem eternamente "executando".
- **Operador dispara repetidamente por ansiedade quando algo demora** → D5 anexa o disparo à execução existente e mostra o progresso, em vez de multiplicar trabalho.
- **`correlacao_id` interrompido em algum salto** → é requisito de spec em todos os componentes e é verificado pelo teste de integração ponta a ponta.
- **Pendência antiga reprocessada sobrescrever dado já corrigido** → guarda de obsolescência: antes de republicar, compara o instante da falha com o lote mais recente da mesma chave; havendo lote mais novo, recusa e marca a pendência como obsoleta.
- **Mensagem expirar na dead-letter antes de alguém agir** → retenção da dead-letter maior que a do tópico de origem, e o alerta destaca a idade da pendência mais antiga, não apenas a contagem.
- **Alerta virar ruído e ser ignorado** → agregação por grupo, ordenação pela falha mais antiga e descarte explícito com justificativa, para que o painel reflita trabalho real pendente.

## Migration Plan

1. Entidade de execução, estados e API de consulta — a tela de monitoramento já funciona, ainda que só com disparo manual.
2. Disparo manual para os dois tipos de insumo, com reporte do feeder e propagação de `correlacao_id`.
3. Encadeamento até `curve.build.requested.v1`, respeitando o modo de origem da definição.
4. Agendamentos duráveis, com janela de tentativa e respeito ao calendário de pregão.
5. Backfill com execução-mãe, filhas por data e limite de concorrência.
6. Consumidor de dead-letter, materialização de pendências, resolução automática por `id_evento` e as ações de reprocessar e descartar.
6. Reconciliação de execuções presas na inicialização.

**Rollback**: o serviço guarda estado no banco, mas nenhum dado de curva. Reverter é voltar a imagem; execuções em andamento são reconciliadas na subida seguinte.

## Open Questions

- Quais são, na prática, os horários de divulgação da B3 por conjunto de dados? Determina o horário inicial e o tamanho da janela de cada agendamento.
- Ausência de dado ao fim da janela deve notificar alguém, ou basta ficar visível na tela? Se notificar, por qual canal?
- Backfill deve respeitar a mesma janela de tentativa do agendamento, ou falhar direto quando a data não tem dado?
- Curva intradiária exige agendamento de alta frequência? Isso mudaria o dimensionamento do agendador.
- Deve existir um limite de datas por backfill, ou o teto é só a concorrência?
- Qual o horário de fechamento de cada curva, e qual a duração da janela de bloqueio anterior a ele?
- `EM_RISCO` deve notificar por canal externo, ou basta o painel do dia?
- Execução `ATRASADA` deve continuar tentando indefinidamente, ou existe um horário após o qual desiste?
- Pendência de dead-letter deve notificar por canal externo além do alerta na tela, ou a tela basta para a POC?
- Qual o limite de idade a partir do qual uma pendência aberta deve escalar de aviso para alarme?
